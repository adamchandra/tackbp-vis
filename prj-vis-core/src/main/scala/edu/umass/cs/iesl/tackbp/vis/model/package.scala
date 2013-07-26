package edu.umass.cs.iesl.tackbp

package object vis {

  import com.mongodb.casbah.Imports._

  import scalaz.{Validation, Success, Failure}
  import scalaz.Validation._
  import scalaz.syntax.validation._

  implicit def mongoWriteResultToValidation(wr: WriteResult): Validation[String, Unit] = {
    val err = wr.getLastError()
    if (err.ok) ().success
    else err.getErrorMessage().failure
  }

}
