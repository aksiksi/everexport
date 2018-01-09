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
      exporter.exportNotebooks(notebooks.map(_.guid))
    }

    f1 map { notes =>
      // Flatten returned notebooks, map Try to titles
      val results: Vector[String] =
        notes.flatten.map {
          case Failure(e) => "-1111111"
          case Success(v) => v.title
        }

      println(results)

      assert(!results.contains("-1111111"))
    }
  }
}
