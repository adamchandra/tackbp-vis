/*
 * Copyright 2007-2011 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.openreview.scalate
package ajax

import scala.xml.{Node, NodeSeq, Group, Unparsed, Elem}

import java.util.concurrent.atomic.AtomicLong
import java.security.SecureRandom
import net.openreview.util.TimeHelpers._
import JqJsCmds._
import JqJE._

import StringHelpers._

object JsCommands {
  def create = new JsCommands(Nil)

  def apply(in: Seq[JsCmd]) = new JsCommands(in.toList.reverse)

  def apply(in: JsExp) = new JsCommands(List(in.cmd))
}

// case class InMemoryResponse(data, List("Content-Length" -> data.length.toString, "Content-Type" -> "text/javascript; charset=utf-8"), S.responseCookies, 200)
case class InMemoryResponse(data: Array[Byte], headers: List[(String, String)], cookies: List[(String, String)], statusCode: Int)

class JsCommands(val reverseList: List[JsCmd]) {
  def &(in: JsCmd) = new JsCommands(in :: reverseList)

  def &(in: List[JsCmd]) = new JsCommands(in.reverse ::: reverseList)

  def toResponse = {
    val data = reverseList.reverse.map(_.toJsCmd).mkString("\n").getBytes("UTF-8")
    // InMemoryResponse(data, List("Content-Length" -> data.length.toString, "Content-Type" -> "text/javascript; charset=utf-8"), S.responseCookies, 200)
    InMemoryResponse(data, List("Content-Length" -> data.length.toString, "Content-Type" -> "text/javascript; charset=utf-8"), List(), 200)
  }

  def toJsCmd: String = {
    reverseList.reverse.map(_.toJsCmd).mkString("\n")
  }
}

case class JsonCall(funcId: String)  extends StringHelpers{
  def exp(exp: JsExp): JsCmd = JsCmds.Run(funcId + "(" + exp.toJsCmd + ");")

  def apply(command: String): JsCmd = apply(JE.Str(command))

  def apply(command: JsExp): JsCmd =
  JsCmds.Run(funcId + "({'command': " + command.toJsCmd + ", 'params': false});")

  def apply(command: String, params: JsExp) =
  JsCmds.Run(funcId + "({'command': " + command.encJs + ", 'params':" +
             params.toJsCmd + "});")

  def apply(command: String, target: String, params: JsExp) =
  JsCmds.Run(funcId + "({'command': " + command.encJs + ", 'target': " +
             target.encJs +
             ", 'params':" +
             params.toJsCmd + "});")


  def apply(command: JsExp, params: JsExp) =
  JsCmds.Run(funcId + "({'command': " + command.toJsCmd + ", 'params':" +
             params.toJsCmd + "});")

  def apply(command: JsExp, target: JsExp, params: JsExp) =
  JsCmds.Run(funcId + "({'command': " + command.toJsCmd + ", 'target': " +
             target.toJsCmd +
             ", 'params':" +
             params.toJsCmd + "});")

}


trait JsObj extends JsExp {
  def props: List[(String, JsExp)]

  def toJsCmd = props.map {case (n, v) => n.encJs + ": " + v.toJsCmd}.mkString("{", ", ", "}")

  override def toString(): String = toJsCmd

  override def equals(other: Any): Boolean = {
    other match {
      case jsObj: JsObj => {
        import scala.annotation.tailrec

        @tailrec def test(me: Map[String, JsExp], them: List[(String, JsExp)]): Boolean = {
          them match {
            case Nil => me.isEmpty
            case _ if me.isEmpty => false
            case (k, v) :: xs =>
              me.get(k) match {
                case None => false
                case Some(mv) if mv != v => false
                case _ => test(me - k, xs)
              }
          }
        }

        test(Map(props :_*), jsObj.props)
      }

      case x => super.equals(x)
    }
  }

  def +*(other: JsObj) = {
    val np = props ::: other.props
    new JsObj {
      def props = np
    }
  }
}

/**
 * The companion object to JsExp that has some
 * helpful conversions to/from Lift's JSON library
 */
object JsExp {
  import org.json4s._
  import org.json4s.native.JsonMethods._
  // import json._

  implicit def jValueToJsExp(jv: JValue): JsExp = new JsExp {
    lazy val toJsCmd = compact(render(jv))
  }

  implicit def strToJsExp(str: String): JE.Str = JE.Str(str)

  implicit def boolToJsExp(b: Boolean): JsExp = JE.boolToJsExp(b)

  implicit def intToJsExp(in: Int): JE.Num = JE.Num(in)

  implicit def longToJsExp(in: Long): JE.Num = JE.Num(in)

  implicit def doubleToJsExp(in: Double): JE.Num = JE.Num(in)

  implicit def floatToJsExp(in: Float): JE.Num = JE.Num(in)

  implicit def numToJValue(in: JE.Num): JValue = in match {
    case JE.Num(n) => JDouble(n.doubleValue())
  }

  implicit def strToJValue(in: JE.Str): JValue = JString(in.str)
}

/**
 * The basic JavaScript expression
 */
