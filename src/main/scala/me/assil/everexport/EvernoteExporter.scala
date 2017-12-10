package me.assil.everexport

import com.evernote.edam.notestore.NoteMetadata

import scala.concurrent.{ExecutionContext, Future}

/**
  * An async API Evernote client for use in a JSON API.
  *
  * @param token A valid Evernote API authentication token.
  * @param sandbox Set to true to run in the Evernote sandbox.
  * @param ec Implicit execution context for Future computation.
  */
class EvernoteExporter(val token: String, val sandbox: Boolean = false)(implicit ec: ExecutionContext) {

  val client = new EvernoteClient(token, sandbox)

  /**
    * Get the note metadata for a given notebook.
    */
  def getNotesMetadata(notebook: Notebook): Future[Seq[NoteMetadata]] = {
    client.getNotesMetadata(notebook, allNotes = true) match {
      case Right(v) => Future(v)
      case Left(e) => Future.failed(e)
    }
  }

  /**
    * List all notebooks in user's account.
    */
  def listNotebooks: Future[List[Notebook]] = {
    client.listNotebooks match {
      case Right(v) => Future(v)
      case Left(e) => Future.failed(e)
    }
  }

  /**
    * Get titles of all notes in a given notebook.
    */
  def getNoteTitles(notebook: Notebook): Future[Seq[String]] = {
    getNotesMetadata(notebook) map { notesMetadata =>
      notesMetadata.map(_.getTitle)
    }
  }

  /**
    * Get a single note.
    */
  def getNote(guid: String): Future[Note] = {
    client.getNote(guid) match {
      case Right(v) => Future(v)
      case Left(e) => Future.failed(e)
    }
  }

  /**
    * Get all notes in a given notebook.
    */
  def getNotebook(notebook: Notebook): Future[Seq[Note]] = {
    getNotesMetadata(notebook) flatMap { notesMetadata =>
      val noteList: Seq[Future[Note]] = notesMetadata map { note => getNote(note.getGuid) }

      // NOTE: this fails if one of the futures fails
      // Transforms a List[Future[T]] to Future[List[T]] -- like magic!
      Future.sequence(noteList)
    }
  }
}
