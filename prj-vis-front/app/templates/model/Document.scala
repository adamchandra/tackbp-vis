package model

import edu.umass.cs.iesl.tackbp.vis.admin.{Section, Mention}
import net.openreview.scalate.ajax._
import net.openreview.scalate.ajax.JsCmds._
import net.openreview.scalate.ajax.JE._
import net.openreview.scalate.ajax.JsExp._
import net.openreview.util.annotation.{Span, LSpan, SpanT}
import java.util.Random



class SectionMockup(val num:Int, sectionObj: JsObj)  {
  def nerData(): JsArray = JsArray()
  def depData(): JsObj = sectionObj
}

class DocumentMockup  {
  import mockupData._

  val (navArray, sectionObjs) = genDocument(6)

  val sections: Seq[SectionMockup] = sectionObjs.zipWithIndex.map {
    case (obj, i) => new SectionMockup(i, obj)
  }

  /* [doc-order#, ent#, ent-canonical#, section#, token-index#, token-length, mention-text] */
  def mentionJsArray: JsArray = navArray

  def depData(): JsArray = JsArray(sectionObjs:_*)

  def collData(): JsObj = {
    JsObj(
      "entity_types" -> JsArray(
        JsObj(
          "type" -> "SPAN_DEFAULT",
          "labels" -> JsArray(),
          "color" -> "red",
          "fgColor" -> "black",
          "bgColor" -> "white"
        ) 
      ),
      "relation_types" -> JsArray(
        JsObj(
          "type" -> "typeA",
          "labels" -> JsArray("anA"),
          "dashArray" -> "6-6",
          "color" -> "blue",
          "args" -> JsArray(
            JsObj("role" -> "From"),
            JsObj("role" -> "To")
          )
        )

      ),
      "entity_attribute_types" -> JsArray(
        JsObj(
          "type" -> "root", 
          "values" -> JsObj(
            "root" -> JsObj(
              "glyph" -> "R"
            )
          )
        ),
        JsObj(
          "type" -> "sstart", 
          "values" -> JsObj(
            "sstart" -> JsObj(
              "glyph" -> "S"
            )
          )
        ),
        JsObj(
          "type" -> "token", 
          "values" -> JsObj(
            "token" -> JsObj(
              "glyph" -> "_"
            )
          )
        )
      ),
      "visual_options" -> JsObj(
        "arc_bundle" -> "all"
      )
    )
  }


}

object mockupData {
  val rand = new Random(System.currentTimeMillis())
  def randInt = (i:Int) => rand.nextInt(i)
  def takeRandLen[T](s:Seq[T]): Seq[T] = s take randInt(s.length min 5)


  def genDocument(nOfSections: Int): (JsArray, Seq[JsObj]) = {
    var nOfDocTokens = 0

    val sectionData: Seq[(Seq[JsArray], JsObj)] = for (section <- 0 until nOfSections) yield {
      val tokenSpans = genTokenSpans()                  // [(0, 3, "the"), ...]
        //docTokens = tokenSpans :: docTokens
      val text = tokenSpans.map(_.label).mkString(" ")  // "the sjkl.."

      //  | "entities": [  ["T$i", "$pos", [[$start, $len]]], ...,  ["T2", "VBD", [[12, 15]]] ...
      val posEntities = genPos(tokenSpans)                      // "NNP VRB"

      //  | "relations": [   ["R0", "nn", [["From", "T0"], ["To", "T1"]] ], ...
      val relationData = JsArray((for {
        rnum <- 0 to (tokenSpans.length / 2)
      } yield JsArray(
        "R"+rnum, "typeA",
        JsArray(
          JsArray("From", "T"+randInt(tokenSpans.length)),
          JsArray("To",   "T"+randInt(tokenSpans.length))))
      ):_*)

      // this is the array used to populate the mention nav sidebar
      val navData = (for {
        (mentionCandidate, tokenIndex) <- tokenSpans.tails.filterNot(_.isEmpty).zipWithIndex
        mention = takeRandLen(mentionCandidate)
        if !mention.isEmpty
        // mention <- mentionTokens
      } yield JsArray(
        nOfDocTokens+tokenIndex,                 // doc-index
        rand.nextInt(4),                         // entity num // TODO
        rand.nextInt(3),                         // canonical num TODO
        "NOM",                                   // mention type TODO
        section,                                 // section #
        tokenIndex, mention.length,        // token span
        mention.map(_.label).mkString(" ") // text
      )).toList

      nOfDocTokens += tokenSpans.length
      (navData, JsObj(
        "text" -> text,
        "entities" -> posEntities,
        "relations" -> relationData
      ))
    }

    val navArray = JsArray(sectionData.map(_._1).reduce(_ ++ _):_*)
    val sectionObjs = sectionData.map(_._2)

    (navArray, sectionObjs)
  }


