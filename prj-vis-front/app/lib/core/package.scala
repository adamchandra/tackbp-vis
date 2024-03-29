package lib

import play.api._

// import scalaz._, Scalaz._
package object core {

  def use[A <: Plugin](implicit app: Application, m: Manifest[A]): Option[A] = {
    app.plugin[A]
      //.getOrElse(throw new RuntimeException(m.erasure.toString+ " plugin should be available at this point"))
  }

  // type Error = Throwable
  // type Res[+A] = Error \/ A
  //  
  // implicit val ThrowableMonoid = new Monoid[Throwable] {
  //   def zero = UnknownException
  //   def append(f1: Throwable, f2: => Throwable) = CombinedException(f1, f2)

  // }
}
