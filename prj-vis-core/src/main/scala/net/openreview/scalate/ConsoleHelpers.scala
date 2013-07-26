package net.openreview.scalate

import _root_.java.util.regex.Pattern
import _root_.org.fusesource.scalate.{DefaultRenderContext, RenderContext}
import scala.io.Source
import collection.JavaConversions._
import collection.immutable.SortedMap
import collection.mutable.{ArrayBuffer, ListBuffer}
import scala.util.parsing.input.Position
import scala.util.parsing.input.OffsetPosition

import xml.NodeSeq
import org.fusesource.scalate._
import org.fusesource.scalate.util._
import org.fusesource.scalate.support.AttributesHashMap


class TemplateRuntimeException(val brief: String, val cause:RuntimeException, val renderContext: CustomRenderContext) extends TemplateException(brief, cause) 

case class SourceLine(line: Int, source: String) {
  def style(errorLine: Int): String = if (line == errorLine) "line error" else "line"
  
  def nonBlank = source != null && source.length > 0
  
  
  /**
    * Return a tuple of the prefix, the error character and the postfix of this source line
    * to highlight the error at the given column
    */
  def splitOnCharacter(col: Int): Tuple3[String, String, String] = {
    val length = source.length
    if (col >= length) {
      (source, "", "")
    }
    else {
      val next = col + 1
      val prefix = source.substring(0, col)
      val ch = if (col < length) source.substring(col, next) else ""
      val postfix = if (next < length) source.substring(next, length) else ""
      (prefix, ch, postfix)
    }
  }
}

 
class ConsoleHelper(engine: EngineLike, attributes: AttributeMap = new AttributesHashMap()) extends ConsoleSnippets {
  override def templateEngine = templateEngine

  def attributeOrElse[T](name: String, defaultValue: => T): T = {
    attributes.get(name)
            .getOrElse(defaultValue)
            .asInstanceOf[T]
  }

  val consoleParameter = "_scalate"

  // TODO figure out the viewName from the current template?
  def viewName = "index"

  /**
   * Returns the class name of the current resource
   */
  def resourceClassName: Option[String] = attributes.get("it") match {
    case Some(it: AnyRef) => Some(it.getClass.getName)
    case _ => None
  }

  // def isDevelopmentMode = context.engine.isDevelopmentMode

  /**
   * Returns an attempt at finding the source file for the current resource.
   *
   * TODO use bytecode swizzling to find the accurate name from the debug info in
   * the class file!
   */
  def resourceSourceFile: Option[JFile] = resourceClassName match {
    case Some(name: String) =>
      val fileName = name.replace('.', '/')
      // TODO: use the template engine to do this:
      val prefixes = List("scalate-work-tmp/src/", "target/scala-2.9.2/src_managed/main/", "app/", "src/main/scala/", "src/main/java/")
      val postfixes = List(".scala", ".java")

      val names = for (prefix <- prefixes; postfix <- postfixes) yield new JFile(prefix + fileName + postfix)
      names.find(_.exists)

    case _ => None
  }


  /**
   * Creates the newly created template name if there can be one for the current resource
   */
  def newTemplateName(): Option[String] = resourceClassName match {
    case Some(resource) =>
      val prefix = "/" + resource.replace('.', '/') + "."

      if (templates.exists(_.startsWith(prefix)) == false) {
        Some(prefix + viewName)
      }
      else {
        None
      }
    case _ => None
  }

  def getListAttribute[T](s:String): Option[List[T]] =  
    attributes.get(s).map(_.asInstanceOf[List[T]])

  /**
   * Returns the current template names used in the current context
   */
  def templates: List[String] = getListAttribute[String]("scalateTemplates") match {
    case Some(list: List[String]) => list.distinct.sortWith(_ < _)
    case _ => Nil
  }

  /**
   * Returns the current layouts used in the current context
   */
  // def layouts: List[String] = attributes.get("scalateLayouts").asInstanceOf[Option[List[String]]] match {
  def layouts: List[String] = getListAttribute[String]("scalateLayouts") match {
    case Some(list: List[String]) => list.distinct.sortWith(_ < _)
    case _ => Nil
  }

  // def layouts: List[String] = attributes.get("scalateLayouts").asInstanceOf[Option[List[String]]].map(_.distinct.sortWith(_ < _))


