package net.openreview.util.annotation
import edu.umass.cs.iesl.tackbp.vis.admin.TextToken

sealed trait SpanT {
  def start: Int 
  def length: Int

  def contains(i:Int) = 
    start <= i && i < start+length

  def end = start+length

  def +(o:Int) //  = this.copy(start = start+o)
  def -(o:Int) // = this.copy(start = start-o)

}

trait LSpanT extends SpanT {
  def label:String
}


case class Span(
  start: Int, length: Int
) extends SpanT {
  def +(o:Int) = copy(start = start+o)
  def -(o:Int) = copy(start = start-o)
}

case class LSpan(
  start: Int, length: Int, label:String
) extends LSpanT {
  def +(o:Int) = copy(start = start+o)
  def -(o:Int) = copy(start = start-o)
}


trait AnnotatedText {

  def text: String

  def annotations: Map[String, Seq[SpanT]]

  def spans[T <: SpanT](spantype:String): Seq[T] = {
    val ss = annotations.get(spantype).getOrElse(sys.error("span seq of type "+spantype+" not found"))
    ss.map(_.asInstanceOf[T])
  }

  def indexedTextTokens: Seq[(LSpanT, Int)]

  def indexedTextTokensInSpan(s:SpanT): Seq[(LSpanT, Int)] =
    indexedTextTokens.drop(s.start).take(s.length)

  def tokensInSpan(s:SpanT): Seq[LSpanT] =
    indexedTextTokensInSpan(s).map(_._1)

}




// object splitspans {
//   sealed trait IOSpan[T] {
//     def ts: Seq[T]
//   }
//  
//   // case class ISpan(str:String) extends IOSpan
//   // case class OSpan(str:String) extends IOSpan
//   abstract class IOCSpan extends IOSpan[Char]
//   case class ICSpan(ts:Seq[Char]) extends IOCSpan
//   case class OCSpan(ts:Seq[Char]) extends IOCSpan
//  
//   abstract class IOSSpan extends IOSpan[Span]
//   case class ISSpan(ts:Seq[Span]) extends IOSSpan
//   case class OSSpan(ts:Seq[Span]) extends IOSSpan
//  
//   // Inside/outside split, with sorting input spans
//   def splitIntoIOSpans(str:String, spans:Seq[Span]): Seq[IOCSpan] = {
//     val (ios, stail, offset) = spans.foldLeft(
//       (List[IOCSpan](), str, 0)
//     ) {
//       case ((iospans, srest, offset), cspan) =>
//         val offspan = cspan - offset
//         println("((iospans, srest, offset, offspan), cspan)" + ((iospans, srest.take(4), offset, offspan), cspan))
//         (iospans ++
//           ((if (offspan.start>0) Some(OCSpan(srest.substring(0, offspan.start))) else None) ::
//             Some(ICSpan(srest.substring(offspan.start, offspan.end))) ::
//             List[Option[IOCSpan]]()
//           ).filter(_.isDefined).map(_.get),
//  
//           srest.substring(offspan.end),
//           offset+offspan.end)
//     }
//  
//     ios ++ List(OCSpan(stail))
//   }
//
//  // taken from Document class, not needed now but not ready to delete
//  def mapSentenceSpansToCharSpans(sspans:Seq[Span]):Seq[Span] = for {
//    sp <- sspans
//  } yield {
//    val (ctstart, ctend) = (tokens(sp.start), tokens(sp.end-1))
//    println("ctstart, ctend: "+(ctstart, ctend))
// 
//    Span(ctstart.textSpan.span.start, ctend.textSpan.span.end)
//  }
// }
