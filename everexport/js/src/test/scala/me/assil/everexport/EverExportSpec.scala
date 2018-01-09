package me.assil.everexport

import org.scalatest.AsyncFunSuite

import scala.scalajs.js

class EverExportSpec extends AsyncFunSuite {
  implicit override def executionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  val token: String = System.getenv("EVERNOTE_TOKEN") // NOTE: this does not access local env for some reason
  val exporter = new EverExport(token, sandbox = true)

  test("Returns the Notebooks in a user's account") {
    exporter.listNotebooks().toFuture map { _ => succeed }
  }

  test("Exports all notes from a user's account successfully") {
    val f1 = exporter.listNotebooks().toFuture flatMap { notebooks =>
      exporter.exportNotebooks(notebooks.map(_.guid)).toFuture
    }

    f1 map { notes =>
      // Flatten returned notebooks, map to their titles
      val results: js.Array[String] = notes.flatten.map { note => note.title }
      println(results)
      assert(true)
    }
  }
}