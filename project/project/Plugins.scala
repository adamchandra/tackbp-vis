import sbt._
import sbt.Keys._

object IeslPluginLoader extends Build {

  lazy val root = Project(id = "plugins", base = file("."))
    .settings(resolvers += "IESL Public Releases" at "http://dev-iesl.cs.umass.edu/nexus/content/groups/public")
    .settings(addSbtPlugin("edu.umass.cs.iesl" %% "iesl-sbt-base" % "latest.release")) // apparently buggy: "latest.integration" changing()
    .settings(addSbtPlugin("play" %% "sbt-plugin" % "2.1-09142012"))
    .settings(addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.0"))
    .settings(addSbtPlugin("com.mojolly.scalate" % "xsbt-scalate-generator" % "0.4.2"))
    .settings(addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.8.8"))
}


