package net.openreview.scalate

import play.api._

trait ScalateConfig {
  def layoutMode: Boolean
  def mode: ScalateMode 
  def getFile(s:String): JFile 
  def classloader: java.lang.ClassLoader 
  def templateRootPaths: Seq[JFile]
}
