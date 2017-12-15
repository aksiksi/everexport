import sbtcrossproject.{crossProject, CrossType}

name := "everexport"

version := "0.1"

scalaVersion := "2.12.3"

lazy val root =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full) // Full directory structure
    .in(file("everexport"))
    .settings() // Common settings
    .jsSettings(
      // Evernote JS SDK
      npmDependencies in Compile += "evernote" -> "2.0.3"
    )
    .jvmSettings(
      libraryDependencies += "com.evernote" % "evernote-api" % "1.25.1",
      mainClass in assembly := Some("me.assil.everexport.Main"),
    )

lazy val eveJVM = root.jvm
lazy val eveJS = root.js.enablePlugins(ScalaJSBundlerPlugin)
