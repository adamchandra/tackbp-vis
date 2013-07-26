package lib.core

import scalaz._, Scalaz._
import play.api._


case object UnknownException extends Exception

case class CombinedException(t1: Throwable, t2: Throwable) extends Exception

