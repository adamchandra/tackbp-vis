package net.openreview.util
package svg

import org.specs2._
import org.specs2.mutable._

class SvgRenderingSpec extends mutable.Specification with Tags {

  object svgRendering extends SVGRendering with scalaz.syntax.ToIdOps
  import scala.xml._
  // import scalaz.syntax.id._
  // import scalaz.std.function._

  "rendering" should {
    "use xml literals" in {

      import svgRendering.xmlLit._

      val sdf =
        (symbol |> x(3) |> y(3) |> width(3)
          |> appendNodes(
            rect |> x(2) |> y(4),
            rect |> x(2) |> y(4)
          )
        )

      println(sdf)

      success
    }


  }

  "document to svg" should {
    import svgRendering.xmlLit._
    // replace brat:
    // create a few doc samples
    //   create class serializer (salat like)
    // turn docs into svg
    //   create svg writer to visualize development in chrome
    // use console to develop

    import edu.umass.cs.iesl.tackbp.vis.admin.TacKbpOps
    import TacKbpOps._

    // TODO put sample data in a real spot
    val doc = jsonToDocument("cd1dc1ba-6e9b-41a4-9695-18e0276c6250")

    "generate token svg" in {
      //<g class="text">
      //  <text x="0" y="0">
      //    <tspan x="25" y="0" data-chunk-id="0">China</tspan>
      //    <tspan x="84" y="0" data-chunk-id="1">look</tspan>
      //    <tspan x="157.5" y="0" data-chunk-id="2">set</tspan>
      //    <tspan x="189.5" y="0" data-chunk-id="3">to</tspan>
      //  </text>
      //</g>

      val section0 = doc.sections(0)
      for {
        ttok <- section0.textTokens
      } yield {
        <tspan/>
      }
      todo
    }


    "gen pos svg" in { todo }
    "gen mention nav tree svg" in { todo }
    "gen relation arc svg" in { todo }

  }

}