trait JsExp extends HtmlFixer with ToJsCmd {
  def toJsCmd: String

  override def equals(other: Any): Boolean = {
    other match {
      case jx: JsExp => this.toJsCmd == jx.toJsCmd
      case _ => super.equals(other)
    }
  }

  override def toString = "JsExp("+toJsCmd+")"


  /** short alias for java.security.SecureRandom */
  private val _random = new SecureRandom

  private def withRandom[T](f: SecureRandom => T): T =
    _random.synchronized(f(_random))

  /** return a random Long modulo a number */
  def randomLong(mod: Long): Long = withRandom(random => math.abs(random.nextLong) % mod)

  private val serial = new AtomicLong(math.abs(randomLong(millis)) + 1000000L)

  /**
   * Get a monotonically increasing number that's guaranteed to be unique for the
   * current session
   */
  def nextNum = serial.incrementAndGet

  /**
   * Get a guaranteed unique field name
   * (16 or 17 letters and numbers, starting with a letter)
   */
  def nextFuncName: String = nextFuncName(0)

  /**
   * Get a guaranteed unique field name
   * (16 or 17 letters and numbers, starting with a letter)
   */
  def nextFuncName(seed: Long): String = {
    val sb = new StringBuilder(24)
    sb.append('F')
    sb.append(nextNum + seed)
    // sb.append('_')
    sb.append(randomString(6))
    sb.toString
  }

  def appendToParent(parentName: String): JsCmd = {
    val ran = "v" + nextFuncName
    JsCmds.JsCrVar(ran, this) &
    JE.JsRaw("if (" + ran + ".parentNode) " + ran + " = " + ran + ".cloneNode(true)").cmd &
    JE.JsRaw("if (" + ran + ".nodeType) {" + parentName + ".appendChild(" + ran + ");} else {" +
      parentName + ".appendChild(document.createTextNode(" + ran + "));}").cmd
  }

  /**
   * ~> accesses a property in the current JsExp
   */
  def ~>(right: JsMember): JsExp = new JsExp {
    def toJsCmd = JsExp.this.toJsCmd + "." + right.toJsCmd
  }

  def ~>(right: Option[JsMember]): JsExp = right.map(r => ~>(r)).getOrElse(this)

  def cmd: JsCmd = JsCmds.Run(toJsCmd + ";")

  def +(right: JsExp): JsExp = new JsExp {
    def toJsCmd = JsExp.this.toJsCmd + " + " + right.toJsCmd
  }

  def ===(right: JsExp): JsExp = new JsExp {
    def toJsCmd = JsExp.this.toJsCmd + " = " + right.toJsCmd
  }

}

trait JsMember {
  def toJsCmd: String
}

/**
 * JavaScript Expressions. To see these in action, check out
 * sites/example/src/webapp/json.html
 */
object JE extends StringHelpers {
  def boolToJsExp(in: Boolean): JsExp = if (in) JsTrue else JsFalse

  /**
   * The companion object to Num which has some helpful
   * constructors
   */
  object Num {
    def apply(i: Int): Num = new Num(i)
    def apply(lng: Long): Num = new Num(lng)
    def apply(d: Double): Num = new Num(d)
    def apply(f: Float): Num = new Num(f)
  }

  case class Num(n: Number) extends JsExp {
    def toJsCmd = n.toString
  }

  case class Stringify(in: JsExp) extends JsExp {
    def toJsCmd = "JSON.stringify(" + in.toJsCmd + ")"
  }

  case class JsArray(in: JsExp*) extends JsExp {
    def toJsCmd = new JsExp {
      def toJsCmd = in.map(_.toJsCmd).mkString("[", ", ", "]\n")
    }.toJsCmd

    def this(in: List[JsExp]) = this (in: _*)
  }

  object JsArray {
    def apply(in: List[JsExp]) = new JsArray(in: _*)
  }

  case class ValById(id: String) extends JsExp {
    def toJsCmd = "(function() {if (document.getElementById(" + id.encJs + ")) {return document.getElementById(" + id.encJs + ").value;} else {return null;}})()"
  }

  /**
   * Given the id of a checkbox, see if it's checked
   */
  case class CheckedById(id: String) extends JsExp {
    def toJsCmd = "(function() {if (document.getElementById(" + id.encJs + ")) {return document.getElementById(" + id.encJs + ").checked} else {return false;}})()"
  }

  /**
   * gets the element by ID
   */
  case class ElemById(id: String, then: String*) extends JsExp {
    override def toJsCmd = "document.getElementById(" + id.encJs + ")" + (
      if (then.isEmpty) "" else then.mkString(".", ".", "")
    )
  }

