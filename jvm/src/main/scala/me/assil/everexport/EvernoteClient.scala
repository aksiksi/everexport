package me.assil.everexport

import com.evernote.clients.NoteStoreClient
import com.evernote.edam.`type`.{Notebook => ENotebook, Resource => EResource}
import com.evernote.edam.error.{EDAMErrorCode, EDAMNotFoundException, EDAMSystemException, EDAMUserException}
import com.evernote.edam.notestore._
import com.evernote.edam.userstore

import scala.collection.JavaConverters._

// Exceptions
sealed class EvernoteException(message: String) extends Exception(message)
final case class EvernoteSystemException(message: String) extends EvernoteException(message)
final case class EvernoteRateLimitException(message: String, duration: Int) extends EvernoteException(message)
final case class EvernoteUserException(message: String) extends EvernoteException(message)
final case class NoteError(id: String) extends EvernoteException(s"Note $id not found!")
final case class NotebookError(id: String) extends EvernoteException(s"Notebook $id not found!")

// https://dev.evernote.com/doc/reference/Types.html#Struct_Notebook
case class Notebook(guid: String, name: String, stack: String, created: Long, updated: Long)

/*
   Combination of these two:
   - https://dev.evernote.com/doc/reference/Types.html#Struct_Note
   - http://dev.evernote.com/doc/reference/NoteStore.html#Struct_NoteMetadata
 */
case class Note(guid: String, title: String, var content: Option[String], created: Long, updated: Long,
                notebookGuid: String, tagGuids: Option[List[String]])

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

  implicit def convertNote(n: NoteMetadata): Note = {
    Note(
      guid = n.getGuid,
      title = n.getTitle,
      content = None,
      created = n.getCreated,
      updated = n.getUpdated,
      notebookGuid = n.getNotebookGuid,
      tagGuids = if (n.getTagGuids != null) Some(n.getTagGuids.asScala.toList) else None
    )
  }
}

/**
  * Simple client that connects to Evernote.
  *
  * @author Assil Ksiksi
  * @version 0.1
  * @param token Valid auth token to access the Evernote API
  * @param noteStoreUrl NoteStore URL for the current user.
  * @param sandbox If set to `true`, API calls are made to the Evernote sandbox
  */
class EvernoteClient(val token: String, val noteStoreUrl: String, val sandbox: Boolean = false) {
  import EvernoteClient._

  import com.evernote.auth.EvernoteAuth
  import com.evernote.auth.EvernoteService
  import com.evernote.clients.ClientFactory

  private val noteStore: NoteStoreClient = {
    val service = if (sandbox) EvernoteService.SANDBOX else EvernoteService.PRODUCTION
    val evernoteAuth = new EvernoteAuth(service, token)
    val factory = new ClientFactory(evernoteAuth)
    val userStore = factory.createUserStoreClient

    // Validate Evernote API version
    require(userStore.checkVersion("EverExport", userstore.Constants.EDAM_VERSION_MAJOR, userstore.Constants.EDAM_VERSION_MINOR),
            "Evernote SDK in use is outdated -- please upgrade!")

    // Finally, get the NoteStore instance; used for all subsequent API requests
    try {
      factory.createNoteStoreClient
    }
    catch {
      case e: EDAMUserException => throw EvernoteUserException(e.getMessage)
      case e: EDAMSystemException =>
        if (e.getErrorCode == EDAMErrorCode.RATE_LIMIT_REACHED)
          throw EvernoteRateLimitException(e.getMessage, e.getRateLimitDuration)
        else
          throw EvernoteSystemException(e.getMessage)
      case e: Throwable => throw new EvernoteException(e.getMessage)
    }
  }

  /**
    * Returns the current NoteStore.
    *
    * @return `NoteStoreClient`
    */
  def getNoteStore: NoteStoreClient = noteStore

