import sbt._
import Keys._
import edu.umass.cs.iesl.sbtbase.Dependencies
import edu.umass.cs.iesl.sbtbase.IeslProject._

import PlayProject._

import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._

object TackbpVisBuild extends Build {

  val vers = "0.1-SNAPSHOT"
  val organization = "edu.umass.cs.iesl.tackbp"

  implicit val allDeps: Dependencies = new Dependencies()

  import allDeps._

  override def settings = super.settings ++ org.sbtidea.SbtIdeaPlugin.ideaSettings

  // root project exists only to help the "idea" task generate a root-level module.
  lazy val root = (PlayProject("root", vers, path = file("."), mainLang = SCALA)
    .ieslSetup(vers, Seq(), Public, WithSnapshotDependencies)
    .cleanLogging.standardLogging //("!.6.1")
    .dependsOn(prjFront)
    .aggregate(prjFront)
    )

  lazy val slf4jVersion = "latest.release"

  lazy val prjCore = {
    val deps: Seq[ModuleID] =
      Seq(
        "org.fusesource.scalate" % "scalate-core_2.9" % "1.6.1",

        // TODO: this should be put back into iesl sbt base w/parameters for logback versions
        logbackCore("1.0.12"), // <- must explicitly parameterize these
        logbackClassic("1.0.12"),

        // use the slf4j wrapper API
        slf4j(slf4jVersion),

        // nice Scala syntax for slf4j
        slf4s(),

        // direct legacy Jakarta Commons Logging calls to slf4j
        jclOverSlf4j(slf4jVersion),

        // direct legacy log4j calls to slf4j
        log4jOverSlf4j(slf4jVersion),

        // direct legacy java.util.logging calls to slf4j
        julToSlf4j(slf4jVersion),

        // direct grizzled-slf4j calls to slf4j
        grizzledSlf4j(slf4jVersion),
        // END cut/paste from sbt iesl base

        "play" %% "play" % "2.1-09142012",
        "play" %% "play-test" % "2.1-09142012" % "test",

        scalatest(),
        "org.mongodb" %% "casbah" % "2.4.1", // TODO move into sbt iesl base
        "org.json4s" %% "json4s-native" % "3.0.0",
        "org.scala-lang" % "scala-compiler" % "2.9.2",
        "org.scala-lang" % "jline" % "2.9.2",
        "commons-io" % "commons-io" % "2.4",
        ieslScalaCommons("latest.integration"),
        scalazCore("7.0.0"), 
        "org.scalaz" %% "scalaz-concurrent" % "7.0.0",
        "org.mozilla" % "rhino" % "1.7R4",
        specs2(),
        junit4(),
        typesafeConfig(),
        scalatime()
      )


    (Project("vis-core", file("prj-vis-core"))
      .ieslSetup(vers, deps, Public, WithSnapshotDependencies, org = organization, conflict = ConflictStrict) //, debugLevel = DebugVars)
      .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
      .settings(scalacOptions ++= List("-Yrepl-sync", "-Ydependent-method-types"))
      .settings(parallelExecution in Test := false)
      .settings(historyPath <<= baseDirectory(t => Some(t / ".sbt-history")))
      // .settings(
      //   logBuffered in Test := false,
      //   Keys.fork in run := true,
      //   scalacOptions in run ++= List("-Yrepl-sync", "-Ydependent-method-types"),
      //   outputStrategy := Some(StdoutOutput)
      // )
    )
  }



  lazy val prjFront = {

    val deps = Seq(
      "edu.umass.cs.iesl" %% "play-navigator" % "0.4.0"
      )

    (PlayProject("vis-front", vers, path = file("prj-vis-front"), mainLang = SCALA)
      .ieslSetup(vers, deps, Public, WithSnapshotDependencies, org = organization, conflict = ConflictStrict)
      .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
      .cleanLogging.standardLogging
      .settings(parallelExecution in Test := false)
// NOPUSH
//       .settings(templatesImport ++= Seq("nav.nav", "controllers._", "lib.core._"))
//       .settings(scalateSettings: _*)
//       .settings(scalateTemplateConfig in Compile <<= (sourceDirectory in Compile){ base =>
//         Seq(
//           TemplateConfig(
//             scalateTemplateDirectory = base / "views",
//             scalateImports = Seq(
//               "import _root_.lib.ScalateOps._",
//               "import _root_.play.api._",
//               "import _root_.play.api.mvc._"
//             ),
//             scalateBindings  = Seq(),  // Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true), ),
//             packagePrefix = Some("")
//           )
//         )
//       })
//       .settings(scalateClasspaths <<= (fullClasspath in Runtime, managedClasspath in scalateClasspaths) map scalateClasspathsTask)
//       .settings(scalateLoggingConfig in Compile <<= (baseDirectory in Compile) { _ / "conf" / "scalate-logback.xml" })
//       .settings(scalateOverwrite := false)
//      .settings(libraryDependencies += "ch.qos.logback" % "logback-classic" % "latest.release" % Scalate.name)
//      .settings(libraryDependencies += "org.slf4j" % "slf4j-api" % "latest.release" % Scalate.name)

      // .settings(defaultExcludes in unmanagedResources := "play.plugins*")
      dependsOn (prjCore)
      )
  }

}





