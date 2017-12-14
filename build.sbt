import sbtcrossproject.{crossProject, CrossType}

name := "everexport"

version := "0.1"

scalaVersion := "2.12.3"

lazy val everexport =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full) // Full directory structure
    .in(file(".")) // Current directory is root
    .settings() // Common settings
    .jsSettings(
      // Evernote JS SDK
      npmDependencies in Compile += "evernote" -> "2.0.3"
    )
    .jvmSettings(
      libraryDependencies += "com.evernote" % "evernote-api" % "1.25.1"
    )
    .enablePlugins(ScalaJSBundlerPlugin)

lazy val eveJVM = everexport.jvm
lazy val eveJS = everexport.js