  /**
    * Returns all notebooks in the current user's account.
    *
    * @return `List[Notebook]` of notebook objects
    */
  def listNotebooks: Either[EvernoteException, Vector[Notebook]] = {
    try {
      Right(noteStore.listNotebooks.asScala.map(convertNotebook).toVector)
    }
    catch {
      case e: EDAMUserException => Left(EvernoteUserException(e.getMessage))
      case e: EDAMSystemException =>
        if (e.getErrorCode == EDAMErrorCode.RATE_LIMIT_REACHED)
          Left(EvernoteRateLimitException(e.getMessage, e.getRateLimitDuration))
        else
          Left(EvernoteSystemException(e.getMessage))
      case e: Throwable => Left(new EvernoteException(e.getMessage))
    }
  }

  def getNotebookByGuid(guid: String): Either[EvernoteException, Notebook] = {
    try {
      Right(noteStore.getNotebook(guid))
    }
    catch {
      case e: EDAMUserException => Left(EvernoteUserException(e.getMessage))
      case e: EDAMSystemException =>
        if (e.getErrorCode == EDAMErrorCode.RATE_LIMIT_REACHED)
          Left(EvernoteRateLimitException(e.getMessage, e.getRateLimitDuration))
        else
          Left(EvernoteSystemException(e.getMessage))
      case e: EDAMNotFoundException => Left(NotebookError(guid))
      case e: Throwable => Left(new EvernoteException(e.getMessage))
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

  def getNotesMetadata(notebook: Notebook, allNotes: Boolean = false): Either[EvernoteException, Vector[Note]] = {
    // Create a NoteFilter
    val noteFilter = new NoteFilter
    noteFilter.setNotebookGuid(notebook.guid)

    // Create a ResultsSpec
    val resultSpec = new NotesMetadataResultSpec
    resultSpec.setIncludeTitle(true)
    resultSpec.setIncludeUpdated(true)

    try {
      // Get first 250 notes in notebook
      val notesMetadataList = noteStore.findNotesMetadata(noteFilter, 0, 250, resultSpec)

      // Get remaining notes in the notebook (if applicable)
      val remaining = notesMetadataList.getTotalNotes - (notesMetadataList.getStartIndex + notesMetadataList.getNotes.size())
      val numReqs: Int = math.ceil(remaining / 250.0).toInt

      // Perform a single request for each 250 notes
      val remainingNotes: Vector[NoteMetadata] = (0 until numReqs).toVector.flatMap { r =>
        noteStore.findNotesMetadata(noteFilter, (r+1) * 250 - 1, 250, resultSpec).getNotes.asScala
      }

      val allNotes: Vector[Note] = (notesMetadataList.getNotes.asScala.toVector ++ remainingNotes).map(convertNote)

      Right(allNotes)
    }
    catch {
      case e: EDAMUserException => Left(EvernoteUserException(e.getMessage))
      case e: EDAMSystemException =>
        if (e.getErrorCode == EDAMErrorCode.RATE_LIMIT_REACHED)
          Left(EvernoteRateLimitException(e.getMessage, e.getRateLimitDuration))
        else
          Left(EvernoteSystemException(e.getMessage))
      case e: EDAMNotFoundException => Left(NotebookError(notebook.guid))
      case e: Throwable => Left(new EvernoteException(e.getMessage))
    }
  }

  def getNoteContent(note: Note): Either[EvernoteException, Note] = {
    // TODO: change from getNote to getNoteWithResultSpec when SDK is updated...
    try {
      // Add content to the given note
      val content = noteStore.getNoteContent(note.guid)
      note.content = Some(content)
      Right(note)
    }
    catch {
      case e: EDAMUserException => Left(EvernoteUserException(e.getMessage))
      case e: EDAMSystemException =>
        if (e.getErrorCode == EDAMErrorCode.RATE_LIMIT_REACHED)
          Left(EvernoteRateLimitException(e.getMessage, e.getRateLimitDuration))
        else
          Left(EvernoteSystemException(e.getMessage))
      case e: EDAMNotFoundException => Left(NoteError(note.guid))
      case e: Throwable => Left(new EvernoteException(e.getMessage))
    }
  }
}
