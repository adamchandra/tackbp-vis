package net.openreview.util.svg


trait SVGRendering {
  import org.json4s._
  import org.json4s.JsonDSL._

  // rendering possibilities:
  //   jade templating
  //   string generation
  object stringInterp {

    """|
       |rect(x=$)
       |"""
    //   2.10 string interp
    // def rect(atts: String*)(chs: String*) = """<rect x=$x y=$y width=$w height=$h />"""
    def elem(n:String)(atts: String*)(chs: String*): String = {
      val as = atts.mkString(" ")
      val ch = chs.mkString(" ")
      """<$n $as>$ch</$n>"""
    }

    //     <symbol id="MySymbol" viewBox="0 0 20 20">
    def attr[T](n:String)(t:T) = """$n=\\$t""" // TODO escaping for attribs

    def symbol = elem("symbol") _
    def rect = elem("rect") _

    def id(v:String) = attr("id")(v)
    def x(v:Int) = attr("x")(v)
    def y(v:Int) = attr("y")(v)
    def w(v:Int) = attr("w")(v)
    def viewBox(zz:Int*) = attr("viewBox")(zz)

    symbol(id("MySymbol"), viewBox(0, 0, 20, 20))(
      rect(x(2),y(3),w(4))(),
      rect(x(2),y(3),w(4))()
    )
  }

  object xmlLit extends scalaz.syntax.ToIdOps{
    import scala.xml._

    def attr(k:String)(v:String): Attribute = {
      new scala.xml.UnprefixedAttribute(k, v, Null)
    }

    def symbol: Node = <symbol />
    def rect: Node = <rect />

    // def symbol: Node => Node = appendChild(<symbol />) _
    //  def rect: Node => Node = appendChild(<rect />) _

    //def appendChild(child:Node)(parent: Node): Node = {
    //  parent match {
    //    case Elem(prefix, label, meta, scope, childs @ _*) =>
    //      new Elem(prefix, label, meta, scope, (childs ++ child):_*)
    //  }
    //}

    def appendNodes(children:Node*)(parent: Node): Node = {
      parent match {
        case Elem(prefix, label, meta, scope, childs @ _*) =>
          new Elem(prefix, label, meta, scope, (childs ++ children):_*)
      }
    }


    def addAttr(k:String, v:String)(n: Node): Node = {
      n match {
        case Elem(prefix, label, meta, scope, childs @ _*) =>
          new Elem(prefix, label,
            meta match {
              case nxt@UnprefixedAttribute(key,value, metaNext) =>
                new scala.xml.UnprefixedAttribute(k, v, nxt)
              case nxt@PrefixedAttribute(namespace_prefix,key,value,metaNext) =>
                new scala.xml.UnprefixedAttribute(k, v, nxt)
              case Null =>
                new scala.xml.UnprefixedAttribute(k, v, Null)
            },
            scope,
            childs:_*
          )
      }
    }

    val width = (v:Int) => addAttr("width", v.toString) _
    val x = (v:Int) => addAttr("x", v.toString) _
    val y = (v:Int) => addAttr("y", v.toString) _

    // symbol
    // rect(3, 4)
    //<symbol id="MySymbol" viewBox="0 0 20 20">
    //  <desc>MySymbol - four rectangles in a grid</desc>
    //  <rect x="1" y="1" width="8" height="8"/>
    //</symbol>

  }

  //   json def -> xml converter
  def rectJson(x:Int, y:Int) = ("rect" ->
    ("-x" -> x) ~
    ("-y" -> y) ~
    ("-width" -> 0) ~
    ("-height" -> 0)
  )

  val svg =
    ("svg" ->
      ("desc" -> "Example") ~
      ("defs" ->
        ("symbol" ->
          ("id" -> "MySymbol")
        )))


  // <?xml version="1.0" standalone="no"?>
  // <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
  // "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">

  // <svg width="10cm" height="3cm" viewBox="0 0 100 30" version="1.1"
  //      xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
  //   <desc>Example Use02 - 'use' on a 'symbol'</desc>
  //   <defs>
  //     <symbol id="MySymbol" viewBox="0 0 20 20">
  //       <desc>MySymbol - four rectangles in a grid</desc>
  //       <rect x="1" y="1" width="8" height="8"/>
  //       <rect x="11" y="1" width="8" height="8"/>
  //       <rect x="1" y="11" width="8" height="8"/>
  //       <rect x="11" y="11" width="8" height="8"/>
  //     </symbol>
  //   </defs>
  //   <rect x=".1" y=".1" width="99.8" height="29.8"
  //         fill="none" stroke="blue" stroke-width=".2" />
  //   <use x="45" y="10" width="10" height="10"
  //        xlink:href="#MySymbol" />
  // </svg>

}


trait BoxesSVGRendering {
  // import java.awt.Rectangle;
  // import java.awt.Graphics2D;
  // import java.awt.Color;
  import java.io.Writer;
  import java.io.OutputStreamWriter;
  import java.io.IOException;

  def syntax() {


  }


  import _root_.net.openreview.util.boxes.{Boxes => B}


  // Render a box as a list of lines.
  def renderBoxToSvg(box: B.Box) {
    val out = box match {
      case B.Box(r, c, B.Blank)                   => // resizeBox(r, c, List(""))
      case B.Box(r, c, B.Text(t))                 => // resizeBox(r, c, List(t))
      case B.Box(r, c, B.Col(bs))                 => // (bs >>= renderBoxWithCols(c)) |> (resizeBox(r, c, _))
      case B.Box(r, c, B.SubBox(ha, va, b))       => // resizeBoxAligned(r, c, ha, va)(renderBox(b))
      case B.Box(r, c, B.Row(bs))                 => 
      case B.Box(r, c, B.AnnotatedBox(props, b))  => 
    }

    out
  }
  
}


