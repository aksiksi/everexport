package me.assil.everexport

import org.scalatest.AsyncFunSuite

import scala.util.{Failure, Success}

class EverExportSpec extends AsyncFunSuite {
  val token = "S=s1:U=93aa5:E=167af159bc9:C=16057646f00:P=1cd:A=en-devtoken:V=2:H=62d4673ee2568acef054ff56e5c1e41d"
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
