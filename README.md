# EverExport

[![Build Status](https://travis-ci.org/aksiksi/everexport.svg?branch=master)](https://travis-ci.org/aksiksi/everexport)

A note export API written in Scala based on the [Evernote SDK](https://github.com/evernote/evernote-sdk-java). Compiles to both the JVM and JS (via [Scala.js](https://www.scala-js.org/)) backends. Currently supports both 2.11.x and 2.12.x.

## Install

### Scala

In your `build.sbt`:

```scala
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
libraryDependencies += "me.assil" %% "everexport" % "0.2-SNAPSHOT"
```

### NPM

```bash
npm install everexport
```

## Examples

On the JVM (Scala):

```scala
import me.assil.everexport.{EverExport, Note, Notebook}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

// Global EC for executing Futures
import scala.concurrent.ExecutionContext.Implicits.global

object QuickStart extends App {
  // https://dev.evernote.com/doc/articles/dev_tokens.php
  val token: String = ???
  val exporter = new EverExport(token, sandbox = true)

  // Get all notebooks in user's account
  val notebooksFuture: Future[Vector[Notebook]] = exporter.listNotebooks

  // Return all note titles for the *first* notebook
  val noteTitlesFuture: Future[Vector[String]] =
    for (
      notebooks <- notebooksFuture;
      titles <- exporter.getNoteTitles(notebooks(0).guid)
    ) yield titles

  // Export all notes from *second* notebook (assuming it exists!)
  val notesFuture: Future[Vector[Try[Note]]] =
    for (
      notebooks <- notebooksFuture;
      notes <- exporter.exportNotebook(notebooks(1).guid)
    ) yield notes

  // Wait 5 seconds for last Future to complete
  val notes = Await.result(notesFuture, 5.seconds)
  
  // Display notes exported from second notebook
  println(notes)
}
```

In JS:

```javascript 1.6
const everexport = require('everexport')
const exporter = new everexport.EverExport(token, sandbox) // Same token as above

// Promise-based API
exporter.listNotebooks().then(notebooks => {
    const names = notebooks.map(notebook => notebook.name).join(", ") 
    console.log(names)
})
```

## Build

First, make sure you have `sbt` 1.x installed.

### Scala

Run `sbt everexportJVM/package`. JAR in `jvm/target/scala-2.12`.

### JS

Run `sbt everexportJS/fullOptJS`. Optimized JS in `js/target/scala-2.12/scalajs-bundler/main`.
