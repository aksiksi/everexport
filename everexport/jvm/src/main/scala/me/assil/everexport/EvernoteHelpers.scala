package me.assil.everexport

import scala.collection.JavaConverters._

import com.evernote.edam.`type`.{Note => ENote, Notebook => ENotebook, Resource => EResource}
import com.evernote.edam.notestore.{NoteMetadata => ENoteMetadata}

// https://dev.evernote.com/doc/reference/Types.html#Struct_Notebook
case class Notebook(guid: String, name: String, stack: String, created: Long, updated: Long)

/*
   Combination of these two:
   - https://dev.evernote.com/doc/reference/Types.html#Struct_Note
   - http://dev.evernote.com/doc/reference/NoteStore.html#Struct_NoteMetadata
 */
case class Note(guid: String, title: String, content: String, created: Long, updated: Long,
                notebookGuid: String, tagGuids: Option[List[String]], resources: Option[List[Resource]])

// http://dev.evernote.com/doc/reference/NoteStore.html#Struct_NoteMetadata
case class NoteMetadata(guid: String, title: String, contentLength: Int, created: Long)

// https://dev.evernote.com/doc/reference/Types.html#Struct_Resource
case class Resource(guid: String, noteGuid: String, width: Option[Int], height: Option[Int], data: Array[Byte])

object EvernoteHelpers {
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

  def convertNoteMetadata(nm: ENoteMetadata): NoteMetadata = {
    NoteMetadata(
      guid = nm.getGuid,
      title = nm.getTitle,
      contentLength = nm.getContentLength,
      created = nm.getCreated
    )
  }
}
