package net.openreview.scalate

import org.fusesource.scalate.TemplateException
import org.fusesource.scalate.util.ResourceNotFoundException
import play.api._
import play.api.mvc._
import play.api.Play.current
import play.api.UnexpectedException

import com.weiglewilczek.slf4s.Logging

trait ScalateGlobal extends GlobalSettings with Logging  {
  import Results._

  def engineContainer(): EngineContainer

  override def beforeStart(app: Application) {
    super.beforeStart(app)

  }

  override def onStart(app: Application) {
    super.onStart(app)
    engineContainer().restart()

    engineContainer.mode match {
      case PrecompileMode =>
        engineContainer.engine.reportConfig()
        engineContainer().engine.precompileAll()
        engineContainer().engine.createTemplateJar(file("scalate-templates.jar"))
        sys.exit(0)
      case ProductionMode =>
        engineContainer.engine.reportConfig()
      case _ =>
        engineContainer.cleanWorkingDirectory()
    }
  }

  /**
   * Called when an exception occurred.
   *
   * The default is to send the framework default error page.
   *
   * @param request The HTTP request header
   * @param ex The exception
   * @return The result to send to the client
   */
  override def onError(request: RequestHeader, throwable: Throwable): Result = {

    import net.openreview.scalate.{ConsoleHelper, TemplateRuntimeException, ScalateLayout}
    import scala.xml.NodeSeq
    implicit val rh = request
    import ScalateResponseEncodings._


    def renderStackTrace(e: Throwable): NodeSeq = {
      val chelper = new ConsoleHelper(engineContainer.engine)
      <ul>{
        (for (ste <- e.getStackTrace().toList) yield {
          chelper.renderStackTraceElement(ste)
        })
      }</ul>
    }

    def _stacktracePage(throwable: Throwable): Result = {
      val (causes, lastcause) = (0 to 4).foldLeft(
        List[String](), throwable
      ) { case ((results, t), depth) =>
          if (t != null) {
          (("""|
               |<h4>%s: %s</h4> 
               | %s
               |"""
            .stripMargin
            .format(t.getClass.getName, t.getMessage(), renderStackTrace(t)) 
            :: results
          ) ->
            t.getCause)
          } else results -> null
      }

      try {
        InternalServerError(
          ViewConfig("/stacktrace.jade").withLayout(
            "/layout-err.jade"
          ).withAttribs(
            "message" -> causes.mkString("\n<br/>")
          )
        ).as("text/html")
      } catch {
        case e: Throwable => {
          Logger.error("Error while rendering default error page", e)
          InternalServerError
        }
      }
    }
    _stacktracePage(throwable)
  }
}
