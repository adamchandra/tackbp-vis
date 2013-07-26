package net.openreview.scalate

import java.io.{PrintWriter, StringWriter}
import org.fusesource.scalate._
import scala.collection.mutable.ListBuffer

import support.AttributesHashMap

import collection.mutable.Stack
import util.Resource
import com.weiglewilczek.slf4s.Logging


object CustomRenderContext extends Logging {

  // if user passes in a printWriter, they are holding both the string writer and print writer, 
  //   and we should just use the provided one
  def apply(uri: String, eng: CustomTemplateEngine, printWriter: PrintWriter): CustomRenderContext = {
    new CustomRenderContext(uri, eng, printWriter)
  }

  // if user passes in a string writer, we can wrap it in our own printWriter
  def apply(uri: String, eng: CustomTemplateEngine, stringWriter: StringWriter): CustomRenderContext = {
    new CustomRenderContext(uri, eng, new PrintWriter(stringWriter), Some(stringWriter))
  }

  def apply(uri: String, eng: CustomTemplateEngine): CustomRenderContext = {
    val stringWriter = new StringWriter()
    val printWriter = new PrintWriter(stringWriter)
    new CustomRenderContext(uri, eng, printWriter, Some(stringWriter))
  }
}

// TODO move this trait
trait ScalateTemplateImplicits {
  implicit def anyRefToConfigOps(model:AnyRef)(implicit eng:CustomTemplateEngine) = new {
    def template: ModelConfig = ModelConfig(model).withEngine(eng)
  }
}


