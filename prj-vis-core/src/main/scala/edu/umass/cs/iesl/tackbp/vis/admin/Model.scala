package edu.umass.cs.iesl.tackbp.vis.admin

import com.mongodb.casbah.Imports._
import play.api.libs.json.{JsValue, JsString}

import play.api.libs.json.Json._
import net.openreview.util.annotation._
import scala.xml._

import net.openreview.scalate.ajax._
import net.openreview.scalate.ajax.JsCmds._
import net.openreview.scalate.ajax.JE._
import net.openreview.scalate.ajax.JsExp._
import net.openreview.util.colors._
import scalaz.syntax.id._
import scalaz.std.function._



object nerParser {
  def apply(s:String): LSpan = {
    val Array(label, start, end) = s.split("\\|")
    LSpan(start.toInt, end.toInt, label)
  }
}

object Document {
  import scala.collection.JavaConverters._

  def zipIntsAndLabelsToSpans(starts: Seq[Int], lens: Seq[Int], labels: Seq[String]): Seq[LSpan] = {
    starts.zip(lens).zip(labels).map {
      case ((st, len), label) => LSpan(st, len, label)
    }
  }

  def zipIntsToSpans(starts: Seq[Int], lens: Seq[Int]): Seq[Span] = {
    starts.zip(lens).map(s => Span(s._1, s._2))
  }

  def fromBson(doc: DBObject, mentionDbos: Seq[DBObject], entityDbos: Seq[DBObject]): Document = {
    val id =       doc.as[String]      ("_id"          )
    val name =     doc.as[String]      ("name"         ) 
    val text =     doc.as[String]      ("text"         )
    val src =      doc.as[String]      ("src"          )

    val sectiondbos = doc.as[Seq[DBObject]]("sections")

    val sections = for {
      (section, sectionIndex) <- sectiondbos.sortBy(_.as[Int]("start")).zipWithIndex
    } yield {
      val start  =   section.as[Int]         ("start"      )
      val end =      section.as[Int]         ("end"      )
      val tstarts =  section.as[Seq[Int]]    ("tstarts"      )
      val tlengths = section.as[Seq[Int]]    ("tlengths"     )
      val sstarts =  section.as[Seq[Int]]    ("sstarts"      )
      val slengths = section.as[Seq[Int]]    ("slengths"     )
      val tstrings = section.as[Seq[String]] ("tstrings"     )
      val tlemma =   section.as[Seq[String]] ("tlemma"       )
      val nerspans = section.as[Seq[String]] ("nerspans"     )
      val tpparent = section.as[Seq[Int]]    ("tpparent"     )
      val tpos =     section.as[Seq[String]] ("tpos"         )
      val tpdeprel = section.as[Seq[String]] ("tpdeprel"     )

      val errMessages = List(
        "tstarts"  ->      tstarts,
        "tlengths" ->      tlengths,
        // "sstarts"  ->      sstarts ,
        // "slengths" ->      slengths,
        "tlemma"   ->      tlemma,
        // "nerspans" ->      nerspans,
        "tpparent" ->      tpparent,
        "tpos"     ->      tpos ,
        "tpdeprel" ->      tpdeprel
      ).foldLeft(
        List[String]()
      ) {
        case (msgs, (seqname, seq)) =>
          if (seq.length != tstrings.length)
            "error: "+seqname+".length differs from token seq length." :: msgs
          else msgs
      }


      val _textTokens = zipIntsAndLabelsToSpans(
        tstarts, tlengths, tstrings
      ).zipWithIndex.map {
        case (t, i) => TextToken(t, i)
      }

      val sentenceSpans = zipIntsToSpans(sstarts, slengths)

      def sentenceSpanForToken(tindex:Int): Span =
        sentenceSpans.dropWhile(!_.contains(tindex)).head

      def tokenIndexInSentence(tindex:Int): Int = {
        val sspan = sentenceSpans.dropWhile(!_.contains(tindex)).head
        tindex-sspan.start
      }

      val (finalText, tokenSpans) = _textTokens.zipWithIndex.foldLeft(
        // final-text, token-spans
        ("", Seq[LSpanT]())
      ) {
        case ((text, tspans), (tok, i)) =>
          val sep = if (tokenIndexInSentence(i) == 0) "\n" else " "
          (text+sep+tok.tspan.label,
            tspans ++ Seq(LSpan(text.length+sep.length, tok.tspan.label.length, tok.tspan.label)))
      }

      val indexedTextTokens: Seq[(LSpanT, Int)] = tokenSpans.zipWithIndex

      def indexedTextTokensInSpan(s:SpanT): Seq[(LSpanT, Int)] =
        indexedTextTokens.drop(s.start).take(s.length)

      def tokensInSpan(s:SpanT): Seq[LSpanT] =
        indexedTextTokensInSpan(s).map(_._1)

      def text = finalText
      def annotations = Map[String, Seq[SpanT]](
        "tokens" -> tokenSpans
      )

      // val indexedTextTokens = this.spans[LSpanT]("tokens").zipWithIndex

      val emap = (for {
        dbo <- entityDbos
      } yield {
        val _id  = dbo.as[String]("_id")
        val cm = dbo.as[String] ("canonicalMention")
        _id -> InDocEntity(_id, cm)
      }).toMap

      val mentions = for {
        (m, mentionIndex) <- mentionDbos.sortBy(_.as[Int]("start")).zipWithIndex
        if m.as[Int]("sectionStart") == start
      } yield {
        val _id              = m.as[String]    ("_id              ".trim)
        val docEntity        = m.getAs[String] ("docEntity        ".trim).getOrElse("no-doc-entity-id")
        val headTokenIdx     = m.as[Int]       ("headTokenIdx     ".trim)
        val tackbpEntityType = m.as[String]    ("TackbpEntityType ".trim)
        val mentionType      = m.as[String]    ("mentionType      ".trim)
        val docMentionType   = m.as[String]    ("docMentionType   ".trim)
        val sectionStart     = m.as[Int]       ("sectionStart     ".trim)
        val mstart            = m.as[Int]       ("start            ".trim)
        val mlength           = m.as[Int]       ("length           ".trim)

        val entityOpt = emap.get(docEntity)

        val isCanonical = entityOpt map (_id == _.canonicalMention) getOrElse false

        val tokenSpan = Span(mstart, mlength)
        val text = tokensInSpan(tokenSpan).map(_.label).mkString(" ")

        Mention(_id, 
          docEntity,
          entityOpt,
          isCanonical,
          tackbpEntityType,
          mentionType,
          docMentionType,
          tokenSpan,
          sectionIndex = sectionIndex, 
          text
        )
      }


      val depRelations = tpparent.zip(
        tpdeprel
      ).zipWithIndex.map{
        case ((parentIndex, label), i) =>
          // change negative parent indices to point to self index (for rendering)
          if (parentIndex>=0) DepRelation(parentIndex + sentenceSpanForToken(i).start, label)
          else                DepRelation(i, label)
      }

      Section(None,
        Span(start, end),
        tokenSpans,
        finalText,
        mentions,
        sentenceSpans,
        tpos.map(POS(_)),
        depRelations,
        nerspans.map(nerParser(_)),
        errMessages
      )
    }

    Document(id.toString(),
      name,
      text,
      sections
    )
  }

}

