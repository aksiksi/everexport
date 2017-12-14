package me.assil.everexport.facades.evernote

import scala.scalajs.js
import scala.scalajs.js.Promise
import scala.scalajs.js.annotation.{JSImport, ScalaJSDefined}

@JSImport("evernote", "Types.Notebook")
@js.native
class Notebook extends js.Object {
  val guid: String = js.native
  val name: String = js.native
  val stack: String = js.native
  val created: Long = js.native
  val updated: Long = js.native
}

@JSImport("evernote", "Types.Note")
@js.native
class Note(guid: String, title: String, content: String, created: Long, updated: Long,
           notebookGuid: String, tagGuids: js.Array[String], tagNames: js.Array[String],
           resources: js.Array[Resource]) extends js.Object

@JSImport("evernote", "Types.Resource")
@js.native
class Resource(guid: String, noteGuid: String, width: Int, height: Int, data: js.Array[Byte]) extends js.Object

@js.native
trait ClientParams extends js.Object {
  val consumerKey: js.UndefOr[String]
  val consumerSecret: js.UndefOr[String]
  val token: js.UndefOr[String]
  val sandbox: Boolean
  val china: Boolean
}

object ClientParams {
  def apply(consumerKey: js.UndefOr[String] = js.undefined, consumerSecret: js.UndefOr[String] = js.undefined,
            token: js.UndefOr[String] = js.undefined, sandbox: Boolean, china: Boolean = false): ClientParams = {
    if (consumerKey.isDefined && consumerSecret.isDefined)
      js.Dynamic.literal(
        consumerKey = consumerKey,
        consumerSecret = consumerSecret,
        sandbox = sandbox,
        china = china).asInstanceOf[ClientParams]
    else
      js.Dynamic.literal(
        token = token,
        sandbox = sandbox,
        china = china).asInstanceOf[ClientParams]
  }
}

@JSImport("evernote", "Client")
@js.native
class Client(val params: ClientParams) extends js.Object {

  // https://github.com/evernote/evernote-sdk-js/blob/a5f9eb20c30c148fc8fc3cd48016763e22e99431/src/client.js#L75
  def getRequestToken(callbackUrl: String, callback: js.Function3[String, String, String, Unit]): Unit = js.native

  // https://github.com/evernote/evernote-sdk-js/blob/a5f9eb20c30c148fc8fc3cd48016763e22e99431/src/client.js#L105
  def getNoteStore(noteStoreUrl: String = ""): NoteStore = js.native

  // https://github.com/evernote/evernote-sdk-js/blob/a5f9eb20c30c148fc8fc3cd48016763e22e99431/src/client.js#L86
  def getAccessToken(oauthToken: String, oauthTokenSecret: String, oauthVerifier: String,
                     callback: js.Function3[String, String, String, Unit]): Unit = js.native
}

@JSImport("evernote", "NoteStore")
@js.native
class NoteStore extends js.Object {
  def listNotebooks(): Promise[js.Array[Notebook]] = js.native
  def findNotesMetadata(filter: NoteFilter, offset: Int, maxNotes: Int,
                        spec: NotesMetadataResultSpec): Promise[NotesMetadataList] = js.native
  def getNote(guid: String, withContent: Boolean, withResourcesData: Boolean,
              withResourcesRecognition: Boolean, withResourcesAlternateData: Boolean): Promise[Note] = js.native
  def getNotebook(guid: String): Promise[Notebook] = js.native
}

@JSImport("evernote", "NoteStore.NotesMetadataResultSpec")
@js.native
class NotesMetadataResultSpec extends js.Object {
  def setIncludeTitle(status: Boolean): Unit = js.native
  def setIncludeUpdated(status: Boolean): Unit = js.native
}

@JSImport("evernote", "NoteStore.NoteFilter")
@js.native
class NoteFilter extends js.Object {
  def setNotebookGuid(guid: String): Unit = js.native
}

@JSImport("evernote", "NoteStore.NoteMetadata")
@js.native
class NoteMetadata extends js.Object {
  val guid: String = js.native
  val title: String = js.native
  val contentLength: Int = js.native
  val created: Long = js.native
  val updated: Long = js.native
  val deleted: Long = js.native
  val notebookGuid: String = js.native
  val tagGuids: Vector[String] = js.native

  def getTitle: String = js.native
  def getGuid: String = js.native
}

@JSImport("evernote", "NoteStore.NotesMetadataList")
@js.native
class NotesMetadataList extends js.Object {
  val totalNotes: Int = js.native
  val notes: js.Array[NoteMetadata] = js.native
  val updateCount: Int = js.native
}