  /**
   * Retrieves a chunk of lines either side of the given error line
   */
  def lines(template: String, errorLine: Int, chunk: Int): Seq[SourceLine] = {
    val file = realPath(template)
    if (file != null) {
      val source = Source.fromFile(file)
      val start = ((errorLine-1) - chunk).max(0)
      source.getLines().zipWithIndex.drop(start).take(chunk*2).map({
        case (e, i) => SourceLine(i+1, e) 
      }).toSeq
    }
    else {
      Nil
    }
  }

  /**
   * Retrieves a chunk of lines either side of the given error line
   */
  def lines(template: String, pos: Position, chunk: Int = 5): Seq[SourceLine] = {
    pos match {
      case op: OffsetPosition =>

        // OffsetPosition's already are holding onto the file contents
        val index: Array[String] = {
          val source = op.source
          var rc = new ArrayBuffer[String]
          var start = 0;
          for (i <- 0 until source.length) {
            if (source.charAt(i) == '\n') {
              rc += source.subSequence(start, i).toString.stripLineEnd
              start = i + 1
            }
          }
          rc.toArray
        }

        val start = (pos.line - chunk).max(1)
        val end = (pos.line + chunk).min(index.length)

        val list = new ListBuffer[SourceLine]
        for (i <- start to end) {
          list += SourceLine(i, index(i - 1))
        }
        list


      case _ =>

        // We need to manually load the file..
        lines(template, pos.line, chunk)
    }

  }

  def systemProperties: SortedMap[String,String] = {
    // TODO is there a better way?
    val m: Map[String,String] = System.getProperties.toMap
    SortedMap(m.iterator.toSeq :_*)
  }

  def renderStackTraceElement(stack:StackTraceElement): NodeSeq = {
    val clsExtra = if (stack.getClassName().startsWith("scala") || stack.getClassName().startsWith("play"))
      "elidable"
    else
      "important"

    val generic = <li class={"stacktrace " + clsExtra}>at {stack.getClassName}.{stack.getMethodName}({stack.getFileName}:{stack.getLineNumber})</li>

    // Does it look like a scalate template class??
    val className = stack.getClassName.split(Pattern.quote(".")).last

    if(className.contains("$_scalate_$")) {
      // Then try to load it's smap info..
      val file = new JFile(templateEngine.bytecodeDirectory, stack.getClassName.replace('.', '/')+".class")
      try {
        val smap = SourceMap.parse(SourceMapInstaller.load(file))
        // And then render a link to the original template file.
        smap.mapToStratum(stack.getLineNumber) match {
          case None =>
            generic
          case Some((file, line)) =>
            editLink(file, Some(line), Some(1)) {
              <pre class="stacktrace">at ({file}:{line})</pre>
            }
        }
      } catch {
        // ignore errors trying to load the smap... we can fallback
        // to rendering a plain stack line.
        case e:Throwable=>
          generic
      }
    } else {
      generic
    }
  }
}


import _root_.org.fusesource.scalate.DefaultRenderContext
import scala.xml.NodeSeq

/**
 * @version $Revision : 1.1 $
 */
trait ConsoleSnippets {
  // def servletContext: ServletContext

  // def renderContext: DefaultRenderContext
  def templateEngine: TemplateEngine


  def realPath(uri: String) = uri

  /**
   * returns an edit link for the given URI, discovering the right URL
   * based on your OS and whether you have TextMate installed and whether you
   * have defined the <code>scalate.editor</code> system property
   */
  def editLink(template: String)(body: => Unit): NodeSeq = editLink(template, None, None)(body)

  def editLink(template: String, line: Int, col: Int)(body: => Unit): NodeSeq = editLink(template, Some(line), Some(col))(body)

  /**
   * returns an edit link for the given URI, discovering the right URL
   * based on your OS and whether you have TextMate installed and whether you
   * have defined the <code>scalate.editor</code> system property
   */
  def editLink(filePath: String, line: Option[Int], col: Option[Int])(body: => Unit): NodeSeq = {
    // It might be a real file path
    if( filePath!=null ) {
      val file = new JFile(filePath);
      val actualPath = if (file.exists) {
        file.getCanonicalPath
      } else {
        realPath(filePath)
      }
      EditLink.editLink(actualPath, line, col)(body)
    } else {
      <span>{body}</span>
    }
  }

