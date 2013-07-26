package lib.template

import net.openreview.scalate._

import java.io.File
import org.apache.commons.io.{FilenameUtils=>FN}
import play.api._

trait ScalatePlayConfig extends ScalateConfig {
  import play.api.Configuration
  import play.api.Play.current

  def conf: Configuration = current.configuration

  override lazy val layoutMode = conf.getBoolean("scalate.layoutMode").getOrElse(false)

  lazy val precompileMode = conf.getBoolean("scalate.just-compile").getOrElse(false)

  override lazy val mode: ScalateMode = Play.mode match {
    case _        if precompileMode      => PrecompileMode
    case Mode.Dev if layoutMode          => LayoutMode 
    case Mode.Dev                        => DevMode
    case Mode.Prod                       => ProductionMode
  }

  override def getFile(s:String):java.io.File = Play.getFile(s)

  lazy val playRootPath = getFile("")

  override def classloader: java.lang.ClassLoader = Play.current.classloader

  def templateRootPaths: Seq[JFile] = Seq(getFile("/app/templates"))

}


