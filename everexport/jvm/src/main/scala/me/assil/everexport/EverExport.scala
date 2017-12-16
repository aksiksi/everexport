package me.assil.everexport

import com.evernote.auth.{EvernoteAuth, EvernoteService}
import com.evernote.clients.{ClientFactory, NoteStoreClient}
import com.evernote.edam.error.{EDAMNotFoundException, EDAMSystemException, EDAMUserException}
import com.evernote.edam.notestore.{NoteFilter, NoteMetadata, NotesMetadataList, NotesMetadataResultSpec}
import com.evernote.edam.`type`.{Note => ENote, Notebook => ENotebook, Resource => EResource}
import com.evernote.thrift.TException

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

// https://dev.evernote.com/doc/reference/Types.html#Struct_Notebook
case class Notebook(guid: String, name: String, stack: String, created: Long, updated: Long)

/*
   Combination of these two:
   - https://dev.evernote.com/doc/reference/Types.html#Struct_Note
   - http://dev.evernote.com/doc/reference/NoteStore.html#Struct_NoteMetadata
 */
case class Note(guid: String, title: String, content: String, created: Long, updated: Long,
                notebookGuid: String, tagGuids: Option[List[String]], resources: Option[List[Resource]])

// https://dev.evernote.com/doc/reference/Types.html#Struct_Resource
case class Resource(guid: String, noteGuid: String, width: Option[Int], height: Option[Int], data: Array[Byte])

object EverExport {
  // Shortcut type
  type VHMap[A, B] = mutable.HashMap[A, Vector[B]]

  def convertNotebook(n: ENotebook): Notebook = {
    Notebook(
      guid = n.getGuid,
      name = n.getName,
      stack = n.getStack,
      created = n.getServiceCreated,
      updated = n.getServiceUpdated
    )
  }

  def convertResource(r: EResource): Resource = {
    Resource(
      guid = r.getGuid,
      noteGuid = r.getNoteGuid,
      width = if (r.isSetWidth) Some(r.getWidth) else None,
      height = if (r.isSetHeight) Some(r.getHeight) else None,
      data = r.getData.getBody
    )
  }

  def convertNote(n: ENote): Note = {
    Note(
      guid = n.getGuid,
      title = n.getTitle,
      content = n.getContent,
      created = n.getCreated,
      updated = n.getUpdated,
      notebookGuid = n.getNotebookGuid,
      tagGuids = if (n.isSetTagGuids) Some(n.getTagGuids.asScala.toList) else None,
      resources = if (n.isSetResources) Some(n.getResources.asScala.toList.map(convertResource)) else None
    )
  }
}

/**
  * An Future-based async API Evernote note exporter and client.
  *
  * @author Assil Ksiksi
  * @version 0.1
  * @param token A valid Evernote API authentication token.
  * @param sandbox Set to true to run in the Evernote sandbox.
  *
  * @throws EDAMSystemException if NoteStore construction fails
  */
class EverExport(val token: String, val sandbox: Boolean = false)(implicit ec: ExecutionContext) {
  import EverExport._

  private val noteStore = getNoteStoreClient

  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  private def getNoteStoreClient: NoteStoreClient = {
    val service = if (sandbox) EvernoteService.SANDBOX else EvernoteService.PRODUCTION
    val evernoteAuth = new EvernoteAuth(service, token)
    val factory = new ClientFactory(evernoteAuth)
    val userStore = factory.createUserStoreClient

    // Finally, get the NoteStore instance; used for all subsequent API requests
    factory.createNoteStoreClient
  }

  /**
    * List all notebooks in a user's account.
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  def listNotebooks: Future[Vector[Notebook]] = {
    Future {
      val noteStore = getNoteStoreClient
      noteStore.listNotebooks.asScala.map(convertNotebook).toVector
    }
  }

  /**
    * Get the note metadata for a given notebook.
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  private def getNotesMetadata(notebookGuid: String, allNotes: Boolean = false): Future[Vector[NoteMetadata]] = {
    Future {
      val noteStore = getNoteStoreClient

      // Create a NoteFilter
      val noteFilter = new NoteFilter
      noteFilter.setNotebookGuid(notebookGuid)

      // Create a ResultsSpec
      val resultSpec = new NotesMetadataResultSpec
      resultSpec.setIncludeTitle(true)
      resultSpec.setIncludeUpdated(true)

      // Get first 250 notes in notebook
      val notesMetadataList: NotesMetadataList = noteStore.findNotesMetadata(noteFilter, 0, 250, resultSpec)

      // Get remaining notes in the notebook (if applicable)
      val remaining = notesMetadataList.getTotalNotes - (notesMetadataList.getStartIndex + notesMetadataList.getNotes.size())
      val numReqs: Int = math.ceil(remaining / 250.0).toInt // Number of additional requests required to get all notes

      // Perform a single request for each batch of 250 notes
      val remainingNotes: Vector[NoteMetadata] = (0 until numReqs).toVector.flatMap { r =>
        noteStore.findNotesMetadata(noteFilter, (r+1) * 250 - 1, 250, resultSpec).getNotes.asScala
      }

      val allNotes: Vector[NoteMetadata] = notesMetadataList.getNotes.asScala.toVector ++ remainingNotes

      allNotes
    }
  }

  /**
    * Get titles of all notes in a given notebook.
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  def getNoteTitles(notebookGuid: String): Future[Vector[String]] = {
    getNotesMetadata(notebookGuid) map { notesMetadata =>
      notesMetadata.map(_.getTitle)
    }
  }

  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  def getNote(noteGuid: String): Future[Note] = {
    Future {
      val noteStore = getNoteStoreClient
      convertNote(noteStore.getNote(noteGuid, true, true, false, false))
    }
  }

  /**
    * Retrieves all notes in a given notebook.
    *
    * @param notebookGuid The GUID of the `Notebook` to retrieve notes from.
    * @return All of the notes in the given notebook, wrapped in a `Future`.
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  def getNotebook(notebookGuid: String): Future[Vector[Note]] = {
    getNotesMetadata(notebookGuid) flatMap { notesMetadata =>
      val noteFutures = notesMetadata.map(n => getNote(n.getGuid))
      Future.sequence(noteFutures) // Vector[Future] -> Future[Vector]
    }
  }

  /**
    * Retrieves one (or more) notes using the Evernote API given the note GUID(s).
    *
    * @param guids One or more Evernote note GUIDs
    * @throws EDAMSystemException
    * @throws EDAMUserException
    * @throws EDAMNotFoundException
    * @throws TException
    * @return A `Future` containing the list of notes requested.
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  def exportNotes(guids: String*): Future[Vector[Note]] = {
    val noteFutures = guids.toVector.map { guid => getNote(guid) }
    Future.sequence(noteFutures)
  }

  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  def exportNotebooks(notebookGuids: String*): Future[VHMap[String, Note]] = {
    val notebookFutures: Vector[Future[Vector[Note]]] =
      notebookGuids.map { notebookGuid =>
        getNotebook(notebookGuid)
      }.toVector

    // Build Future[HashMap] from retrieved note vectors
    Future.sequence(notebookFutures) map { notebooks: Vector[Vector[Note]] =>
      val vhmap = new VHMap[String, Note]()

      for (
        (notebookGuid, notes) <- notebookGuids.zip(notebooks)
      ) yield vhmap += (notebookGuid -> notes)

      vhmap
    }
  }
}
