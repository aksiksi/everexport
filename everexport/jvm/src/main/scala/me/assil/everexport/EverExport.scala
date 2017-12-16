package me.assil.everexport

import com.evernote.auth.{EvernoteAuth, EvernoteService}
import com.evernote.clients.{ClientFactory, NoteStoreClient}
import com.evernote.edam.error.{EDAMNotFoundException, EDAMSystemException, EDAMUserException}
import com.evernote.edam.notestore.{NoteFilter, NoteMetadata, NotesMetadataList, NotesMetadataResultSpec}
import com.evernote.edam.`type`.{Notebook => ENotebook, Resource => EResource}
import com.evernote.thrift.TException

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

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

object EverExport {
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
    * Get titles of all notes in a given notebook.
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  def getNoteTitles(notebook: Notebook): Future[Vector[String]] = {
    getNotesMetadata(notebook) map { notesMetadata =>
      notesMetadata.map(_.title)
    }
  }

  /**
    * Get the note metadata for a given notebook.
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  private def getNotesMetadata(notebook: Notebook, allNotes: Boolean = false): Future[Vector[Note]] = {
    Future {
      val noteStore = getNoteStoreClient

      // Create a NoteFilter
      val noteFilter = new NoteFilter
      noteFilter.setNotebookGuid(notebook.guid)

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

      val allNotes: Vector[Note] = (notesMetadataList.getNotes.asScala.toVector ++ remainingNotes).map(convertNote)

      allNotes
    }
  }

  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  private def getNoteContent(note: Note): Future[Note] = {
    // TODO: change from getNote to getNoteWithResultSpec when SDK is updated...
    Future {
      val noteStore = getNoteStoreClient

      // Add content to the given note
      val content = noteStore.getNoteContent(note.guid)
      note.content = Some(content)
      note
    }
  }

  /**
    * Get all notes in a given notebook(s).
    *
    * First, grab metadata. Then, grab the actual note content.
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  def getNotebook(notebook: Notebook): Future[Vector[Note]] = {
    getNotesMetadata(notebook) flatMap { notes =>
      val noteFutures = notes map { noteMetadata => getNoteContent(noteMetadata) }
      Future.sequence(noteFutures) // Vector[Future] -> Future[Vector]
    }
  }
}
