package evernote

import com.evernote.edam.`type`.{Note => ENote, Notebook => ENotebook, Resource => EResource}
import com.evernote.edam.error.{EDAMNotFoundException, EDAMSystemException, EDAMUserException}
import com.evernote.edam.notestore.{NoteFilter, NoteMetadata, NoteStore, NotesMetadataResultSpec}
import com.evernote.edam.userstore.UserStore
import com.evernote.thrift.protocol.TBinaryProtocol
import com.evernote.thrift.transport.THttpClient

import scala.collection.JavaConverters._

// Exceptions
sealed class EvernoteException(message: String) extends Exception(message)
final case class EvernoteSystemException(message: String) extends EvernoteException(message)
final case class EvernoteUserException(message: String) extends EvernoteException(message)
final case class NoteError(id: String) extends EvernoteException(s"Note $id not found!")
final case class NotebookError(id: String) extends EvernoteException(s"Notebook $id not found!")

// https://dev.evernote.com/doc/reference/Types.html#Struct_Notebook
case class Notebook(guid: String, name: String, stack: String, created: Long, updated: Long)

// https://dev.evernote.com/doc/reference/Types.html#Struct_Note
case class Note(guid: String, title: String, content: String, created: Long, updated: Long,
                 notebookGuid: String, tagGuids: List[String], tagNames: List[String],
                 resources: List[Resource])

// https://dev.evernote.com/doc/reference/Types.html#Struct_Resource
case class Resource(guid: String, noteGuid: String, width: Int, height: Int, data: Array[Byte])

object EvernoteClient {
  implicit def convertNotebook(n: ENotebook): Notebook = {
    Notebook(
      guid = n.getGuid,
      name = n.getName,
      stack = n.getStack,
      created = n.getServiceCreated,
      updated = n.getServiceUpdated
    )
  }

  implicit def convertResource(r: EResource): Resource = {
    Resource(
      guid = r.getGuid,
      noteGuid = r.getNoteGuid,
      width = r.getWidth,
      height = r.getHeight,
      data = r.getData.getBody
    )
  }

  implicit def convertNote(n: ENote): Note = {
    Note(
      guid = n.getGuid,
      title = n.getTitle,
      content = n.getContent,
      created = n.getCreated,
      updated = n.getUpdated,
      notebookGuid = n.getNotebookGuid,
      tagGuids = n.getTagGuids.asScala.toList,
      tagNames = n.getTagNames.asScala.toList,
      resources = n.getResources.asScala.map(convertResource).toList
    )
  }
}

/**
  * Simple client that connects to Evernote.
  *
  * @author Assil Ksiksi
  * @version 0.1
  * @param token Valid auth token to access the Evernote API
  * @param sandbox If set to `true`, API calls are made to the Evernote sandbox
  */
class EvernoteClient(val token: String, val sandbox: Boolean = false) {
  import EvernoteClient._

  val userStoreUrl: String =
    if (sandbox)
      "https://sandbox.evernote.com/edam/user"
    else
      "https://evernote.com/edam/user"

  // Build UserStore client to make API calls
  private val userStore: UserStore.Client = {
    val userStoreTrans: THttpClient = new THttpClient(userStoreUrl)
    val userStoreProt: TBinaryProtocol = new TBinaryProtocol(userStoreTrans)
    new UserStore.Client(userStoreProt, userStoreProt)
  }

  require(userStore != null)

  // Get the NoteStore URL
  private val noteStoreUrl: String = userStore.getNoteStoreUrl(token)

  // Get a NoteStore instance; used for all subsequent API requests
  private val noteStore: NoteStore.Client = {
    val noteStoreTrans: THttpClient = new THttpClient(noteStoreUrl)
    val noteStoreProt: TBinaryProtocol = new TBinaryProtocol(noteStoreTrans)
    new NoteStore.Client(noteStoreProt, noteStoreProt)
  }

  require(noteStore != null)

  /**
    * Returns the current Evernote UserStore.
    *
    * @return A `UserStore.Client` instance
    */
  def getUserStore: UserStore.Client = userStore

  /**
    * Returns the current NoteStore.
    *
    * @return `NoteStore.Client`
    */
  def getNoteStore: NoteStore.Client = noteStore

  /**
    * Returns all notebooks in the current user's account.
    *
    * @return `List[Notebook]` of notebook objects
    */
  def listNotebooks: Either[EvernoteException, List[Notebook]] = {
    try {
      Right(noteStore.listNotebooks(token).asScala.map(convertNotebook).toList)
    } catch {
      case e: EDAMUserException => Left(EvernoteUserException(e.getMessage))
      case e: EDAMSystemException => Left(EvernoteSystemException(e.getMessage))
    }
  }

  def getNotebookByGuid(guid: String): Either[EvernoteException, Notebook] = {
    try {
      Right(noteStore.getNotebook(token, guid))
    } catch {
      case e: EDAMUserException => Left(EvernoteUserException(e.getMessage))
      case e: EDAMSystemException => Left(EvernoteSystemException(e.getMessage))
      case e: EDAMNotFoundException => Left(NotebookError(guid))
    }
  }

  def getNotebookByTitle(name: String): Either[EvernoteException, Notebook] = {
    listNotebooks.right.flatMap { notebooks =>
      notebooks.find(_.name == name) match {
        case None => Left(NotebookError(name))
        case Some(v) => Right(v)
      }
    }
  }

  def getNotesMetadata(notebook: Notebook, allNotes: Boolean = false): Either[EvernoteException, List[NoteMetadata]] = {
    // Create a NoteFilter
    val noteFilter = new NoteFilter
    noteFilter.setNotebookGuid(notebook.guid)

    // Create a ResultsSpec
    val resultSpec = new NotesMetadataResultSpec
    resultSpec.setIncludeTitle(true)
    resultSpec.setIncludeUpdated(true)

    try {
      // Perform API request
      // TODO: get all notes! Currently, grabs only up to 250
      val result = noteStore.findNotesMetadata(token, noteFilter, 0, 250, resultSpec)
      Right(result.getNotes.asScala.toList)
    } catch {
      case e: EDAMUserException => Left(EvernoteUserException(e.getMessage))
      case e: EDAMSystemException => Left(EvernoteSystemException(e.getMessage))
      case e: EDAMNotFoundException => Left(NotebookError(notebook.guid))
    }
  }

  def getNote(guid: String): Either[EvernoteException, Note] = {
    try {
      Right(noteStore.getNote(token, guid, true, true, true, true))
    } catch {
      case e: EDAMUserException => Left(EvernoteUserException(e.getMessage))
      case e: EDAMSystemException => Left(EvernoteSystemException(e.getMessage))
      case e: EDAMNotFoundException => Left(NoteError(guid))
    }
  }

  def getNotes(notebook: Notebook, allNotes: Boolean = false): Either[EvernoteException, List[Note]] = {
    getNotesMetadata(notebook, allNotes).right.flatMap { notesMetadata =>
      try {
        Right {
          notesMetadata map { note =>
            val result = getNote(note.getGuid)

            if (result.isRight) result.right.get
            else throw result.left.get
          }
        }
      } catch {
        case e: EvernoteException => Left(e)
      }
    }
  }
}
