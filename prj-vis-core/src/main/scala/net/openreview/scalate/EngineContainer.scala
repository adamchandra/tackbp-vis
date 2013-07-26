package net.openreview.scalate

import org.fusesource.scalate._
import org.fusesource.scalate.util._
import org.fusesource.scalate.layout._

import com.weiglewilczek.slf4s.Logging

trait EngineLike {
  var layoutStrategy: LayoutStrategy
  var mode: String
  var allowReload: Boolean
  var allowCaching: Boolean
  var workingDirectory: JFile
  var combinedClassPath: Boolean
  var classLoader: java.lang.ClassLoader
  var resourceLoader: ResourceLoader

  def reportConfig()

  def load(source: TemplateSource, extraBindings:Traversable[Binding]= Nil): Template
  def load(file: JFile, extraBindings: Traversable[Binding]): Template 
  def load(file: JFile): Template
  def load(uri: String, extraBindings: Traversable[Binding]): Template
  def load(uri: String): Template 

  def model(model: AnyRef, viewName:String, attributes: (String,Any)*): String

  def shutdown()
  def cleanWorkingDirectory()
  def precompileAll()
  def createTemplateJar(jarfile: java.io.File)

  def addViewRemappings(ms: (Class[_], String)*)

}

trait EngineContainer extends ScalateEngineFactory {

  protected def createEngine(): EngineLike

  def engine: EngineLike = _engine.getOrElse(sys.error("no configured scalate engine"))

  def configurator: EngineLike => Unit

  var _engine: Option[EngineLike] = None

  def startup() {
    shutdown()
    _engine = Some(configureEngine(configurator))
  }

  def shutdown() {
    _engine.foreach {e =>
      e.shutdown()
    }
  }

  def restart() {
    shutdown()
    startup()
  }

  def cleanWorkingDirectory() {
    engine.workingDirectory
  }


}


trait ScalateEngineFactory extends ScalateConfig {

  protected def createEngine(): EngineLike   = {
    new CustomTemplateEngine(templateRootPaths, "mode-not-yet-set", mode)
  }

  def configureEngine(confFn: EngineLike => Unit = (e => ())) = {
    val templateEngine = createEngine()

    templateEngine.layoutStrategy = new CustomLayoutStrategy(templateEngine)

    templateEngine.workingDirectory = getFile("scalate-work-tmp")
    templateEngine.combinedClassPath = true

    mode match {
      case PrecompileMode =>
        templateEngine.mode = "dev"
        templateEngine.allowReload = false
        templateEngine.allowCaching = true
      case ProductionMode =>
        templateEngine.mode = "production"
        templateEngine.allowReload = false
        templateEngine.allowCaching = true
        templateEngine.workingDirectory = null
      case DevMode =>
        templateEngine.mode = "dev"
        templateEngine.allowReload = true
        templateEngine.allowCaching = false
      case LayoutMode =>
        templateEngine.mode = "dev"
        templateEngine.allowReload = true
        templateEngine.allowCaching = false
    }

    templateEngine.classLoader = classloader
    templateEngine


    templateEngine.resourceLoader = CustomResourceLoader(
      sourceDirectories = templateRootPaths, 
      classloader = classloader,
      resourcePrefix = "scalate"
    )

    confFn(templateEngine)
    // templateEngine.reportConfig()
    templateEngine
  }
}
