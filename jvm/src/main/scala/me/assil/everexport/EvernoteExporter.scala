package me.assil.everexport

import scala.concurrent.{ExecutionContext, Future}

/**
  * An async API Evernote client for use in a JSON API.
  *
  * @author Assil Ksiksi
  * @version 0.1
  * @param token A valid Evernote API authentication token.
  * @param noteStoreUrl NoteStore URL returned during authentication.
  * @param sandbox Set to true to run in the Evernote sandbox.
  */
class EvernoteExporter(val token: String, val noteStoreUrl: String, val sandbox: Boolean = false)(implicit ec: ExecutionContext) {
  /**
    * List all notebooks in a user's account.
    */
  def listNotebooks: Future[Vector[Notebook]] = {
    Future {
      val client = new EvernoteClient(token, noteStoreUrl, sandbox)
      client.listNotebooks
    } flatMap {
      case Right(v) => Future(v)
      case Left(e) => Future.failed(e)
    }
  }

  /**
    * Get titles of all notes in a given notebook.
    */
  def getNoteTitles(notebook: Notebook): Future[Vector[String]] = {
    Future {
      val client = new EvernoteClient(token, noteStoreUrl, sandbox)
      client.getNotesMetadata(notebook) map { notesMetadata =>
        notesMetadata.map(_.title)
      }
    } flatMap {
      case Right(v) => Future(v)
      case Left(e) => Future.failed(e)
    }
  }

  /**
    * Get the note metadata for a given notebook.
    */
  def getNotesMetadata(notebook: Notebook): Future[Vector[Note]] = {
    Future {
      val client = new EvernoteClient(token, noteStoreUrl, sandbox)
      client.getNotesMetadata(notebook, allNotes = true)
    } flatMap {
      case Right(v) => Future(v)
      case Left(e) => Future.failed(e)
    }
  }

  def getNoteContent(note: Note): Future[Note] = {
    Future {
      val client = new EvernoteClient(token, noteStoreUrl, sandbox)

      client.getNoteContent(note)
    } flatMap {
      case Right(v) => Future(v)
      case Left(e) => Future.failed(e)
    }
  }

  /**
    * Get all notes in a given notebook.
    *
    * First, grab metadata. Then, grab the actual note content.
    */
  def getNotebook(notebook: Notebook): Future[Vector[Note]] = {
    getNotesMetadata(notebook) flatMap { notes =>
      val noteFutures = notes map { noteMetadata => getNoteContent(noteMetadata) }
      Future.sequence(noteFutures) // Vector[Future] -> Future[Vector]
    }
  }
}
