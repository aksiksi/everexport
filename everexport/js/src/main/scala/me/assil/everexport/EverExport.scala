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
  * An [[http://evernote.com Evernote]] API client and note exporter targeting JavaScript.
  *
  * The methods of this class all return Promises and are therefore compatible
  * with most JavaScript applications.
  *
  * Some typical usage examples follow.
  *
  * - Get the titles of all notebooks in a user's account (sandbox):
  *
  * {{{
  *   // Evernote sandbox
  *   val exporter = new EverExport("YOUR_TOKEN", true)
  *
  *   // Convert returned Promise to Future
  *   val titlesFuture =
  *     for (notebooks <- this.listNotebooks().toFuture) yield notebooks.map(_.name)
  *
  *   titlesFuture onComplete {
  *     case Failure(e) => throw e
  *     case Success(v: js.Array[String]) => println(v.mkString(", "))
  *   }
  * }}}
  *
  * @author Assil Ksiksi
  * @version 0.3
  * @param token A valid Evernote API authentication token
  * @param sandbox Set to true to run in the Evernote sandbox
  * @example val exporter = new EverExport("YOUR_TOKEN", false)
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
  def listNotebooks(): Promise[js.Array[Notebook]] = {
    noteStore.listNotebooks()
  }

  /**
    * Returns the `Notebook` with the given GUID.
    *
    * @param notebookGuid A valid `Notebook` GUID
    * @return Requested `Notebook` instance
    */
  @JSExport
  def getNotebook(notebookGuid: String): Promise[Notebook] = {
    noteStore.getNotebook(notebookGuid)
  }

  /**
    * Returns the `Note` metadata for '''all''' notes in a given `Notebook`.
    *
    * The `Note` metadata can be used to learn general information
    * about the notes in a given `Notebook` ''without'' grabbing their entire contents.
    *
    * @param notebookGuid Valid `Notebook` GUID
    * @return A [[Promise]] containing a [[js.Array]] of metadata, one for each `Note`
    */
  @JSExport
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

  /**
    * Exports a single `Note` using the Evernote API.
    *
    * @param noteGuid A valid `Note` GUID
    * @return A `Promise` containing the [[Note]]
    */
  @JSExport
  def exportNote(noteGuid: String): Promise[Note] = {
    val resultSpec = new NoteResultSpec
    resultSpec.includeContent = true
    resultSpec.includeResourcesData = true

    noteStore.getNoteWithResultSpec(noteGuid, resultSpec)
  }

  /**
    * Exports one (or more) notes using the Evernote API.
    *
    * @param noteGuids One or more Evernote `Note` GUIDs as a [[js.Array]]
    * @return A [[js.Array]] of [[Promise]], each containing a [[Note]]
    */
  @JSExport
  def exportNotes(noteGuids: js.Array[String]): Promise[js.Array[Note]] = {
    val noteFutures = noteGuids.map(guid => exportNote(guid).toFuture).toVector
    Future.sequence(noteFutures).map(_.toJSArray).toJSPromise
  }

  /**
    * Exports all notes in a given Evernote `Notebook`.
    *
    * @param notebookGuid The GUID of a `Notebook`
    * @return All `Note`s in the given `Notebook`, wrapped in a [[Promise]]
    */
  @JSExport
  def exportNotebook(notebookGuid: String): Promise[js.Array[Note]] = {
    getNotesMetadata(notebookGuid).toFuture flatMap { notesMetadata =>
      val f = notesMetadata.toVector.map { noteMetadata =>
        exportNote(noteMetadata.guid).toFuture
      }

      Future.sequence(f)
    } map(_.toJSArray) toJSPromise
  }

  /**
    * Exports one (or more) `Notebook`s using the Evernote API.
    *
    * @param notebookGuids One or more `Notebook` GUIDs
    * @return A 2D [[js.Array]] of [[Promise]]s, where each row corresponds to a notebook [[Promise]]
    */
  @JSExport
  def exportNotebooks(notebookGuids: js.Array[String]): Promise[js.Array[js.Array[Note]]] = {
    val notebookFutures = notebookGuids.map(guid => exportNotebook(guid).toFuture).toVector
    Future.sequence(notebookFutures).map(_.toJSArray).toJSPromise
  }
}