  /**
   * Gives the parent node of the node denominated by the id
   *
   * @param id - the id of the node
   */
  case class ParentOf(id: String) extends JsExp {
    def toJsCmd = (ElemById(id) ~> Parent).toJsCmd
  }

// ++   object LjSwappable {
// ++     def apply(visible: JsExp, hidden: JsExp): JxBase = {
// ++       new JxNodeBase {
// ++         def child = Nil
// ++ 
// ++         def appendToParent(name: String): JsCmd =
// ++         JsRaw(name + ".appendChild(lift$.swappable(" + visible.toJsCmd
// ++               + ", " + hidden.toJsCmd + "))").cmd
// ++       }
// ++     }
// ++ 
// ++     def apply(visible: NodeSeq, hidden: NodeSeq): JxBase = {
// ++       new JxNodeBase {
// ++         def child = Nil
// ++ 
// ++         def appendToParent(name: String): JsCmd =
// ++         JsRaw(name + ".appendChild(lift$.swappable(" + AnonFunc(
// ++             JsCmds.JsCrVar("df", JsRaw("document.createDocumentFragment()")) &
// ++             addToDocFrag("df", visible.toList) &
// ++             JE.JsRaw("return df").cmd
// ++           ).toJsCmd
// ++               + "(), " + AnonFunc(JsCmds.JsCrVar("df", JsRaw("document.createDocumentFragment()")) &
// ++                                   addToDocFrag("df", hidden.toList) &
// ++                                   JE.JsRaw("return df").cmd).toJsCmd + "()))").cmd
// ++       }
// ++     }
// ++   }

  object LjBuildIndex {
    def apply(obj: String,
              indexName: String, tables: (String, String)*): JsExp = new JsExp {
      def toJsCmd = "lift$.buildIndex(" + obj + ", " + indexName.encJs +
      (if (tables.isEmpty) "" else ", " +
       tables.map {case (l, r) => "[" + l.encJs + ", " + r.encJs + "]"}.mkString(", ")) +
      ")"
    }

    def apply(obj: JsExp,
              indexName: String, tables: (String, String)*): JsExp = new JsExp {
      def toJsCmd = "lift$.buildIndex(" + obj.toJsCmd + ", " + indexName.encJs +
      (if (tables.isEmpty) "" else ", " +
       tables.map {case (l, r) => "[" + l.encJs + ", " + r.encJs + "]"}.mkString(", ")) +
      ")"
    }
  }

  protected trait MostLjFuncs {
    def funcName: String

    def apply(obj: String, func: String): JsExp = new JsExp {
      def toJsCmd = "lift$." + funcName + "(" + obj + ", " + func.encJs + ")"
    }

    def apply(obj: JsExp, func: JsExp): JsExp = new JsExp {
      def toJsCmd = "lift$." + funcName + "(" + obj.toJsCmd + ", " + func.toJsCmd + ")"
    }
  }

  object LjAlt {
    def apply(obj: String, func: String, alt: String): JsExp = new JsExp {
      def toJsCmd = "lift$.alt(" + obj + ", " + func.encJs + ", " + alt.encJs + ")"
    }

    def apply(obj: JsExp, func: JsExp, alt: String): JsExp = new JsExp {
      def toJsCmd = "lift$.alt(" + obj.toJsCmd + ", " + func.toJsCmd + ", " + alt.encJs + ")"
    }

    def apply(obj: JsExp, func: JsExp, alt: JsExp): JsExp = new JsExp {
      def toJsCmd = "lift$.alt(" + obj.toJsCmd + ", " + func.toJsCmd + ", " + alt.toJsCmd + ")"
    }
  }

  object LjMagicUpdate {
    def apply(obj: String, field: String, idField: String, toUpdate: JsExp): JsExp = new JsExp {
      def toJsCmd = "lift$.magicUpdate(" + obj + ", " + field.encJs + ", " + idField.encJs + ", " + toUpdate.toJsCmd + ")"
    }

    def apply(obj: JsExp, field: String, idField: String, toUpdate: JsExp): JsExp = new JsExp {
      def toJsCmd = "lift$.magicUpdate(" + obj.toJsCmd + ", " + field.encJs + ", " + idField.encJs + ", " + toUpdate.toJsCmd + ")"
    }
  }

  object LjForeach extends MostLjFuncs {
    def funcName: String = "foreach"
  }

  object LjFilter extends MostLjFuncs {
    def funcName: String = "filter"
  }

  object LjMap extends MostLjFuncs {
    def funcName: String = "map"
  }

  object LjFold {
    def apply(what: JsExp, init1: JsExp, func: String): JsExp = new JsExp {
      def toJsCmd = "lift$.fold(" + what.toJsCmd + ", " + init1.toJsCmd + ", " + func.encJs + ")"
    }

    def apply(what: JsExp, init1: JsExp, func: AnonFunc): JsExp = new JsExp {
      def toJsCmd = "lift$.fold(" + what.toJsCmd + ", " + init1.toJsCmd + ", " + func.toJsCmd + ")"
    }
  }

  object LjFlatMap extends MostLjFuncs {
    def funcName: String = "flatMap"
  }

  object LjSort extends MostLjFuncs {
    def funcName: String = "sort"

    def apply(obj: String): JsExp = new JsExp {
      def toJsCmd = "lift$." + funcName + "(" + obj + ")"
    }

    def apply(obj: JsExp): JsExp = new JsExp {
      def toJsCmd = "lift$." + funcName + "(" + obj.toJsCmd + ")"
    }
  }

