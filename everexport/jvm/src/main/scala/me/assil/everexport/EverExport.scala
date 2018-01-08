package me.assil.everexport

import com.evernote.auth.{EvernoteAuth, EvernoteService}
import com.evernote.clients.{ClientFactory, NoteStoreClient}
import com.evernote.edam.error.{EDAMNotFoundException, EDAMSystemException, EDAMUserException}
import com.evernote.edam.notestore.{NoteFilter, NotesMetadataList, NotesMetadataResultSpec}
import com.evernote.thrift.TException

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Failure, Success, Try}

import EvernoteHelpers._

/** An [[ http://evernote.com/ Evernote ]] API client and note exporter.
  *
  * All of the methods in this class return a [[Future]] which can be handled
  * as required. Further, all blocking API calls are wrapped in [[blocking]].
  *
  * Of course, this means that you need to pass in an [[ExecutionContext]].
  * You can construct one using the methods provided in [[util]], or just pass in the
  * global [[http://www.scala-lang.org/api/2.12.3/scala/concurrent/ExecutionContext$$Implicits$.html EC ]].
  *
  * This class is thread-safe since all methods construct a new Evernote
  * [[https://thrift.apache.org/ Thrift]] client in-place.
  * This covers the case of, for example, sharing a ''single'' [[EverExport]] instance
  * across multiple threads.
  *
  * Some typical usage examples follow.
  *
  * - Get the titles of all notebooks in a user's account (sandbox):
  *
  * {{{
  *   val exporter = new EverExport("YOUR_TOKEN", sandbox = true, numThreads = 10)
  *
  *   val titlesFuture =
  *     for (notebooks <- exporter.listNotebooks) yield notebooks.map(_.name)
  *
  *   titlesFuture onComplete {
  *     case Failure(e) => throw e
  *     case Success(v: Vector[String]) => println(v.mkString(", "))
  *   }
  * }}}
  *
  * @author Assil Ksiksi
  * @version 0.2
  * @param token A valid Evernote API authentication token
  * @param sandbox Set to true to run in the Evernote sandbox
  * @example val exporter = new EverExport("YOUR_TOKEN", sandbox = false)
  *
  * @throws EDAMSystemException if [[NoteStoreClient]] construction fails
  */
class EverExport(val token: String, val sandbox: Boolean = false)(implicit ec: ExecutionContext) {
  // Build an Evernote client factory
  private val service = if (sandbox) EvernoteService.SANDBOX else EvernoteService.PRODUCTION
  private val evernoteAuth = new EvernoteAuth(service, token)
  private val factory = new ClientFactory(evernoteAuth)

