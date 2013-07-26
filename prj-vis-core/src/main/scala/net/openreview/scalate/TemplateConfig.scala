package net.openreview.scalate


import java.io.File
import net.openreview.scalate.ajax.JE.JsRaw
import net.openreview.scalate.ajax.JsCmd
import net.openreview.scalate.ajax.JsCmds
import net.openreview.scalate.ajax.JsCmds._
import net.openreview.scalate.ajax.JsCommands
import net.openreview.scalate.ajax.JsExp
import net.openreview.util.json.JsonUtils
import org.fusesource.scalate._
import org.fusesource.scalate.support.AttributesHashMap
import play.api._
import play.api.mvc._
import play.api.http.Status._
import play.api.http.{ContentTypeOf, ContentTypes, Writeable}
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import java.io.{PrintWriter, StringWriter}
import net.openreview.scalate._

import BindingHelpers._

case class TemplateCommonConfig(
  layoutUri:Option[String] = None,
  attrs: List[(String, Any)] = List(),
  script: Option[JsCmd] = None,
  scalateEngine: Option[CustomTemplateEngine] = None,
  request: Option[RequestHeader] = None
) {

  def viewHtml() = this

  def requestAttributes(rh: RequestHeader): List[(String, Any)] = {
    List(
      "request" -> rh,
      "session" -> rh.session,
      "flash" -> rh.flash
    ) ++ attrs
  }

  def withLayout(s:String) = this.copy(layoutUri=Some(s))
  def withAttribs(as: (String, Any)*) =  this.copy(attrs = as.toList)
  def withScript(s: JsCmd) =  this.copy(script = Some(s))       
  def withEngine(e: CustomTemplateEngine) = this.copy(scalateEngine = Some(e))
  def withRequestHeader(r: RequestHeader) = this.copy(request = Some(r))

  def attributes(): List[(String, Any)] = {
    ("headScripts" -> script.getOrElse(Noop).toJsCmd
      :: "layout" -> layoutUri.getOrElse("")
      :: request.map(requestAttributes(_)).getOrElse(Nil))
  }
  def engine = scalateEngine.getOrElse(sys.error("no configured template engine"))
}


case class ModelConfig(
  it:AnyRef,
  view:String = "index",
  conf:TemplateCommonConfig = TemplateCommonConfig()
) {

  def withLayout(s:String) = this.copy(conf = conf.withLayout(s))
  def withAttribs(as: (String, Any)*) = this.copy(conf = conf.withAttribs(as:_*))
  def withScript(s:JsCmd) = this.copy(conf = conf.withScript(s))
  def withEngine(e: CustomTemplateEngine) = this.copy(conf = conf.withEngine(e))
  def withRequestHeader(r: RequestHeader) = this.copy(conf = conf.withRequestHeader(r))

  def withView(s:String) = this.copy(view = s)

  import conf._

  def render:String = {
    engine.model(it, view, attributes:_*)
  }

}


case class ViewConfig( 
  viewUri:String,
  conf:TemplateCommonConfig = TemplateCommonConfig()
) {
  def withLayout(s:String) = this.copy(conf = conf.withLayout(s))
  def withAttribs(as: (String, Any)*) = this.copy(conf = conf.withAttribs(as:_*))
  def withScript(s:JsCmd) = this.copy(conf = conf.withScript(s))
  def withEngine(e: CustomTemplateEngine) = this.copy(conf = conf.withEngine(e))
  def withRequestHeader(r: RequestHeader) = this.copy(conf = conf.withRequestHeader(r))

  import conf._

  def viewHtml() = this

  def render:String = {
    engine.layout(viewUri, attributes.toMap)
  }

}

case class StringAsHtml(val cont: String)

object ScalateResponseEncodings extends ScalateResponseEncodings

trait ScalateResponseEncodings {

  implicit def writeableOf_ViewConfig(implicit codec: Codec): Writeable[ViewConfig] = 
    Writeable[ViewConfig]{vconf => codec.encode(vconf.render)}

  implicit def contentTypeOf_ViewConfig(implicit codec: Codec): ContentTypeOf[ViewConfig] =
    ContentTypeOf[ViewConfig](Some(ContentTypes.HTML))


  implicit def writeableOf_ModelConfig(implicit codec: Codec): Writeable[ModelConfig] = 
    Writeable[ModelConfig]{conf => codec.encode(conf.render)}

  implicit def contentTypeOf_ModelConfig(implicit codec: Codec): ContentTypeOf[ModelConfig] =
    ContentTypeOf[ModelConfig](Some(ContentTypes.HTML))


  implicit def writeableOf_ScalateContent(implicit codec: Codec): Writeable[StringAsHtml] = 
    Writeable[StringAsHtml](scalate => codec.encode(scalate.cont))

  implicit def contentTypeOf_ScalateContent(implicit codec: Codec): ContentTypeOf[StringAsHtml] =
    ContentTypeOf[StringAsHtml](Some(ContentTypes.HTML))

}