  object FormToJSON {
    def apply(formId: String) = new JsExp {
      def toJsCmd = JQueryArtifacts.formToJSON(formId).toJsCmd;
    }
  }

  /**
   * A String (JavaScript encoded)
   */
  case class Str(str: String) extends JsExp {
    def toJsCmd = str.encJs
  }

  /**
   * A JavaScript method that takes parameters
   *
   * JsFunc is very similar to Call but only the latter will be implicitly converted to a JsCmd.
   * @see Call
   */
  case class JsFunc(method: String, params: JsExp*) extends JsMember {
    def toJsCmd = params.map(_.toJsCmd).mkString(method + "(", ", ", ")")

    def cmd: JsCmd = JsCmds.Run(toJsCmd + ";")
  }

  /**
   * Put any JavaScript expression you want in here and the result will be
   * evaluated.
   */
  case class JsRaw(rawJsCmd: String) extends JsExp {
    def toJsCmd = rawJsCmd
  }

  case class JsVar(varName: String, andThen: String*) extends JsExp {
    def toJsCmd = varName + (if (andThen.isEmpty) ""
                             else andThen.mkString(".", ".", ""))
  }

  /**
   * A value that can be retrieved from an expression
   */
  case class JsVal(valueName: String) extends JsMember {
    def toJsCmd = valueName
  }

  case object Id extends JsMember {
    def toJsCmd = "id"
  }

  case object Parent extends JsMember {
    def toJsCmd = "parentNode"
  }

  case object Style extends JsMember {
    def toJsCmd = "style"
  }

  case object Value extends JsMember {
    def toJsCmd = "value"
  }

  case object JsFalse extends JsExp {
    def toJsCmd = "false"
  }

  case object JsNull extends JsExp {
    def toJsCmd = "null"
  }

  case object JsTrue extends JsExp {
    def toJsCmd = "true"
  }

  /**
   * A JavaScript method that takes parameters
   *
   * Call is very similar to JsFunc but only the former will be implicitly converted to a JsCmd.
   * @see JsFunc
   */
  case class Call(function: String, params: JsExp*) extends JsExp {
    def toJsCmd = function + "(" + params.map(_.toJsCmd).mkString(",") + ")"
  }

  trait AnonFunc extends JsExp {
    def applied: JsExp = new JsExp {
      def toJsCmd = "(" + AnonFunc.this.toJsCmd + ")" + "()"
    }

    def applied(params: JsExp*): JsExp = new JsExp {
      def toJsCmd = "(" + AnonFunc.this.toJsCmd + ")" +
      params.map(_.toJsCmd).mkString("(", ",", ")")
    }

  }

  object AnonFunc {
    def apply(in: JsCmd): AnonFunc = new JsExp with AnonFunc {
      def toJsCmd = "function() {" + in.toJsCmd + "}"
    }

    def apply(params: String, in: JsCmd): AnonFunc = new JsExp with AnonFunc {
      def toJsCmd = "function(" + params + ") {" + in.toJsCmd + "}"
    }
  }

  object JsObj {
    def apply(members: (String, JsExp)*): JsObj = new JsObj {
      def props = members.toList
    }
  }

  case class JsLt(left: JsExp, right: JsExp) extends JsExp {
    def toJsCmd = left.toJsCmd + " < " + right.toJsCmd
  }

  case class JsGt(left: JsExp, right: JsExp) extends JsExp {
    def toJsCmd = left.toJsCmd + " > " + right.toJsCmd
  }

  case class JsEq(left: JsExp, right: JsExp) extends JsExp {
    def toJsCmd = left.toJsCmd + " == " + right.toJsCmd
  }

  case class JsNotEq(left: JsExp, right: JsExp) extends JsExp {
    def toJsCmd = left.toJsCmd + " != " + right.toJsCmd
  }

  case class JsLtEq(left: JsExp, right: JsExp) extends JsExp {
    def toJsCmd = left.toJsCmd + " <= " + right.toJsCmd
  }

  case class JsGtEq(left: JsExp, right: JsExp) extends JsExp {
    def toJsCmd = left.toJsCmd + " >= " + right.toJsCmd
  }

  case class JsOr(left: JsExp, right: JsExp) extends JsExp {
    def toJsCmd = left.toJsCmd + " || " + right.toJsCmd
  }

  case class JsAnd(left: JsExp, right: JsExp) extends JsExp {
    def toJsCmd = left.toJsCmd + " && " + right.toJsCmd
  }

  case class JsNot(exp: JsExp) extends JsExp {
    def toJsCmd = "!" + exp.toJsCmd
  }


}

trait HtmlFixer {
// --   /**
// --    * Super important... call fixHtml at instance creation time and only once
// --    * This method must be run in the context of the thing creating the XHTML
// --    * to capture the bound functions
// --    */
// --   @deprecated("Use fixHtmlAndJs or fixHtmlFunc", "2.4")
// --   protected def fixHtml(uid: String, content: NodeSeq): String = {
// --     val w = new java.io.StringWriter
// -- 
// --     S.htmlProperties.
// --     htmlWriter(Group(S.session.
// --                      map(s =>
// --                        s.fixHtml(s.processSurroundAndInclude("JS SetHTML id: "
// --                                                              + uid,
// --                                                              content))).
// --                      openOr(content)),
// --                w)
// --     w.toString.encJs
// --   }

