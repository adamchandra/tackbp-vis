package net.openreview.util
package json

trait JsonUtils {
  import org.json4s._
  import org.json4s.native.JsonMethods._
  import org.json4s.native.JsonMethods

  import play.api.libs.json.{JsValue, Json}
  def playJsonToLiftJson(jsval: JsValue): JValue = {
    JsonMethods.parse(
      Json.stringify(jsval),
      useBigDecimalForDouble = false)
  }

  def prettyPrintJson(jsval: JsValue): String = {
    org.json4s.native.Printer.pretty(
      render(playJsonToLiftJson(jsval))
    )
  }

}

object JsonUtils extends JsonUtils
