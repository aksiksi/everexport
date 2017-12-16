package me.assil.everexport

import facades.evernote._

import scala.scalajs.js
import scala.scalajs.js.Promise
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.JSConverters._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

sealed class EverExportException(message: String) extends Exception(message)

/**
  * An async API Evernote client for use in a JSON API.
  *
  * @param token A valid Evernote API authentication token.
  * @param sandbox Set to true to run in the Evernote sandbox.
  */
@JSExportTopLevel("EverExport")
class EverExport(val token: String, val sandbox: Boolean = false) {
  // Get a NoteStore instance; used for all subsequent API requests
  private val client: Client = new Client(ClientParams(token = token, sandbox = sandbox))
  private val noteStore: NoteStore = client.getNoteStore()

  /**
    * Returns all notebooks in the current user's account.
    */
  @JSExport
  def listNotebooks: Promise[js.Array[Notebook]] = {
    noteStore.listNotebooks()
  }

  @JSExport
  def getNotebook(notebookGuid: String): Promise[Notebook] = {
    noteStore.getNotebook(notebookGuid)
  }

  def getNotesMetadata(notebookGuid: String): Promise[js.Array[NoteMetadata]] = {
    // Create a NoteFilter
    val noteFilter = new NoteFilter
    noteFilter.notebookGuid = notebookGuid

    // Create a ResultsSpec
    val resultSpec = new NotesMetadataResultSpec
    resultSpec.includeTitle = true
    resultSpec.includeUpdated = true

    // Perform API request
    val result = noteStore.findNotesMetadata(noteFilter, 0, 250, resultSpec).toFuture

    result flatMap { notesMetadataList =>
      val remaining: Int = notesMetadataList.totalNotes - (notesMetadataList.startIndex + notesMetadataList.notes.length)
      val numReqs: Int = math.ceil(remaining / 250.0).toInt

      if (numReqs > 0) {
        val noteMetadataFutures: Seq[Future[js.Array[NoteMetadata]]] = (1 to numReqs) map { r =>
          noteStore.findNotesMetadata(noteFilter, r * 250 - 1, 250, resultSpec).toFuture.map(_.notes)
        }

        // Transform above into a Future[Seq[Future[...]]]
        Future.sequence(noteMetadataFutures)
      }
      else
        Future(Vector(notesMetadataList.notes))

    } map(_.flatten.toJSArray) toJSPromise
  }

  @JSExport
  def getNoteTitles(notebookGuid: String): Promise[js.Array[String]] = {
    getNotesMetadata(notebookGuid).toFuture map { notesMetadata =>
      notesMetadata.map(_.title)
    } toJSPromise
  }

  def getNote(noteGuid: String, includeResources: Boolean = true): Promise[Note] = {
    val resultSpec = new NoteResultSpec
    resultSpec.includeContent = true
    resultSpec.includeResourcesData = includeResources

    noteStore.getNoteWithResultSpec(noteGuid, resultSpec)
  }

  def getNotes(notebookGuid: String): Promise[js.Array[Note]] = {
    getNotesMetadata(notebookGuid).toFuture flatMap { notesMetadata =>
      val f = notesMetadata.toVector.map { note =>
        getNote(note.guid).toFuture
      }

      Future.sequence(f)
    } map(_.toJSArray) toJSPromise
  }

  /**
    * Export one or more notes.
    *
    * @param noteGuids One or more note GUIDs
    * @return
    */
  @JSExport
  def exportNotes(noteGuids: String*): Promise[js.Array[Note]] = {
    val noteFutures = noteGuids.toVector.map { guid => getNote(guid).toFuture }
    Future.sequence(noteFutures).map(_.toJSArray).toJSPromise
  }

  /**
    * Export all notes in given notebook(s).
    *
    * @return 2D [[js.Array]] of [[Note]]
    */
  @JSExport
  def exportNotebooks(notebookGuids: String*): Promise[js.Array[js.Array[Note]]] = {
    Future.sequence {
      notebookGuids.toVector map { guid => getNotes(guid).toFuture }
    }.map(_.toJSArray).toJSPromise
  }
}