  /**
   * returns an edit link for the given file, discovering the right URL
   * based on your OS and whether you have TextMate installed and whether you
   * have defined the <code>scalate.editor</code> system property
   */
  def editFileLink(template: String)(body: => Unit): NodeSeq = editFileLink(template, None, None)(body)

  /**
   * returns an edit link for the given file, discovering the right URL
   * based on your OS and whether you have TextMate installed and whether you
   * have defined the <code>scalate.editor</code> system property
   */
  def editFileLink(file: String, line: Option[Int], col: Option[Int])(body: => Unit): NodeSeq = {
    EditLink.editLink(file, line, col)(body)
  }


  def shorten(file: JFile): String = shorten(file.getPath)

  def shorten(file: String): String = {
    if( file==null ) {
      "<unknown>"
    } else {
      var root = templateEngine.workingDirectory.getPath;
      if (file.startsWith(root)) {
        file.substring(root.length + 1)
      } else {
        sourcePrefixes.find(file.startsWith(_)) match {
          case Some(prefix) => file.substring(prefix.length + 1)
          case _ => file
        }
      }
    }
  }


  def exists(fileName: String) = new JFile(fileName).exists

  protected var sourcePrefixes = List("src/main/scala", "src/main/java")
}

import _root_.org.fusesource.scalate.util.{SourceMap}
import org.fusesource.scalate.RenderContext.captureNodeSeq
import xml.{Text, Elem, NodeSeq}

/**
 * @version $Revision : 1.1 $
 */

object EditLink {
  var idePluginPort = 51235

  def editLink(file: String)(body: => Unit): NodeSeq = editLink(file, None, None)(body)

  def editLink(file: String, line: Option[Int], col: Option[Int])(body: => Unit): NodeSeq = {
    if (file == null) {
      Nil
    }
    else {
      System.getProperty("scalate.editor", "") match {
        case "textmate" => editLinkTextMate(file, line, col)(body)
        case "ide" => editLinkIdePlugin(file, line, col)(body)
        case "file" => editLinkFileScheme(file, line, col)(body)
        case _ =>
          if (isMacOsx && hasTextMate)
            editLinkTextMate(file, line, col)(body)
          else {
            editLinkFileScheme(file, line, col)(body)
          }
      }
    }
  }

  def editLinkFileScheme(file: String, line: Option[Int], col: Option[Int])(body: => Unit): NodeSeq = {
    val bodyText = captureNodeSeq(body)
    <a href={"file://" + file} title="Open File" target="_blank">
      {bodyText}
    </a>
  }

  def editLinkTextMate(file: String, line: Option[Int], col: Option[Int])(body: => Unit): NodeSeq = {
    val bodyText = captureNodeSeq(body)
    val href = "txmt://open?url=file://" + file +
            (if (line.isDefined) "&line=" + line.get else "") +
            (if (col.isDefined) "&col=" + col.get else "")

    <a href={href} title="Open in TextMate">
      {bodyText}
    </a>
  }

  def editLinkIdePlugin(file: String, line: Option[Int], col: Option[Int])(body: => Unit): NodeSeq = {
    val bodyText = captureNodeSeq(body)

    // The Atlassian IDE plugin seems to highlight the line after the actual line number, so lets subtract one
    val lineNumber = if (line.isDefined) {
      val n = line.get
      if (n > 1) n - 1 else 0
    }
    else 0

    <span>
      {bodyText}<img class="ide-icon tb_right_mid"
                     id={"ide-" + file.hashCode}
                     title={bodyText}
                     onclick={"this.src='http://localhost:" + idePluginPort + "/file?file=" + file + "&line=" + lineNumber + "&id=' + Math.floor(Math.random()*1000);"}
                     alt="Open in IDE"
                     src={"http://localhost:" + idePluginPort + "/icon"}/>
    </span>
  }


  def isMacOsx = System.getProperty("os.name", "").contains("Mac OS X")

  def hasTextMate = exists("/Applications/TextMate.app") || exists("~/Applications/TextMate.app")

  def exists(fileName: String) = new JFile(fileName).exists
}
