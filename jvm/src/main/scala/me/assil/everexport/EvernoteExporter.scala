package me.assil.everexport

import scala.concurrent.{ExecutionContext, Future}

/**
  * An async API Evernote client for use in a JSON API.
  *
  * @param token A valid Evernote API authentication token.
  * @param noteStoreUrl NoteStore URL returned during authentication.
  * @param sandbox Set to true to run in the Evernote sandbox.
  */
class EvernoteExporter(val token: String, val noteStoreUrl: String, val sandbox: Boolean = false)(implicit ec: ExecutionContext) {

  val client = new EvernoteClient(token, noteStoreUrl, sandbox)

  /**
    * List all notebooks in a user's account.
    */
  def listNotebooks: Future[Vector[Notebook]] = {
    Future {
      // Blocking call to underlying client
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
    client.getNotesMetadataAsync(notebook)(ec) map { notesMetadata =>
      notesMetadata.map(_.getTitle)
    }
  }

  /**
    * Get a single note.
    */
  def getNote(guid: String): Future[Note] = {
    Future {
      // Blocking call to underlying client
      client.getNote(guid)
    } flatMap {
      case Right(v) => Future(v)
      case Left(e) => Future.failed(e)
    }
  }

  /**
    * Get all notes in a given notebook.
    */
  def getNotebook(notebook: Notebook): Future[Vector[Note]] = {
    client.getNotesMetadataAsync(notebook) flatMap { notesMetadata =>
      val noteList: Vector[Future[Note]] = notesMetadata map { note => getNote(note.getGuid) }

      // NOTE: this fails if one of the futures fails
      // Transforms a List[Future[T]] to Future[List[T]] -- like magic!
      Future.sequence(noteList)
    }
  }
}
