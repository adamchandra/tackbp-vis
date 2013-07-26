package net.openreview.util.annotation

import org.specs2._
import org.specs2.mutable._

class AnnotationSpec extends mutable.Specification with Tags {

  "spans" should {
    "have labeled/unlabeled varieties" in {
      (List[SpanT](
        Span(0, 3),
        LSpan(3, 5, "x")
      ) map { s => 
        s match {
          case Span(s, l) =>
            "span"
          case LSpan(s, l, t) =>
            "span:"+t
        }
      } mkString (",")) must_== "span,span:x"
    } tag("labels")
  }

}