  // given :
  // China look set to dominate track cycling at the Asian Games when it gets underway on Saturday , with Olympic women sprint bronze medallist Guo Shuang tipped to go for a world record .
  // 
  // generate sample data
  def genTokens(): List[String] = {
    "China look set to dominate track cycling at the Asian Games when it gets underway on Saturday , with Olympic women sprint bronze medallist Guo Shuang tipped to go for a world record .".split(" ").toList
  }

  def genTokenSpans(): List[LSpan] = {
    genTokens().foldLeft(
      List[LSpan]()
    ) { case (acc, e) =>
        val tailOffset = if (acc.isEmpty) 0 else acc.last.end+1
        acc ++ List(LSpan(tailOffset, e.length, e))
    }
  }

  val poss = "NNP NN VBD".split(" ")


  def genPos(tokenSpans: Seq[SpanT]): JsArray = {
    JsArray(tokenSpans.zipWithIndex.map {
      case (span, i) =>
        JsArray(
          ("T"+i),
          poss(rand.nextInt(poss.length)),
          JsArray(JsArray(span.start, span.end)))
    }:_*)
  }


  val mentionData =
    """|
       | var mentions = [
       |   [0, 6, 0, "NOM", 0, 0, 2, "China look"]
       | , [1, 11, 0, "NOM", 0, 19, 5, "Olympic women \u0027s sprint bronze"]
       | , [2, 2, 0, "NOM", 0, 31, 3, "a world record"]
       | ]
       |"""

  val collData =
    """|
       |collData = {
       |  "entity_types": [{"type": "SPAN_DEFAULT", "labels": [], "color": "red", "fgColor": "black", "bgColor": "white"}],
       |  "relation_types": [
       |    {"type": "infmod", "labels": ["infmod"], "dashArray": "6-6", "color": "blue", "args": [{"role": "From"}, {"role": "To"}]},
       |    {"type": "intj", "labels": ["intj"], "dashArray": "6-6", "color": "blue", "args": [{"role": "From"}, {"role": "To"}]},
       |    {"type": "conj", "labels": ["conj"] , "dashArray": "6-6", "color": "blue", "args": [{"role": "From"}, {"role": "To"}]}, 
       |  ],
       |  "entity_attribute_types": [
       |    {"type": "root", "values": {"root": {"glyph": "R"}}}, {"type": "sstart", "values": {"sstart": {"glyph": "S"}}}, {"type": "token", "values": {"token": {"glyph": "_"}}}
       |  ],
       |  "visual_options": {"arc_bundle": "all"}
       |};
       |"""


  val depData =
    """|
       |Data = [
       | 
       | "text": "000aChina look set to dominate track cycling at the Asian Games when it gets underway on Saturday , with Olympic women \u0027s sprint bronze medallist Guo Shuang tipped to go for a world record .",
       | "entities": [
       |   ["T0", "NNP", [[1, 6]]],
       |   ["T1", "NN", [[7, 11]]],
       |   ["T2", "VBD", [[12, 15]]]
       | ],
       | "relations": [
       |   ["R0", "nn", [["From", "T0"], ["To", "T1"]] ],
       |   ["R1", "nsubj", [["From", "T1"], ["To", "T2"]]],
       |   ["R2", "advcl", [["From", "T2"], ["To", "T24"]]],
       | ]
       |""" 
   


}




