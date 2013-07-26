package lib.template
import net.openreview.scalate._

import play.api.mvc._

trait PlayScalateTemplateImplicits {

  implicit def stringToConfigOps(uri:String)(
    implicit eng:CustomTemplateEngine, request: RequestHeader, layout:ScalateLayout
  ) = new {
    def template: ViewConfig = 
      ViewConfig(uri)
        .withEngine(eng)
        .withRequestHeader(request)
        .withLayout(layout.uri)
  }

  implicit def anyRefToConfigOps(model:AnyRef)(
    implicit eng:CustomTemplateEngine, request: RequestHeader, layout:ScalateLayout
  ) = new {
    def template: ModelConfig = 
      ModelConfig(model)
        .withEngine(eng)
        .withRequestHeader(request)
        .withLayout(layout.uri)
  }

}

trait ScalatePredefs extends ScalateResponseEncodings with PlayScalateTemplateImplicits
