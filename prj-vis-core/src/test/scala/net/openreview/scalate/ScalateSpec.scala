package net.openreview.scalate
package ajax

import org.specs2._
import org.specs2.mutable._

class ScalateSpec extends mutable.Specification with Tags {
  // import JE._
  // import JsCmds._
  // import JqJE._

  val nc = <b>some new content</b>

  "ajax support" should {
    "succeed" in {
      val cmds = List(
        "Replacers =======",
        JsCmds.Replace("elemId", nc).toJsCmd,
        (JqJE.JqId(JE.Str("objId")) ~> JqJE.JqReplace(<some-content/>)).cmd.toJsCmd,
        "JsCmds =======",
        JsCmds.SetValueAndFocus("some-id", "newValue") & JsCmds.Focus("some-id"),
        JsCmds.Replace("elemId", nc),
        "",
        "JqJE ==========",
        JqJE.Jq("#my-id") ~> JqJE.JqAppend(<new-content/>),
        (JqJE.JqId(JE.Str("objId")) ~> JqJE.JqReplace(<some-content/>)),
        (JqJE.JqId(JE.Str("objId")) ~> JqJE.JqReplace(<some-content/>)).cmd,
        (JqJE.JqId(JE.Str("objId")) ~> JqJE.JqReplace(<some-content/>)).cmd.toJsCmd,
        "",
        "JsonCall",
        JsonCall("funcId").exp(JE.ParentOf("elemId")),
        JsonCall("funcId")(command="something?"),
        JsonCall("funcId")(command="something?", params=JE.JsArray(JE.Num(42), JE.Stringify("arg2"))),
        // case class ParentOf(id: String) extends JsExp {


        JsonCall("funcId")("something?"),
        "==",
        JsCmds.SetHtml("some-id", (<p>text content</p>)),
        JsCmds.Focus("some-id")
      ).mkString("=======\n", "  \n\n", "\n==========")

      println(cmds)

      success
    } tag("none")
  } 
}