   /**
     * Calls fixHtmlAndJs and if there's embedded script tags,
     * construct a function that executes the contents of the scripts
     * then evaluations to Expression.  For use when converting
     * a JsExp that contains HTML.
     */
   def fixHtmlFunc(uid: String, content: NodeSeq)(f: String => String) =
     fixHtmlAndJs(uid, content) match {
       case (str, Nil) => f(str)
       case (str, cmds) => "((function() {"+cmds.reduceLeft{_ & _}.toJsCmd+" return "+f(str)+";})())"
     }
 
   /**
     * Calls fixHtmlAndJs and if there's embedded script tags,
     * append the JsCmds to the String returned from applying
     * the function to the enclosed HTML.
     * For use when converting
     * a JsCmd that contains HTML.
     */
   def fixHtmlCmdFunc(uid: String, content: NodeSeq)(f: String => String) =
     fixHtmlAndJs(uid, content) match {
       case (str, Nil) => f(str)
       case (str, cmds) => f(str)+"; "+cmds.reduceLeft(_ & _).toJsCmd
     }
 
   /**
     * Super important... call fixHtml at instance creation time and only once
     * This method must be run in the context of the thing creating the XHTML
     * to capture the bound functions
     */
   protected def fixHtmlAndJs(uid: String, content: NodeSeq): (String, List[JsCmd]) = {
     import Helpers._
 
     val w = new java.io.StringWriter
 
     // val xhtml = S.session.map(s =>
     //     s.fixHtml(s.processSurroundAndInclude("JS SetHTML id: "
     //       + uid,
     //       content))).
     //   openOr(content)
 
     val xhtml = content.toString
 
     import scala.collection.mutable.ListBuffer
     val lb = new ListBuffer[JsCmd]
     // TODO maybe?
     //val revised = ("script" #> nsFunc(ns => {
     //  ns match {
     //    case FindScript(e) => {
     //      lb += JE.JsRaw(ns.text).cmd
     //      NodeSeq.Empty
     //    }
     //    case x => x
     //  }
     //})).apply(xhtml)
     //
     //S.htmlProperties.htmlWriter(Group(revised), w)
 
     // (w.toString.encJs, lb.toList)
     (xhtml.encJs, lb.toList)
   }
 
   private object FindScript {
     def unapply(in: NodeSeq): Option[Elem] = in match {
       case e: Elem => {
         e.attribute("type").map(_.text).filter(_ == "text/javascript").flatMap {
           a =>
             if (e.attribute("src").isEmpty) Some(e) else None
         }
       }
       case _ => None
     }
   }

}

trait JsCmd extends HtmlFixer with ToJsCmd {
  def &(other: JsCmd): JsCmd = JsCmds.CmdPair(this, other)

  def toJsCmd: String

  override def toString() = "JsCmd("+toJsCmd+")"
}

object JsCmd {
  /**
   * If you've got Unit and need a JsCmd, return a Noop
   */
  implicit def unitToJsCmd(in: Unit): JsCmd = JsCmds.Noop
}

object JsCmds extends StringHelpers  {
  implicit def seqJsToJs(in: Seq[JsCmd]): JsCmd = in.foldLeft[JsCmd](Noop)(_ & _)

  object Script {
    def apply(script: JsCmd): Node = (
      <script type="text/javascript">{
        Unparsed(
          """|
             |// <![CDATA[
             |""".stripMargin + fixEndScriptTag(script.toJsCmd) +
          """|
             |// ]]>
             |""".stripMargin
        )
      }</script>)
    
    // TODO: fix for IE
    // private def fixEndScriptTag(in: String): String =
    //   if (S.ieMode) """\<\/script\>""".r.replaceAllIn(in, """<\\/script>""")
    //   else in
    private def fixEndScriptTag(in: String): String = in
  }


  def JsHideId(what: String): JsCmd = JQueryArtifacts.hide(what).cmd

  def JsShowId(what: String): JsCmd = JQueryArtifacts.show(what).cmd

  /**
    * Replaces the node having the provided id with the markup given by node
    *
    * @param id - the id of the node that will be replaced
    * @param node - the new node
    */
  case class Replace(id: String, content: NodeSeq) extends JsCmd {
    // TODO: val toJsCmd = JQueryArtifacts.replace(id, Helpers.stripHead(content)).toJsCmd
    val toJsCmd = JQueryArtifacts.replace(id, content).toJsCmd
  }


  /**
    * Replaces the content of the node with the provided id with the markup given by content
    *
    * This is analogous to assigning a new value to a DOM object's innerHtml property in Javascript.
    *
    * @param id - the id of the node whose content will be replaced
    * @param content - the new content
    */
  case class SetHtml(uid: String, content: NodeSeq) extends JsCmd {
    // we want eager evaluation of the snippets so they get evaluated in context
    // val toJsCmd = JQueryArtifacts.setHtml(uid, Helpers.stripHead(content)).toJsCmd
    val toJsCmd = JQueryArtifacts.setHtml(uid, content).toJsCmd
  }