case class Document(
  val id: String,
  val name: String,
  val srctext: String, 
  _sections: Seq[Section]
) {
  val sections: Seq[Section] = _sections.map(_.copy(document=Some(this)))

  def combinedText: String = (for (
    section <- sections
  ) yield {
    section.text
  }).mkString(" ")

  def displayName(): NodeSeq = {
    import org.joda.time.LocalDate
    import org.joda.time.format.DateTimeFormat

    if (name.contains("LDC")) {
      val Date ="""[^\d]+(\d{4})(\d{2})(\d{2}).*""".r
      val Date(year, month, day) = name
      val ymd = new LocalDate(year.toInt, month.toInt, day.toInt)
      val fmt  = DateTimeFormat.forPattern("yyyy, MMM dd");
      <span class="docname timestamp">{fmt.print(ymd)} </span>
    } 
    else {
      <span class="docname">{ name } </span>
    }
  }

  def toIndexable(): JsValue = {
    val sep = """[-_\.]""".r
    // val nameParts = sep.split(name).map(JsString(_))
    val nameParts = sep.split(name).mkString(" ")
    obj(
      "textdoc" ->
        obj("text" -> (srctext + " " + nameParts))
    )
  }


  lazy val depRelations = {
    for (section <- sections; rel <- section.depRelations) yield rel 

  }

  lazy val relationTypes = depRelations.groupBy(_.label).keys.toSeq.map{
    label =>
    JsObj(
      "type" -> label,
      "labels" -> JsArray(label),
      "dashArray" -> "6-6",
      "color" -> "blue",
      "args" -> JsArray(
        JsObj("role" -> "From"),
        JsObj("role" -> "To")
      )
    )
  }

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
        //:: (entityIds.map{ case (id, (e, eId, i)) =>
        //  JsObj(
        //    "type" -> eId,
        //    "labels" -> JsArray(eId),
        //    "bgColor" -> rgb(248, 248, 0),
        //    "borderColor" -> "darken"
        //  )
        //}).toList:_*
      ),
      "relation_types" -> JsArray(
        relationTypes:_*
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

  def mentions: Seq[Mention] = for {
    s <- sections
    m <- s.mentions
  } yield m


  def unique(ss: Seq[String]): Seq[String] = {
    ss.groupBy(s => s).keySet.toSeq
  }


  /* [doc-order#, ent#, ent-canonical#, section#, token-index#, token-length, mention-text] */
  def mentionJsArray: JsArray = {
    val entityIds = (mentions.map(_.entityId) |> unique).zipWithIndex.map{ case(a, b) => (a, b+1) }.toMap
    JsArray(
      (for ((m, docIndex) <- mentions.zipWithIndex) yield {
        val entityId = m.entity.map(_.id).getOrElse("0")
        val entityNum = entityIds.get(entityId).getOrElse(0)
        JsArray(docIndex,
          entityNum,
          if (m.isCanonical) 0 else 1,
          m.mentionType,
          m.sectionIndex, 
          m.tokenSpan.start, m.tokenSpan.length,
          m.text
        )
      }):_*
    )
  }

  def depData(): JsArray = {
    JsArray((
      for (s <- sections) yield s.depData
    ):_*)
  }
}

