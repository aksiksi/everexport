// Scala.js
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.19")

// Cross project support
addSbtPlugin("org.portable-scala" % "sbt-crossproject" % "0.3.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.3.0")

// ScalaJS bundler -- allows for importing libs via npm
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.9.0")
