import sbtcrossproject.{crossProject, CrossType}

version := "0.1"

scalaVersion := "2.12.3"

crossScalaVersions := Seq("2.12.3", "2.11.12")

// Root project; combines both projects below
lazy val root = project
  .in(file("."))
  .aggregate(everJVM, everJS)

lazy val everexport =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full) // Full directory structure
    .in(file("everexport"))
    .settings(name := "everexport")
    .jsSettings(
      // Evernote JS SDK via npm
      npmDependencies in Compile += "evernote" -> "2.0.3"
    )
    .jvmSettings(
      // Evernote Java SDK
      libraryDependencies += "com.evernote" % "evernote-api" % "1.25.1",
      mainClass in assembly := Some("me.assil.everexport.Main"),
    )

// JVM and JS target projects
lazy val everJVM = everexport.jvm
lazy val everJS = everexport.js.enablePlugins(ScalaJSBundlerPlugin)

// Disable artifact publishing/packaging for root project
// Source: https://stackoverflow.com/a/21966398/845275
Keys.`package` := {
  (Keys.`package` in (everJVM, Compile)).value
  (Keys.`package` in (everJS, Compile)).value
}
