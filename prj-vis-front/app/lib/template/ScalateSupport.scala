package lib.template

import org.fusesource.scalate._
import org.fusesource.scalate.support.AttributesHashMap
import java.io.{PrintWriter, StringWriter}
import net.openreview.scalate._

import BindingHelpers._

class PlayRenderContext(
  private val _uri: String,
  override val engine: PlayTemplateEngine,
  override val printWriter: PrintWriter,
  override val stringWriter: Option[StringWriter] = None 
) extends CustomRenderContext(
  _uri, engine, printWriter, stringWriter
) {
  override val attributes: AttributeMap = new AttributesHashMap() {
    update("context", PlayRenderContext.this)
  }
}

class PlayTemplateEngine(
  srcdirs: Traversable[JFile] = None,
  mode: String,
  scalateMode: ScalateMode
) extends CustomTemplateEngine(srcdirs, mode, scalateMode) {

  override protected def createRenderContext(uri: String, out: PrintWriter): RenderContext = {
    logger.debug("Creating PlayRenderContext for uri "+uri)
    new PlayRenderContext(uri, this, out)
  } 
}


trait ScalatePlaySupport extends ScalatePredefs { 
  import play.api.Play.current
  implicit val defaultLayout = ScalateLayout("/layout.jade")
  implicit lazy val templateEngine:PlayTemplateEngine = {
    val g = current.global.asInstanceOf[ScalateGlobal]
    g.engineContainer.engine.asInstanceOf[PlayTemplateEngine]
  }

}
