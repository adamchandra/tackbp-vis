package net.openreview.scalate

import java.io.{PrintWriter, StringWriter}
import org.fusesource.scalate._
import com.weiglewilczek.slf4s.Logging

class CustomTemplateEngine(
  var sourceDirs: Traversable[JFile] = None,
  val engmode: String,
  val scalateMode: ScalateMode
) extends TemplateEngine(sourceDirs, engmode) with EngineLike with BindingHelpers with Logging {


  import scala.collection.mutable

  val viewCache = mutable.HashMap[(Class[_], String), String]()
  val templateCache = mutable.HashMap[(String, String), Template]()
  
  // Replaces the default coffeescript compiler
  filters += "coffeescript" -> CoffeeScriptFilter
  CoffeeScriptPipeline(this)

  import scala.collection.mutable.HashMap

  val viewRemappings: HashMap[Class[_], String] = HashMap()

  def addViewRemappings(ms: (Class[_], String)*) = {
    ms.foreach(viewRemappings += _)
  }
  override protected def createRenderContext(uri: String, out: PrintWriter): RenderContext = {
    logger.debug("Creating CustomRenderContext for uri "+uri)
    CustomRenderContext(uri, this, out)
  } 

  protected def createRenderContext(uri: String): RenderContext = {
    logger.debug("Creating CustomRenderContext for uri "+uri)
    CustomRenderContext(uri, this)
  } 

  def pubCreateRenderContext(uri: String, out: PrintWriter): RenderContext = {
    createRenderContext(uri, out)
  } 

  bindings = List(
    binding[CustomRenderContext]("context").withImportedMembers
  )

  import org.fusesource.scalate.filter.CoffeeScriptCompiler

  def reportConfig() {
    println("""|
               | == Template Engine Config == 
               |  mode                         = %s
               |  scalate-mode                 = %s
               |  allow caching                = %s
               |  allow reload                 = %s
               |  combined classpath           = %s
               |  template dirs                = %s
               |  source dirs (for .jade/etc.) = %s
               |  available filters            = %s
               |  classpath                    = %s
               |  view remappings              = %s
               |  classloader                  = ...
               | ============================
               |""".stripMargin.format (mode.toString,
                 scalateMode.toString,
                 allowCaching,
                 allowReload,
                 combinedClassPath,
                 templateDirectories,
                 sourceDirectories,
                 this.filters.keys.toSeq.mkString(", "),
                 viewRemappings.mkString(", "),
                 this.classpath
                 // this.classLoader
    ))
  }
  import org.fusesource.scalate.support.Compiler

  override protected def createCompiler: Compiler = {
    compilerInitialized = true
    ScalaCompiler.create(this)
  }


  ///TODO figure out why the generateScala and compile methods don't seem to get called during dev and precompiling phases
  // /**
  //  * Generates the Scala code for a template.  Useful for generating scala code that
  //  * will then be compiled into the application as part of a build process.
  //  */
  // override def generateScala(source: TemplateSource, extraBindings:Traversable[Binding] = Nil) = {
  //   logger.info("generateScala "+source)
  //   println(source.uri)
  //   println(source.className)
  //   println(source.simpleClassName)
  //   println(source.toFile)
  //   super.generateScala(source, extraBindings)
  // }


  override def compile(source: TemplateSource, extraBindings:Traversable[Binding] = Nil):Template = {
    println("Here!!!")
    super.compile(source, extraBindings)
  }

  // /**
  //  * Compiles a template source without placing it in the template cache. Useful for temporary
  //  * templates or dynamically created template
  //  */
  // override def compile(source: TemplateSource, extraBindings:Traversable[Binding] = Nil):Template = {
  //   logger.info("compiling "+source)
  //   println(source.uri)
  //   println(source.className)
  //   println(source.simpleClassName)
  //   println(source.toFile)
  //     //if (FN.getName(fpath)(0).isUpper) {
  //     //  // This is a model view
  //     //  val relfile = relativize(d, f).get
  //     //  val pkg = FN.getPath(relfile).replaceAll("/", ".")
  //     //  val clsname = FN.getName(relfile).split("\\.")(0)
  //     //  val itCls = pkg+""+clsname
  //     //  // reverse lookup on remapped view classes
  //     //  val remappedClassname = viewRemappings.map(_.swap).get(itCls).map(_.getName).getOrElse(itCls)
  //     // 
  //     //  logger.info("  Model remapping: "+itCls+" => "+remappedClassname)
  //     // 
  //     //  compile(
  //     //    TemplateSource.fromFile(f, relfile),
  //     //    List(binding[Any]("it").withImportedMembers.withClassName("_root_."+remappedClassname)))
  //   
  //   super.compile(source, extraBindings)
  // }


  def model(model: AnyRef, viewName:String, attributes: (String,Any)*): String  = {
    logger.info("model(model: AnyRef, viewName:String, attributes: (String,Any)*)")
    // reportConfig()
    val context = createRenderContext("/").asInstanceOf[CustomRenderContext]
    context.template(model, viewName, true)(attributes:_*)
    context.outputString
  }

  // this is the version of layout that creates the new rendercontext for subviews
  override protected def layout(uri: String, template: Template, out: PrintWriter, attributes: Map[String, Any]) {
    logger.info("layout(uri: String, template: Template, out: PrintWriter, attributes: Map[String, Any])")
    val context = createRenderContext(uri, out).asInstanceOf[CustomRenderContext]
    for ((key, value) <- attributes) {
      context.attributes(key) = value
    }
    try {
      layout(template, context)
    } catch {
      case e: RuntimeException =>
        throw new TemplateRuntimeException(e.getMessage(), e, context)
    }
  }

  /**
   * Renders the given template URI returning the output
   */
  override def layout(uri: String, attributes: Map[String,Any] = Map(), extraBindings: Traversable[Binding] = Nil): String = {
    logger.info("layout(uri: String, attributes: Map[String,Any] = Map(), extraBindings: Traversable[Binding] = Nil)")
    try {
      canLoad(uri, extraBindings)
      super.layout(uri, attributes, extraBindings)
    }
    catch {
      case e: org.fusesource.scalate.InvalidSyntaxException => 
        throw new TemplateException("Syntax error in "+uri+": " +e.getMessage())
      case e:TemplateException =>
        logger.debug( "TemplateException: " + e.getMessage + ": " + e.getCause())
        throw e
      case npe:NullPointerException =>
        throw new TemplateException("Cannot load uri: "+uri)
    }
  }

  override def layout(uri: String, out: PrintWriter, attributes: Map[String, Any]) {
    logger.info("layout(uri: String, out: PrintWriter, attributes: Map[String, Any])")
    try {
      canLoad(uri)
      super.layout(uri, out, attributes)
    }
    catch {
      case npe:NullPointerException => 
        throw new TemplateException("Cannot load uri: "+uri)
    }
  }


  import org.apache.commons.io.{FilenameUtils=>FN}
  import org.apache.commons.io.FileUtils

  def cleanWorkingDirectory() {
    FileUtils.cleanDirectory(workingDirectory)
  }

  def createTemplateJar(jarfile: java.io.File) {
    import jartool._

    val classes = file(FN.concat(workingDirectory.getPath, "classes"))
    val classFiles = tree(classes).map { f =>
      println("classes/f = "+classes + " / "+f)
      f -> relativize(classes, f).getOrElse(sys.error("error creating jar entries"))
    }

    JarBuilder(
      jarfile,
      classFiles.drop(1) 
    ).toJar()

  }

  def isUppercase(s:String) = s(0).isUpper
  def isModelViewname(s:String) = isUppercase(s)

  def filenameToClassname(fn: String): String = {
    FN.getPath(fn).replaceAll(
      "/", "."
    ) + FN.getName(
      fn
    ).split("\\.")(0)
  }

  def precompileAll() {
    logger.info("Precompiling scalate templates")
    cleanWorkingDirectory()
    for {
      d <- sourceDirectories
      _ = println("precompiling templates in directory "+d)
      f <- tree(d, skipHidden=true) if f.isFile && f.getName.endsWith("jade") // TODO generalize to more than just jade
    } {
      val baseDir = file(FN.normalizeNoEndSeparator(d.getAbsolutePath()) + "/")
      val fpath = file(FN.normalize(f.getAbsolutePath()))
      val relativePath = relativize(baseDir, fpath).getOrElse(sys.error("error getting relative path for file "+f))

      logger.info("Compiling "+relativePath)
      // TODO: collect all templates that look like models and compile them together w/model classes

      if (isModelViewname(FN.getName(relativePath))) {
        val classname = filenameToClassname(relativePath)
        // reverse lookup on remapped view classes
        val remappedClassname = viewRemappings.map(_.swap).get(classname).map(_.getName).getOrElse(classname)
        logger.info("  Model remapping: "+classname+" => "+remappedClassname)
        compile(
          TemplateSource.fromFile(f, relativePath),
          List(binding[Any]("it").withImportedMembers.withClassName("_root_."+remappedClassname)))
      } else {
        compile(TemplateSource.fromFile(f, relativePath))
      }
    }
  }




}