  // ++   /**
  // ++    * Makes the parameter the selected HTML element on load of the page
  // ++    *
  // ++    * @param in the element that should have focus
  // ++    *
  // ++    * @return the element and a script that will give the element focus
  // ++    */
  // ++   object FocusOnLoad {
  // ++     def apply(in: Elem): NodeSeq = {
  // ++       val (elem, id) = findOrAddId(in)
  // ++       elem ++ Script(LiftRules.jsArtifacts.onLoad(Run("if (document.getElementById(" + id.encJs + ")) {document.getElementById(" + id.encJs + ").focus();};")))
  // ++     }
  // ++   }

  /**
   * Sets the value of an element and sets the focus
   */
  case class SetValueAndFocus(id: String, value: String) extends JsCmd {
    def toJsCmd = "if (document.getElementById(" + id.encJs + ")) {document.getElementById(" + id.encJs + ").value = " +
            value.encJs +
            "; document.getElementById(" + id.encJs + ").focus();};"
  }

  /**
   * Sets the focus on the element denominated by the id
   */
  case class Focus(id: String) extends JsCmd {
    def toJsCmd = "if (document.getElementById(" + id.encJs + ")) {document.getElementById(" + id.encJs + ").focus();};"
  }


  /**
   * Creates a JavaScript function with a name, a parameters list and
   * a function body
   */
  object Function {
    def apply(name: String, params: List[String], body: JsCmd): JsCmd =
      new JsCmd {
        def toJsCmd = (
          "function " + name + 
            "(" + params.mkString(", ") + ") {" +
            body.toJsCmd + 
            "}"
        )
      }
  }

  /**
    * Execute the 'what' code when the page is ready for use
    */
  object OnLoad {
    def apply(what: JsCmd): JsCmd = JQueryArtifacts.onLoad(what)
  }

  /**
   * Sets the value to the element having the 'id' attribute with
   * the result of the 'right' expression
   */
  case class SetValById(id: String, right: JsExp) extends JsCmd {
    def toJsCmd = "if (document.getElementById(" + id.encJs + ")) {document.getElementById(" + id.encJs + ").value = " +
    right.toJsCmd + ";};"
  }

  /**
   * Assigns the value computed by the 'right' expression to the
   * 'left' expression.
   */
  case class SetExp(left: JsExp, right: JsExp) extends JsCmd {
    def toJsCmd = left.toJsCmd + " = " + right.toJsCmd + ";"
  }

  /**
   * Creates a JavaScript var named by 'name' and assigns it the
   * value of 'right' expression.
   */
  case class JsCrVar(name: String, right: JsExp) extends JsCmd {
    def toJsCmd = "var " + name + " = " + right.toJsCmd + ";"
  }

  /**
   * Assigns the value of 'right' to the members of the element
   * having this 'id', chained by 'then' sequences
   */
  case class SetElemById(id: String, right: JsExp, then: String*) extends JsCmd {
    def toJsCmd = "if (document.getElementById(" + id.encJs + ")) {document.getElementById(" + id.encJs + ")" + (
      if (then.isEmpty) "" else then.mkString(".", ".", "")
    ) + " = " + right.toJsCmd + ";};"
  }

  implicit def jsExpToJsCmd(in: JsExp) = in.cmd

  case class CmdPair(left: JsCmd, right: JsCmd) extends JsCmd {
    import scala.collection.mutable.ListBuffer;

    def toJsCmd: String = {
      val acc = new ListBuffer[JsCmd]()
      appendDo(acc, left :: right :: Nil)
      acc.map(_.toJsCmd).mkString("\n")
    }

    @scala.annotation.tailrec
    private def appendDo(acc: ListBuffer[JsCmd], cmds: List[JsCmd]) {
      cmds match {
        case Nil =>
        case CmdPair(l, r) :: rest => appendDo(acc, l :: r :: rest)
        case a :: rest => acc.append(a); appendDo(acc, rest)
      }
    }
  }

  trait HasTime {
    def time: Option[TimeSpan]

    def timeStr = time.map(_.millis.toString) getOrElse ""
  }

  case class After(time: TimeSpan, toDo: JsCmd) extends JsCmd {
    def toJsCmd = "setTimeout(function() {" + toDo.toJsCmd + "}, " + time.millis + ");"
  }

  case class Alert(text: String) extends JsCmd {
    def toJsCmd = "alert(" + text.encJs + ");"
  }

  case class Prompt(text: String, default: String = "") extends JsExp {
    def toJsCmd = "prompt(" + text.encJs + "," + default.encJs + ")"
  }

  case class Confirm(text: String, yes: JsCmd) extends JsCmd {
    def toJsCmd = "if (confirm(" + text.encJs + ")) {" + yes.toJsCmd + "}"
  }

  case class Run(text: String) extends JsCmd {
    def toJsCmd = text
  }

  case object _Noop extends JsCmd {
    def toJsCmd = ""
  }

  implicit def cmdToString(in: JsCmd): String = in.toJsCmd