  /** Constructs and returns a new [[NoteStoreClient]] instance.
    *
    * Throws an [[Exception]] if construction fails.
    *
    * @throws EDAMSystemException
    * @throws EDAMUserException
    * @throws TException
    * @return A new [[NoteStoreClient]]
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[TException]
  private def getNoteStoreClient: NoteStoreClient = {
    factory.createNoteStoreClient
  }

  /** Lists all notebooks in a user's account.
    *
    * @throws EDAMSystemException
    * @throws EDAMUserException
    * @throws TException
    * @return A [[Future]] containing a [[ Vector[Notebook] ]]
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[TException]
  def listNotebooks: Future[Vector[Notebook]] = {
    Future {
      val noteStore = getNoteStoreClient

      blocking {
        noteStore.listNotebooks.asScala.map(convertNotebook).toVector
      }
    }
  }

  /** Returns the `Notebook` with the given GUID.
    *
    * @param notebookGuid A valid `Notebook` GUID
    * @return The requested [[Notebook]] instance, wrapped in a [[Future]]
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  def getNotebook(notebookGuid: String): Future[Notebook] = {
    Future {
      val noteStore = getNoteStoreClient

      convertNotebook {
        blocking(noteStore.getNotebook(notebookGuid))
      }
    }
  }

  /** Returns the `Note` metadata for '''all''' notes in a given `Notebook`.
    *
    * The `Note` metadata can be used to learn general information
    * about the notes in a given `Notebook` ''without'' grabbing their entire contents.
    *
    * Note that this method can perform ''multiple'' API requests based on the
    * number of notes in the `Notebook` in question (250 notes per request).
    * All requests are executed in parallel using the previously constructed thread pool.
    *
    * @param notebookGuid GUID of the `Notebook`
    * @throws EDAMSystemException
    * @throws EDAMUserException
    * @throws EDAMNotFoundException
    * @throws TException
    * @todo Perform exception handling for Future.sequence() call
    * @return A [[Vector]] containing a [[NoteMetadata]] instance for each `Note`, wrapped in a [[Future]]
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  def getNotesMetadata(notebookGuid: String): Future[Vector[NoteMetadata]] = {
    // Create a NoteFilter
    val noteFilter = new NoteFilter
    noteFilter.setNotebookGuid(notebookGuid)

    // Create a ResultsSpec
    val resultSpec = new NotesMetadataResultSpec
    resultSpec.setIncludeTitle(true)
    resultSpec.setIncludeUpdated(true)

    // Perform first API request
    val firstFuture: Future[NotesMetadataList] = Future {
      val noteStore = getNoteStoreClient

      // Get first 250 notes in notebook
      blocking {
        noteStore.findNotesMetadata(noteFilter, 0, 250, resultSpec)
      }
    }

    // Create a Future for *each* of the remaining requests, combine them, and then return
    // a Future containing ALL note metadata
    firstFuture flatMap { notesMetadataList =>
      // Get number of remaining notes in the notebook
      val remaining: Int =
        notesMetadataList.getTotalNotes - (notesMetadataList.getStartIndex + notesMetadataList.getNotes.size())

      // Number of additional requests required to get remaining notes
      val numReqs: Int = math.ceil(remaining / 250.0).toInt

      // Perform a single request for each batch of 250 notes
      // Requests are performed in *parallel* -> each in a Future
      val remainingFutures: Vector[Future[NotesMetadataList]] =
        (0 until numReqs).toVector.map { r =>
          Future {
            // Construct a new NoteStore client to maintain thread safety
            // TODO: is this necessary?
            val noteStore = getNoteStoreClient

            val offset = (r+1) * 250 - 1

            blocking {
              noteStore.findNotesMetadata(noteFilter, offset, 250, resultSpec)
            }
          }
        }

      // Sequence the futures
      val metadataListsFuture: Future[Vector[NotesMetadataList]] = Future.sequence(remainingFutures)

      // Combine all lists into a single Future
      metadataListsFuture map { metadataLists: Vector[NotesMetadataList] =>
        // Flatten all results into a *single* list of NoteMetadata
        val flattenedMetadata: Vector[NoteMetadata] =
          metadataLists flatMap { metadataList =>
            metadataList.getNotes.asScala.map(convertNoteMetadata)
          }

        // Combine with initial results to get the final list
        notesMetadataList.getNotes.asScala.map(convertNoteMetadata).toVector ++ flattenedMetadata
      }
    }
  }

  /** Returns the titles of all notes in a given notebook.
    *
    * @param notebookGuid The GUID of the `Notebook`
    * @throws EDAMSystemException
    * @throws EDAMUserException
    * @throws EDAMNotFoundException
    * @throws TException
    * @return A [[Vector]] of titles
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  def getNoteTitles(notebookGuid: String): Future[Vector[String]] = {
    getNotesMetadata(notebookGuid) map { notesMetadata =>
      notesMetadata.map(_.title)
    }
  }

  /** Retrieves a single [[Note]].
    *
    * @param noteGuid A valid `Note` GUID
    * @throws EDAMSystemException
    * @throws EDAMUserException
    * @throws EDAMNotFoundException
    * @throws TException
    * @return The [[Note]] instance, if found
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  def getNote(noteGuid: String): Future[Note] = {
    Future {
      val noteStore = getNoteStoreClient
      convertNote {
        blocking {
          noteStore.getNote(noteGuid, true, true, false, false)
        }
      }
    }
  }

  /** Exports one (or more) notes using the Evernote API.
    *
    * Gracefully handles exceptions by returning a [[Vector]] of [[Try]].
    * This allows you to check for potential exceptions and react accordingly.
    *
    * @param noteGuids One or more Evernote `Note` GUIDs
    * @throws EDAMSystemException
    * @throws EDAMUserException
    * @throws EDAMNotFoundException
    * @throws TException
    * @return A [[Future]] containing a [[Vector]] of [[Try]] for each [[Note]]
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  def exportNotes(noteGuids: String*): Future[Vector[Try[Note]]] = {
    val noteFutures: Vector[Future[Note]] = noteGuids.toVector.map { guid => getNote(guid) }

    // Convert Future[Note] => Future[Try[Note]]
    val noteFutureTrys = noteFutures.map { f =>
      f.map(Success(_)).recover { case e => Failure(e) }
    }

    Future.sequence(noteFutureTrys)
  }

  /** Exports all notes in a given `Notebook`.
    *
    * Returns each [[Note]] instance wrapped in a [[Try]] for graceful
    * exception handling.
    *
    * @param notebookGuid The GUID of a `Notebook`
    * @throws EDAMSystemException
    * @throws EDAMUserException
    * @throws EDAMNotFoundException
    * @throws TException
    * @return All [[Note]]s in the given `Notebook`, wrapped in a [[Future]]
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  def exportNotebook(notebookGuid: String): Future[Vector[Try[Note]]] = {
    getNotesMetadata(notebookGuid) flatMap { notesMetadata =>
      val noteFutures = notesMetadata.map(n => getNote(n.guid))

      // Convert Future[Note] => Future[Try[Note]]
      val noteFutureTrys = noteFutures.map { f =>
        f.map(Success(_)).recover { case e => Failure(e) }
      }

      Future.sequence(noteFutureTrys)
    }
  }

  /** Exports one (or more) `Notebook`s using the Evernote API.
    *
    * The sequence of `Notebook`s is maintained as it was passed in. You
    * can still use [[Notebook.guid]] if you do not want to track ordering.
    *
    * As with [[exportNotes]], this method gracefully handles
    * exceptions. It is your job to handle them however.
    *
    * @param notebookGuids One or more `Notebook` GUIDs
    * @throws EDAMSystemException
    * @throws EDAMUserException
    * @throws EDAMNotFoundException
    * @throws TException
    * @return A 2D [[Vector]], where each row contains the exported [[Note]]s, each wrapped in a [[Try]]
    */
  @throws[EDAMSystemException]
  @throws[EDAMUserException]
  @throws[EDAMNotFoundException]
  @throws[TException]
  def exportNotebooks(notebookGuids: String*): Future[Vector[Vector[Try[Note]]]] = {
    val notebookFutures: Vector[Future[Vector[Try[Note]]]] =
      notebookGuids.map { notebookGuid => exportNotebook(notebookGuid) }.toVector

    // Future[Vector[Note]] -> Future[Try[Vector[Note]]
    Future.sequence(notebookFutures)
  }
}