class CustomRenderContext(
  private val _requestUri: String,
  override implicit val engine: CustomTemplateEngine,
  val printWriter: PrintWriter, 
  val stringWriter: Option[StringWriter] = None 
) extends RenderContext with BindingHelpers with ScalateTemplateImplicits with Logging {
  var out: PrintWriter = printWriter

  def outputString:String = {
    out.flush
    stringWriter.toString
  }

  def bindStr(args: (String, Any)*): CustomRenderContext = {
    for ((key, value) <- args) {
      attributes(key) = value
    }
    this
  }

  def bind(args: (Symbol, Any)*): CustomRenderContext = {
    bindStr((args.map { case (k, v) => k.name -> v }):_*)
  }

  def asHtml(): StringAsHtml = {
    StringAsHtml(outputString)
  }

  def customResolveUri(path: String) = resolveUri(path)

  override def value(any: Any, shouldSanitize: Boolean = escapeMarkup): Any = {
    any match {
      case _ => 
        super.value(any, shouldSanitize)
    }
  }

  override def requestUri = _requestUri
  currentTemplate = requestUri

  def requestResource: Option[Resource] = engine.resourceLoader.resource(requestUri)

  def requestFile: Option[JFile] = requestResource match {
    case Some(r) => r.toFile
    case _ => None
  }

  def <<(v: Any): Unit = {
    out.print(value(v, false).toString)
  }

  def <<<(v: Any): Unit = {
    out.print(value(v).toString)
  }


  private val outStack = new Stack[PrintWriter]

  /**
   * Evaluates the body capturing any output written to this page context during the body evaluation
   */
  def capture(body: => Unit): String = {
    val buffer = new StringWriter();
    outStack.push(out)
    out = new PrintWriter(buffer)
    try {
      val u: Unit = body
      out.close()
      buffer.toString
    } finally {
      out = outStack.pop
    }
  }

  /**
   * Evaluates the template capturing any output written to this page context during the body evaluation
   */
  def capture(template: Template): String = {
    val buffer = new StringWriter();
    outStack.push(out)
    out = new PrintWriter(buffer)
    try {
      logger.debug("rendering template %s".format(template.toString))
      template.render(this)
      out.close()
      buffer.toString
    } finally {
      out = outStack.pop
    }
  }

  import util.Strings.isEmpty

  viewPostfixes = List(".jade")

  def viewCache = engine.viewCache

  def viewUriForModel(model: AnyRef, viewName: String): String = {
    val classSearchList = new ListBuffer[Class[_]]()

    def buildClassList(clazz: Class[_]): Unit = {
      if (clazz != null && clazz != classOf[Object] && clazz != classOf[ScalaObject] && !classSearchList.contains(clazz)) {
        classSearchList.append(clazz);
        buildClassList(clazz.getSuperclass)
        for (
          i <- clazz.getInterfaces 
          if ! (i.getName.startsWith("scala.") || i.getName.startsWith("java."))
        ) {
          buildClassList(i)
        }
      }
    }

    def viewForClass(clazz: Class[_]): String = {
      classNameToViewNames(
        clazz.getName()
      ).headOption.getOrElse(null)
    }

    def classNameToViewNames(className: String): List[String] = {
      (for (prefix <- viewPrefixes; postfix <- viewPostfixes) yield {
        val path = className.replace('.', '/') + "." + viewName + postfix
        val fullPath = if (isEmpty(prefix)) {"/" + path} else {"/" + prefix + "/" + path}
        if (engine.resourceLoader.exists(fullPath)) 
          Some(fullPath)
        else None
      }).filter(
        _.isDefined
      ).map(
        _.get
      )
    }

    def searchForView(): String = {
      for (i <- classSearchList) {
        val rc = viewForClass(i)
        if (rc != null) {
          return rc;
        }
      }
      null
    }


    engine.scalateMode match {
      case ProductionMode =>
        if (!viewCache.contains(model.getClass -> viewName)) {
          val viewPath = engine.viewRemappings.get(
            model.getClass
          ).map(
            classNameToViewNames(_).headOption.getOrElse("??")
          ).getOrElse{
            buildClassList(model.getClass)
            logger.debug("possible model classes: "+classSearchList.mkString(", "))
            searchForView()
          }
          logger.debug("caching class %s (as %s) for view '%s'".format(model.getClass, viewPath, viewName))
          
          viewCache(model.getClass -> viewName) = viewPath
        }
        val templateUri = viewCache(model.getClass -> viewName)

        if (templateUri == null)
          throw new NoSuchViewException(model, viewName)
        else
          templateUri


      case LayoutMode =>
        val realClassname = model.getClass.getName
        val effectiveClassname = if (realClassname.endsWith("Mockup")) realClassname.dropRight("Mockup".length) else realClassname
        logger.info("Layout mode: using class "+realClassname)
        classNameToViewNames(
          effectiveClassname
        ).headOption.getOrElse {
          classNameToViewNames(
            realClassname
          ).headOption getOrElse {
            throw new NoSuchViewException(model, viewName)
          }
        }

      case DevMode =>
        if (!viewCache.contains(model.getClass -> viewName)) {
          val viewPath = engine.viewRemappings.get(
            model.getClass
          ).map(
            classNameToViewNames(_).headOption.getOrElse("??")
          ).getOrElse{
            buildClassList(model.getClass)
            logger.debug("viewable model classes: "+classSearchList.mkString(", "))
            searchForView()
          }
          logger.debug("caching class %s (as %s) for view '%s'".format(model.getClass, viewPath, viewName))
          viewCache(model.getClass -> viewName) = viewPath
        }
        val templateUri = viewCache(model.getClass -> viewName)

        if (templateUri == null)
          throw new NoSuchViewException(model, viewName)
        else
          templateUri
    }

  }
  /**
    * Renders the view of the given model object, looking for the view in
    * packageName/className.viewName.ext
    */
  def template(model: AnyRef, viewName: String = "index", layout:Boolean=false)(
    attrs: (String, Any)*
  ): Unit = {
    val attMap = attrs.foldLeft(Map[String, Any]()){case (acc, e) => acc + e}
    using(model) {
      withAttributes(attMap) {
        include(
          path=viewUriForModel(model, viewName),
          layout=layout,
          extraBindings=Seq(
            binding[Any]("it").withClassName("_root_."+model.getClass.getName).withImportedMembers // .makeImplicit
          ))
      }
    }
  }


  override def view(model: AnyRef, viewName: String = "index"): Unit = {
    using(model) {
      include(
        path=viewUriForModel(model, viewName),
        layout=false,
        extraBindings=Seq(
          binding[Any]("it").withClassName("_root_."+model.getClass.getName).withImportedMembers // .makeImplicit
        ))
    }
  }

  def templateCache = engine.templateCache

  /**
   * Includes the given template path
   *
   * @param layout if true then applying the layout the included template
   */
  override def include(path: String, layout: Boolean, extraBindings: Traversable[Binding]): Unit = {
    val uri = resolveUri(path)
    logger.info("include("+path+", "+layout+", "+extraBindings.mkString(",")+")"+"  resolvedUri="+uri)
    logger.info("templateCache="+templateCache.mkString(", "))
    withUri(uri) {
      if (!templateCache.contains(currentTemplate -> uri)) {
        templateCache(currentTemplate -> uri) = engine.load(uri, extraBindings)
      }
      val template = templateCache(currentTemplate -> uri)
      if (layout) {
        engine.layout(template, this);
      }
      else {
        template.render(this);
      }
    }
  }


  override protected def resolveUri(path: String) = {
    if (currentTemplate != null) {
      engine.resourceLoader.resolve(currentTemplate, path);
    } else {
      path
    }
  }

  val attributes: AttributeMap = new AttributesHashMap() {
    update("context", CustomRenderContext.this)
  }

  escapeMarkup = engine.escapeMarkup

}

