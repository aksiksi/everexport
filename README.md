# EverExport

A note export API written in Scala based on the [Evernote SDK](https://github.com/evernote/evernote-sdk-java). Compiles to both the JVM and JS (via [Scala.js](https://www.scala-js.org/)) backends.

## Build

First, make sure you have `sbt` 1.x installed.

### JVM

Run `sbt everexportJVM/assembly`. Fat JAR in `jvm/target/scala-2.12`.

### JS

Run `sbt everexportJS/fullOptJS`. Optimized JS in `jstarget/scala-2.12/scalajs-bundler/main`.

## Examples

The following two examples -- in Scala and ES6, respectively -- show how to use EverExport to list all notebooks in a user's account and print them to the console.

On the JVM (Scala):

```scala
import me.assil.everexport.EverExport

object QuickStart extends App {
  val token: String = ??? // https://dev.evernote.com/doc/articles/dev_tokens.php
  val exporter = new EverExport(token, sandbox = false)
  
  // Future-based API
  exporter.listNotebooks map { notebooks =>
    println(notebooks.map(_.name).mkString(", "))
  }
}
```

In JS:

```javascript 1.6
const everexport = require('everexport.js')
const exporter = new everexport.EverExport(token, sandbox) // Same token as above

// Promise-based API
exporter.listNotebooks().then(notebooks => {
    const names = notebooks.map(notebook => notebook.name).join(", ") 
    console.log(names)
})
```
