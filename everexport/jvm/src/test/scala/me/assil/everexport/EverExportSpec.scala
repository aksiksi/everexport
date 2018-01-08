package me.assil.everexport

import org.scalatest.AsyncFunSuite

import scala.util.{Failure, Success}

class EverExportSpec extends AsyncFunSuite {
  val token: String = System.getenv("EVERNOTE_TOKEN")
  val exporter = new EverExport(token, sandbox = true)

  test("Returns the Notebooks in a user's account") {
    exporter.listNotebooks map { _ => succeed }
  }

  test("Exports all notes from a user's account successfully") {
    val f1 = exporter.listNotebooks flatMap { notebooks =>
      val notebookGuids = notebooks.map(_.guid)
      exporter.exportNotebooks(notebookGuids: _*)
    }

    f1 map { notes =>
      // Flatten returned notebooks, map Try to Boolean, then ensure all true
      val results: Vector[Boolean] =
        notes.flatten.map {
          case Failure(_) => false
          case Success(_) => true
        }

      assert(!results.contains(false))
    }
  }
}