case class Section(
  val document: Option[Document] = None,
  val span: Span,
  val textTokens: Seq[LSpanT],
  val text: String,
  // val annotatedText: AnnotatedText,
  val mentions: Seq[Mention], 
  val sentenceSpans: Seq[Span], 
  val pos: Seq[POS], 
  val depRelations: Seq[DepRelation],
  val nerSpans: Seq[LSpan], 
  val infoMessages: List[String] = List()
) {

  // val text = textTokens.map(_.tspan.label).mkString(" ")
  def toIndexable(): JsValue = {
    obj(
      "textdoc" ->
        obj(
          "type" -> "section",
          "text" -> text
        )
    )
  }

  lazy val relationTypes = depRelations.groupBy(_.label).keys.toSeq.map{
    label =>
    JsObj(
      "type" -> label,
      "labels" -> JsArray(label),
      "dashArray" -> "6-6",
      "color" -> "blue",
      "args" -> JsArray(
        JsObj("role" -> "From"),
        JsObj("role" -> "To")
      )
    )
  }


  val posArray = textTokens.zip(pos).zipWithIndex.map {
    case ((lspan@LSpan(_,_,_), POS(pos)), i) =>
      JsArray(("T"+i), pos,
        JsArray(JsArray(lspan.start, lspan.end)))
  }


  def depData(): JsObj = {
    val depRelationObjs = depRelations.zipWithIndex.map{
      case (dr@DepRelation(parent, _), i) =>
        JsArray(
          ("R"+i),
          dr.label,
          JsArray(
            JsArray("From", "T"+i),
            JsArray("To", "T"+parent)
          )
        )
    }

    JsObj(
      "text" -> text,
      "entities" -> JsArray(posArray:_*),
      "relations" -> JsArray(depRelationObjs:_*)
      // "attributes" -> JsArray(attribs:_*)
    )
  }

  def posData(): JsObj = {
    JsObj(
      "text" -> text,
      "entities" -> JsArray(posArray:_*)
    )
  }

}


case class POS(s: String)

case class DepRelation(parentIndex:Int, rellabel: String) {
  val label = if (rellabel.trim.isEmpty) "?" else  rellabel
}

case class TextToken(
  tspan: LSpan, // text and char span in original text
  index:Int     // index in original text
) 

case class Mention(
  id: String,
  entityId: String,
  entity: Option[InDocEntity],
  isCanonical: Boolean,
  tackbpEntityType: String, 
  mentionType: String, 
  docMentionType: String, 
  tokenSpan: Span, 
  sectionIndex:Int, 
  text: String
    // annotatedText: AnnotatedText
)  {
  //def text:String = {
  //  annotatedText.tokensInSpan(tokenSpan).map(_.label).mkString(" ")
  //}

}


case class InDocEntity(id: String, canonicalMention: String) {
}

