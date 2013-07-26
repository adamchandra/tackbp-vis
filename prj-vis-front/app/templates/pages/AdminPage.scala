package pages

import lib.template.ScalatePlaySupport
import play.api.mvc._

// import com.weiglewilczek.slf4s.Logging
// import lib.template.ScalatePlaySupport
// import net.openreview.scalate.ajax.JE.JsRaw
// import net.openreview.scalate.ajax.JsCmds
// import net.openreview.scalate.ajax.JsExp
// import net.openreview.scalate.ScalateLayout
// import net.openreview.util.json.JsonUtils
// import org.bson.types.ObjectId
// import edu.umass.cs.iesl.tackbp.vis.admin._

object AdminPage extends Controller with ScalatePlaySupport {
  def index() = Action { implicit request =>
    Ok(AdminPage().template)
  }

}

case class AdminPage() {
}
