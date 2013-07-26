package net.openreview.scalate

import java.io.{PrintWriter, StringWriter}
import org.fusesource.scalate._
import org.fusesource.scalate.layout._

case class ScalateLayout(uri: String)

class CustomLayoutStrategy(val engine: EngineLike, val defaultLayouts: String*) extends LayoutStrategy {
  import DefaultLayoutStrategy._

  def layout(template: Template, context: RenderContext) {

    def isLayoutDisabled(layout: String) = layout.trim.isEmpty

    // lets capture the body to be used for the layout
    val body = context.capture(template)

    // lets try find the default layout
    context.attributes.get("layout") match {
      case Some(layout: String) =>
        if (isLayoutDisabled(layout))
          noLayout(body, context)
        else
        if (!tryLayout(layout, body, context)) {
          debug("Could not load layout resource: %s", layout)
          noLayout(body, context)
        }

      case _ =>
        val layoutName = defaultLayouts.find(tryLayout(_, body, context))
        if (layoutName.isEmpty) {
          debug("Could not load any of the default layout resource: %s", defaultLayouts)
          noLayout(body, context)
        }
    }
  }

  private def tryLayout(layoutTemplate: String, body: String, context: RenderContext): Boolean = {
    def removeLayout() = {
      context.attributes("scalateLayouts") = context.attributeOrElse[List[String]]("scalateLayouts", List()).filterNot(_ == layoutTemplate)
    }

    try {
      debug("Attempting to load layout: %s", layoutTemplate)

      context.attributes("scalateLayouts") = layoutTemplate :: context.attributeOrElse[List[String]]("scalateLayouts", List())
      context.attributes("body") = body
      engine.load(layoutTemplate).render(context)

      debug("layout completed of: %s", layoutTemplate)
      true
    } catch {
      case e: ResourceNotFoundException =>
        removeLayout()
        false
      case e: Exception =>
        removeLayout()
        error(e, "Unhandled: %s", e)
        throw e
    }
  }

  /* Returns Option so it can be used in a for comprehension. */
  private def noLayout(body: String, context: RenderContext) = {
    context << body
    None
  }
}

