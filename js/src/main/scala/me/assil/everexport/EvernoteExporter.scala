package me.assil.everexport

import facades.evernote._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import scala.scalajs.js
import scala.scalajs.js.Promise
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.JSConverters._

sealed class EvernoteException(message: String) extends Exception(message)
final case class EvernoteSystemException(message: String) extends EvernoteException(message)
final case class EvernoteUserException(message: String) extends EvernoteException(message)
final case class NoteError(id: String) extends EvernoteException(s"Note $id not found!")
final case class NotebookError(id: String) extends EvernoteException(s"Notebook $id not found!")

/**
  * An async API Evernote client for use in a JSON API.
  *
  * @param token A valid Evernote API authentication token.
  * @param sandbox Set to true to run in the Evernote sandbox.
  */
@JSExportTopLevel("EvernoteExporter")
class EvernoteExporter(val token: String, val sandbox: Boolean = false) {
  // Get a NoteStore instance; used for all subsequent API requests
  private val client: Client = new Client(ClientParams(token = token, sandbox = sandbox))
  private val noteStore: NoteStore = client.getNoteStore()

  /**
    * Returns the current NoteStore.
    *
    * @return `Evernote.NoteStore`
    */
  def getNoteStore: NoteStore = noteStore

  /**
    * Returns all notebooks in the current user's account.
    */
  @JSExport
  def listNotebooks(): Promise[js.Array[Notebook]] = {
    noteStore.listNotebooks()
  }

  @JSExport
  def getNotebookByGuid(guid: String): Promise[Notebook] = {
    noteStore.getNotebook(guid)
  }

  @JSExport
  def getNotebookByTitle(name: String): Promise[Notebook] = {
    listNotebooks.toFuture.map { notebooks =>
      notebooks.find(_.name == name) match {
        case None => throw NotebookError(name)
        case Some(v) => v
      }
    }.toJSPromise
  }

  def getNotesMetadata(notebook: Notebook, allNotes: Boolean = false): Promise[js.Array[NoteMetadata]] = {
    // Create a NoteFilter
    val noteFilter = new NoteFilter
    noteFilter.setNotebookGuid(notebook.guid)

    // Create a ResultsSpec
    val resultSpec = new NotesMetadataResultSpec
    resultSpec.setIncludeTitle(true)
    resultSpec.setIncludeUpdated(true)

    // Perform API request
    // TODO: get all notes! Currently, grabs only up to 250
    val result = noteStore.findNotesMetadata(noteFilter, 0, 250, resultSpec).toFuture
    result map { notesMetadataList => notesMetadataList.notes } toJSPromise
  }

  @JSExport
  def getNote(guid: String): Promise[Note] = {
    noteStore.getNote(guid, true, true, true, true)
  }

  def getNotes(notebook: Notebook, allNotes: Boolean = false): Promise[js.Array[Note]] = {
    getNotesMetadata(notebook, allNotes).toFuture flatMap { notesMetadata =>
      val f = notesMetadata.toVector map { note =>
        getNote(note.getGuid).toFuture
      }

      Future.sequence(f)
    } map(_.toJSArray) toJSPromise
  }

  /**
    * Get titles of all notes in a given notebook.
    */
  @JSExport
  def getNoteTitles(notebook: Notebook): Promise[js.Array[String]] = {
    getNotesMetadata(notebook).toFuture map { notesMetadata =>
      notesMetadata.map(_.getTitle)
    } toJSPromise
  }

  /**
    * Get all notes in a given notebook.
    */
  @JSExport
  def getNotebook(notebook: Notebook): Promise[js.Array[Note]] = {
    getNotes(notebook, allNotes = true)
  }
}
