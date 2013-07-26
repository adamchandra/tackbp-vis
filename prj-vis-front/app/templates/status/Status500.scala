package status

import scala.xml._

trait Status

case class Status500(
  val message:Node
) extends Status {
}