  def Noop: JsCmd = _Noop

  case class JsTry(what: JsCmd, alert: Boolean) extends JsCmd {
    def toJsCmd = "try { " + what.toJsCmd + " } catch (e) {" + (if (alert) "alert(e);" else "") + "}"
  }

// ++   /**
// ++    * A companion object with a helpful alternative constructor
// ++    */
// ++   object RedirectTo {
// ++     /**
// ++      * Redirect to a page and execute the function
// ++      * when the page is loaded (only if the page is on the
// ++      * same server, not going to some other server on the internet)
// ++      */
// ++     def apply(where: String, func: () => Unit): RedirectTo =
// ++     S.session match {
// ++       case Some(liftSession) =>
// ++         new RedirectTo(liftSession.attachRedirectFunc(where, Some(func)))
// ++       case _ => new RedirectTo(where)
// ++     }
// ++   }
// ++ 
// ++   case class RedirectTo(where: String) extends JsCmd {
// ++     private val where2 = // issue 176
// ++     if (where.startsWith("/") &&
// ++         !LiftRules.excludePathFromContextPathRewriting.vend(where)) (S.contextPath + where) else where
// ++ 
// ++     def toJsCmd = "window.location = " + S.encodeURL(where2).encJs + ";"
// ++   }


  /**
   * Reload the current page
   */
  case object Reload extends JsCmd {
    def toJsCmd = "window.location.reload();"
  }


  /**
   * Update a Select with new Options
   */
  case class ReplaceOptions(select: String, opts: List[(String, String)], dflt: Option[String]) extends JsCmd {
    def toJsCmd = """var x=document.getElementById(""" + select.encJs + """);
    if (x) {
    while (x.length > 0) {x.remove(0);}
    var y = null;
    """ +
    opts.map {
      case (value, text) =>
        "y=document.createElement('option'); " +
        "y.text = " + text.encJs + "; " +
        "y.value = " + value.encJs + "; " +
        (if (Some(value) == dflt) "y.selected = true; " else "") +
        " try {x.add(y, null);} catch(e) {if (typeof(e) == 'object' && typeof(e.number) == 'number' && (e.number & 0xFFFF) == 5){ x.add(y,x.options.length); } } "
    }.mkString("\n")+"};"
  }

  case object JsIf {
    def apply(condition: JsExp, body: JsCmd): JsCmd = JE.JsRaw("if ( " + condition.toJsCmd + " ) { " + body.toJsCmd + " }")

    def apply(condition: JsExp, bodyTrue: JsCmd, bodyFalse: JsCmd): JsCmd =
    JE.JsRaw("if ( " + condition.toJsCmd + " ) { " + bodyTrue.toJsCmd + " } else { " + bodyFalse.toJsCmd + " }")

    def apply(condition: JsExp, body: JsExp): JsCmd = JE.JsRaw("if ( " + condition.toJsCmd + " ) { " + body.toJsCmd + " }")

    def apply(condition: JsExp, bodyTrue: JsExp, bodyFalse: JsExp): JsCmd =
    JE.JsRaw("if ( " + condition.toJsCmd + " ) { " + bodyTrue.toJsCmd + " } else { " + bodyFalse.toJsCmd + " }")
  }

  case class JsWhile(condition: JsExp, body: JsExp) extends JsCmd {
    def toJsCmd = "while ( " + condition.toJsCmd + " ) { " + body.toJsCmd + " }"
  }

  case class JsWith(reference: String, body: JsExp) extends JsCmd {
    def toJsCmd = "with ( " + reference + " ) { " + body.toJsCmd + " }"
  }

  case class JsDoWhile(body: JsExp, condition: JsExp) extends JsCmd {
    def toJsCmd = "do { " + body.toJsCmd + " } while ( " + condition.toJsCmd + " )"
  }

  case class JsFor(initialExp: JsExp, condition: JsExp, incrementExp: JsExp, body: JsExp) extends JsCmd {
    def toJsCmd = "for ( " + initialExp.toJsCmd + "; " +
    condition.toJsCmd + "; " +
    incrementExp.toJsCmd + " ) { " + body.toJsCmd + " }"
  }

  case class JsForIn(initialExp: JsExp, reference: String, body: JsCmd) extends JsCmd {
    def toJsCmd = "for ( " + initialExp.toJsCmd + " in " + reference + ") { " + body.toJsCmd + " }"
  }

  case object JsBreak extends JsCmd {
    def toJsCmd = "break"
  }

  case object JsContinue extends JsCmd {
    def toJsCmd = "continue"
  }

  object JsReturn {
    def apply(in: JsExp): JsCmd = new JsCmd {
      def toJsCmd = "return " + in.toJsCmd
    }

    def apply(): JsCmd = new JsCmd {
      def toJsCmd = "return "
    }
  }

}

/**
* A collection of defaults for JavaScript related stuff
*/
object JsRules {
  /**
    * The default duration for displaying FadeOut and FadeIn
    * messages.
    */
  //@deprecated
  @volatile var prefadeDuration: TimeSpan = 5 seconds

