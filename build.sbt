import sbt.Keys._
import sbtcrossproject.{CrossType, crossProject}

version in ThisBuild := "0.2-SNAPSHOT"

val commonSettings = Seq(
  name := "everexport",
  organization := "me.assil",
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq("2.12.3", "2.11.12")
)

// Root project; combines both projects below
lazy val root = project
  .in(file("."))
  .aggregate(everJVM, everJS)

lazy val everexport =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full) // Full directory structure
    .in(file("everexport"))
    .settings(commonSettings: _*)
    .jsSettings(
      // Evernote JS SDK via npm
      npmDependencies in Compile += "evernote" -> "2.0.3"
    )
    .jvmSettings(
      libraryDependencies ++= Seq(
        // Evernote Java SDK
        "com.evernote" % "evernote-api" % "1.25.1",
        "org.scalatest" %% "scalatest" % "3.0.4" % "test"
      ),

      // Add sonatype repository settings
      publishTo := Some(
        if (isSnapshot.value)
          Opts.resolver.sonatypeSnapshots
        else
          Opts.resolver.sonatypeStaging
      ),

      publishMavenStyle := true
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

// POM settings for Sonatype
homepage := Some(url("https://everexport.assil.me"))
scmInfo := Some(ScmInfo(url("https://github.com/aksiksi/everexport"), "git@github.com:aksiksi/everexport.git"))
developers := List(Developer("aksiksi",
  "Assil Ksiksi",
  "assilksiksi@gmail.com",
  url("https://github.com/aksiksi")))
licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
