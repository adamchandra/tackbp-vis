package admin.scalate

import lib.template.ScalatePlaySupport
import play.api.mvc._
import net.openreview.scalate._

// import com.weiglewilczek.slf4s.Logging
// import lib.template.ScalatePlaySupport
// import net.openreview.scalate.ajax.JE.JsRaw
// import net.openreview.scalate.ajax.JsCmds
// import net.openreview.scalate.ajax.JsExp
// import net.openreview.scalate.ScalateLayout
// import net.openreview.util.json.JsonUtils
// import org.bson.types.ObjectId
// import edu.umass.cs.iesl.tackbp.vis.admin._

object ScalatePage extends Controller with ScalatePlaySupport {
  def index() = Action { implicit request =>
    // attach the .ajax handler setup
    // Ajax button/form handling is a standard setup, and should be easily included
//    ScalatePage().template.withScripts(
//      onLoad(
//        ".ajax"
//      )
//    )
    Ok(ScalatePage().template)
  }


  // on client:
  //   ajax call is queued,
  //   spinner is (optionally) init'd
  //   ajax call is executed
  //     dev mode has a tracer ajax call made that reports the params
  // on server
  //   ajax call received, processed, responded to
  //     dev mode records successful completion
  // on client
  //  ajax response is received
  //     dev mode records successful completion
  //  

//  
//   // = AjaxAction {
//   def toggleLayoutMode = Action { implicit request =>
//     // b.ajax(href="/scalate/toggle ..")
//     val m = EngineSettings(engine)
//     "#layout-mode-line".select.replaceInner(m.layout("mode-line")) &
//     "#layout-mode-line".select.refresh(m.layout("mode-line"))
//  
//   }
}



case class ScalatePage() {
}

case class Engine(
  engine: CustomTemplateEngine
) {

  def settings() = {
    // engine.reportConfig()
  }

}