  /**
    * The default fade time for fading FadeOut and FadeIn
    * messages.
    */
  //@deprecated
  @volatile var fadeTime: TimeSpan = 1 second
}

trait ToJsCmd {
  def toJsCmd: String
}


trait JQueryArtifacts  {
  /**
   * Toggles between current JS object and the object denominated by id
   */
  def toggle(id: String) = JqId(id) ~> new JsMember {
    def toJsCmd = "toggle()"
  }

  /**
   * Hides the element denominated by id
   */
  def hide(id: String) = JqId(id) ~> new JsMember {
    def toJsCmd = "hide()"
  }

  /**
   * Shows the element denominated by this id
   */
  def show(id: String) = JqId(id) ~> new JsMember {
    def toJsCmd = "show()"
  }

  /**
   * Shows the element denominated by id and puts the focus on it
   */
  def showAndFocus(id: String) = JqId(id) ~> new JsMember {
    def toJsCmd = "show().each(function(i) {var t = this; setTimeout(function() { t.focus(); }, 200);})"
  }

  /**
   * Serializes a form denominated by the id. It returns a query string
   * containing the fields that are to be submitted
   */
  def serialize(id: String) = JqId(id) ~> new JsMember {
    def toJsCmd = "serialize()"
  }

  /**
   * Replaces the content of the node with the provided id with the markup given by content
   */
  def replace(id: String, content: NodeSeq): JsCmd = JqJsCmds.JqReplace(id, content)

  /**
   * Sets the inner HTML of the element denominated by the id
   */
  def setHtml(id: String, content: NodeSeq): JsCmd = JqJsCmds.JqSetHtml(id, content)

  /**
   * Sets the JavScript that will be executed when document is ready
   * for processing
   */
  def onLoad(cmd: JsCmd): JsCmd = JqJsCmds.JqOnLoad(cmd)

  /**
   * Fades out the element having the provided id, by waiting
   * for the given duration and fades out during fadeTime
   */
  def fadeOut(id: String, duration: TimeSpan, fadeTime: TimeSpan) = 
    FadeOut(id, duration, fadeTime)

// ++  /**
// ++   * Makes an Ajax request using lift's Ajax path and the request
// ++   * attributes described by data parameter
// ++   */
// ++  def ajax(data: AjaxInfo): String = {
// ++    "jQuery.ajax(" + toJson(data, S.contextPath,
// ++      prefix =>
// ++              JsRaw("liftAjax.addPageNameAndVersion(" + S.encodeURL(prefix + "/" + LiftRules.ajaxPath + "/").encJs + ", version)")) + ");"
// ++  }

// ++  /**
// ++   * Makes a Ajax comet request using lift's Comet path and the request
// ++   * attributes described by data parameter
// ++   */
// ++  def comet(data: AjaxInfo): String = {
// ++    "jQuery.ajax(" + toJson(data, LiftRules.cometServer(), LiftRules.calcCometPath) + ");"
// ++  }

  /**
   * Transforms a JSON object in to its string representation
   */
  def jsonStringify(in: JsExp): JsExp = new JsExp {
    def toJsCmd = "JSON.stringify(" + in.toJsCmd + ")"
  }

  /**
   * Converts a form denominated by formId into a JSON object
   */
  def formToJSON(formId: String): JsExp = new JsExp() {
    def toJsCmd = "lift$.formToJSON('" + formId + "')";
  }

  private def toJson(info: AjaxInfo, server: String, path: String => JsExp): String =
    (("url : " + path(server).toJsCmd) ::
            "data : " + info.data.toJsCmd ::
            ("type : " + info.action.encJs) ::
            ("dataType : " + info.dataType.encJs) ::
            "timeout : " + info.timeout ::
            "cache : " + info.cache :: Nil) ++
            info.successFunc.map("success : " + _).toList ++
            info.failFunc.map("error : " + _).toList mkString ("{ ", ", ", " }")
}


case object JQueryArtifacts extends JQueryArtifacts


/**
 * The companion module for AjaxInfo that provides
 * different construction schemes
 */
object AjaxInfo {
  def apply(data: JsExp, post: Boolean) =
    new AjaxInfo(data, if (post) "POST" else "GET", 1000, false, "script", None, None)

  def apply(data: JsExp,
            dataType: String,
            post: Boolean) =
    new AjaxInfo(data, if (post) "POST" else "GET", 1000, false, dataType, None, None)

  def apply(data: JsExp) =
    new AjaxInfo(data, "POST", 1000, false, "script", None, None)

  def apply(data: JsExp,
            dataType: String) =
    new AjaxInfo(data, "POST", 1000, false, dataType, None, None)

  def apply(data: JsExp,
            post: Boolean,
            timeout: Long,
            successFunc: String,
            failFunc: String) =
    new AjaxInfo(data,
      if (post) "POST" else "GET",
      timeout,
      false,
      "script",
      Some(successFunc),
      Some(failFunc))
}

/**
 * Represents the meta data of an Ajax request.
 */
case class AjaxInfo(data: JsExp, action: String, timeout: Long,
                    cache: Boolean, dataType: String,
                    successFunc: Option[String], failFunc: Option[String])

