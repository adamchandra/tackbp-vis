package controllers

import com.weiglewilczek.slf4s.Logging
import lib.template.ScalatePlaySupport
import net.openreview.scalate.ajax.JE.JsRaw
import net.openreview.scalate.ajax.JsCmds
import net.openreview.scalate.ajax.JsExp
import net.openreview.scalate.ScalateLayout
import net.openreview.util.json.JsonUtils
import org.bson.types.ObjectId
import play.api.mvc._
import edu.umass.cs.iesl.tackbp.vis.admin._



object DocumentController extends Controller with ScalatePlaySupport  {
  import TacKbpOps._


  def index(docId:String) = Action { implicit request => 
    findDocumentById(docId).fold(
      err => Ok(
        status.Status500(
          <ul>{ err.list.map(i => <li>{i}</li>)}</ul>
        ).template
      ),
      succ={doc =>
        Ok(doc.template)
      }
    )
  }
}

