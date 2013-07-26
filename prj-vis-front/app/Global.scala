import com.mongodb.casbah.MongoConnection
import edu.umass.cs.iesl.tackbp.vis.admin.TacKbpOps
import lib.template.ScalatePlaySupport
import nav.nav
import org.fusesource.scalate.TemplateException
import org.fusesource.scalate.util.ResourceNotFoundException
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api._
import play.api.mvc._
import play.api.Play.current
import play.api.UnexpectedException
import net.openreview.scalate.{ScalateGlobal, CustomTemplateEngine}

// configure with: /main/resources/logback.xml and/or /test/resources/logback-test.xml 
import com.weiglewilczek.slf4s.Logging

import lib._
import lib.core._
import lib.template._
import net.openreview.scalate.EngineLike
import net.openreview.scalate.EngineContainer

object Global extends GlobalSettings with ScalateGlobal  {
  import logger._
  import Results._


  val engineContainer = new EngineContainer with ScalatePlayConfig {
    override protected def createEngine(): EngineLike = {
      new PlayTemplateEngine(templateRootPaths, "mode-not-yet-set", mode)
    }

    def configurator: EngineLike => Unit = {e => 
      e.addViewRemappings(
        classOf[edu.umass.cs.iesl.tackbp.vis.admin.Document] -> "model.Document",
        classOf[edu.umass.cs.iesl.tackbp.vis.admin.Section] -> "model.Section",
        classOf[edu.umass.cs.iesl.tackbp.vis.admin.SearchResults] -> "model.SearchResults",
        classOf[edu.umass.cs.iesl.tackbp.vis.admin.Pager] -> "model.Pager"
      )
    }
  }

  override def beforeStart(app: Application) {
    super.beforeStart(app)
  }

  // Called Just before the action is used.
  override def doFilter(a: EssentialAction): EssentialAction = a

  override def onStop(app: Application) {
    super.onStop(app)
  }

  override def onStart(app: Application) {
    super.onStart(app)

    val mode = current.mode.toString.toLowerCase()

    for {
      mongoConf <- current.configuration.getConfig("mongodb")
      conf <- mongoConf.getConfig(mode)
      host <- conf.getString("host")
      db <- conf.getString("db")
      user <- conf.getString("user")
      pass <- conf.getString("pass")
    }  {
      TacKbpOps.setMongoContext(host, db, user, pass)
    }

    println(
      """|
         |Tackbp Visualizer (%s) mode
         |""".stripMargin.format(mode))
    
  }
 
  override def onRouteRequest(request: RequestHeader):Option[Handler] = {
    logger.info(request.uri)
    nav.onRouteRequest(request) orElse super.onRouteRequest(request)
  }



  override def onHandlerNotFound(request: RequestHeader) = {
    nav.onHandlerNotFound(request) 
  }


  /**
   * Called when an action has been found, but the request parsing has failed.
   *
   * The default is to send the framework default 400 page.
   *
   * @param request the HTTP request header
   * @return the result to send to the client
   */
  override def onBadRequest(request: RequestHeader, error: String): Result = {
    println("bad request")
    BadRequest(views.html.defaultpages.badRequest(request, error))
  }

}

