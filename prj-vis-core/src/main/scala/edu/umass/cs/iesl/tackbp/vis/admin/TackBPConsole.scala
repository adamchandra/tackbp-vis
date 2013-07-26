package edu.umass.cs.iesl.tackbp.vis.admin

import java.util.UUID
import com.mongodb.casbah.Imports._
import net.openreview.scalate.ajax.JsCmds
import net.openreview.scalate.ajax.JE
import net.openreview.scalate.ajax.JsExp
import org.bson.types.ObjectId
import play.api.libs.json.{JsValue, Json}, Json._
import _root_.net.openreview.util.console._
import _root_.net.openreview.util.json.JsonUtils
import net.openreview.util.annotation._

trait Queries extends SystemQueries {
  def docs = db("docs")
  def docMentions = db("docMentions")
  def docEntities = db("docEntities")
}
 
trait Formatting  {
 
}

trait MongoAdminOps extends SystemAdminOps with Formatting with Queries {
  
}

trait TacKbpOps extends MongoAdminOps with PlayWebServicing with Formatting with ElasticSearcher  {
  import java.net.URI
  import org.apache.commons.io.FileUtils
  import scalaz.syntax.validation._
  import scalaz.syntax.std.option._
  import scalaz.syntax.traverse._
  import scalaz.{ValidationNel, Traverse, NonEmptyList}, NonEmptyList._
  import scalaz.std.stream.streamInstance

  def urlEncode(s:String): String = java.net.URLEncoder.encode(s, "UTF-8")

  def toObjectId(s:String) = ObjectId.massageToObjectId(s)

  def searchResultsToDocuments(res:Seq[SearchHit]): ValidationNel[String, Seq[Document]] = {
    res.toStream.map(
      hit => hit.id
    ).map(
      findDocumentById(_)
    ).sequenceU
  }

  def findDocumentById(docId:String): ValidationNel[String, Document] = {
    for {
      d <- docs.findOne(Map("_id" -> docId)).toSuccess(NonEmptyList("document found in index but not in mongo. id is: "+docId))
    } yield docDbo2Document(d)
  }


  val sampleDocs = List(
    //"3ec18760-586c-4dbc-9a2a-0ce3ae889d18",
    //"f1aab6b4-4809-41ea-814a-c63a4d299e0a",
    //"6d917d70-51b8-4c15-9d2d-26c8ea6ac9c9",
    //"d2c90417-94ec-4dd1-8b56-58048a25a77c",
    //"d818560c-2de2-46a2-a6ff-d285aed23d86",
    //"2508cd8a-49f1-405e-aad8-a02db10f9a44",
    //"896336b2-3938-4a4b-ba51-5f7ca0186cba",
    //"3138106d-64d7-498c-a0ef-82c6badd1e14",
    //"b52764bf-debb-425b-aeba-6fe579d86a41",
    //"1895ae0a-3737-460c-9705-25fa032f2baa",
    //"e9df84bb-9218-4b73-bb64-80d55975e6f3",
    //"14ce88eb-8dad-4b4e-a6c6-ca1341704b6c",
    //"47c88451-4dea-40be-98ee-ca711f61b1bf",
    //"eb221dee-cdf0-447a-9a5f-20669724d79f",
    //"66d8b74b-4be0-4cc5-a7ce-71530132aecf",
    // "0cd116dc-ebe2-4ee4-b87d-b07b3ded5b63",
    // "87d01f00-4e41-42c4-9092-813ba21ad2b5",
    // "1d00ae02-90bc-4d3e-9e7e-d619129b5231",
    "bb04970b-dadd-4408-a91d-18d84c66d242",
    "cd1dc1ba-6e9b-41a4-9695-18e0276c6250"
  )

  def serializeSamples() {
    for {
      docId <- sampleDocs
    } {
      documentToJson(docId)
      jsonToDocument(docId)
    }
  }

  def jsonToDocument(docId: String): Document = {
    import org.json4s._
    import org.json4s.Writer
    // import org.json4s.jackson.JsonMethods._
    import org.json4s.native.JsonMethods._
    import org.json4s.native.Serialization.read

    implicit val formats = DefaultFormats

    def file(s:String) = new java.io.File(s)
    val jsonFile = file(docId+".json")

    val json = FileUtils.readFileToString(jsonFile)
    read[Document](json)
    //     res1: Child = Child(Mary,5,None)
  }

  def documentToJson(docId: String): String = {
    import org.json4s._
    // import org.json4s.JsonInput
    import org.json4s.native.JsonMethods._
    import org.json4s.native.Printer
    import org.json4s.native.Serialization
    import org.json4s.native.Serialization.{read, write}
    implicit val formats = Serialization.formats(NoTypeHints)
    // val ser = write(Child("Mary", 5, None))

    val json = findDocumentById(docId).fold(
      fail= (err => sys.error( err.list.mkString(", ") )),
      succ= (write(_))
    )

    val jsonPretty = Printer.pretty(render(parse(json)))

    def file(s:String) = new java.io.File(s)
    val jsonFile = file(docId+".json")
    FileUtils.deleteQuietly(jsonFile)
    FileUtils.write(jsonFile, jsonPretty)

    json
  }


  import scalaz.syntax.id._
  import scalaz.std.function._

  def unique(ss: Seq[String]): Seq[String] = {
    ss.groupBy(s => s).keySet.toSeq
  }

  def docDbo2Document(d: DBObject): Document = {
    val mentionDbos = docMentions.find(Map("doc" -> d.as[String]("_id"))).toSeq
    val entityDbos = mentionDbos.map(
      m=>m.getAs[String]("docEntity")
    ).filter(_.isDefined).map(_.get) |> unique flatMap(
      eid => {
        docEntities.find(Map("_id" -> eid))
      }
    )

    Document.fromBson(
      d, mentionDbos, entityDbos
    )
  }

  def docCount() = docs.size
  
  def getDocuments(start:Int=0, len:Int=10): Seq[Document] = {
    println("getDocuments")
    docs.iterator.skip(start).limit(len).map{d =>
      println("  building doc")
      docDbo2Document(d)
    }.toSeq
  }

  def getDocument(docId:String): Document = {
    findDocumentById(docId).fold(
      fail= { err =>
        sys.error( err.list.mkString(", ") )
      }, succ={ doc =>
        doc
      }) 
  }


  def bulkIndexAllDocuments() {
    bulkIndexDocuments(docs)
  }

    
  import Bx._

  // def outputDocumentEntities(docId: String): StringOutput  = {
  //   StringOutput(
  //     (for {
  //       d <- findDocumentById(docId)
  //     } yield {
  //       d
  //     }).fold(fail= { err =>
  //       err.list.mkString(", ")
  //     }, succ={ doc =>
  //       Bx.render(
  //         hjoin(sep="++++")(
  //           tbox(doc.srctext),
  //           vjoinList()(
  //             doc.entityMentions.map{
  //               case (e, ms) =>
  //                 (tbox("entity ") +| tbox(e.id)).atop(
  //                   indent(2)(
  //                     Bx.vjoinList()(ms.map{ m=>
  //                       val tspan = doc.tokens.filter(_.getClass==classOf[TextToken]).drop(m.span.start).take(m.span.length)
  //                       tbox(m.id) +| tbox(": ") +| hjoinList(sep=" ")(
  //                         tspan.map(t =>
  //                           tbox(t.asInstanceOf[TextToken].textSpan.text)
  //                         )
  //                       )
  //                     })
  //                   ))
  //             })
  //         )
  //         )
  //       })
  //     )
  //   }





  // Console-based formatting functions
  //def formatDocText(docId: String): Box = {
  //  formatDocumentText(getDocument(docId))
  //}

  def tokensInSpan(s:Span, ts:Seq[TextToken]): Seq[TextToken] = {
    ts.drop(s.start).take(s.length)
  }

//   def formatDocumentText(doc: Document): Box = {
//     val oneLineText = doc.srctext.split("\n").mkString("↲")
//  
//     val ruler = drawRuler(oneLineText.length)
//  
//     val ruledText = oneLineText atop ruler
//  
//     val splitRuled = vjoinList()(
//       (0 until oneLineText.length by 180).map{
//         i =>
//         ruledText.dropCols(i).takeCols(180)
//       })
//  
//  
//     // mention spans
//     val mentions = doc.mentions.map(
//       m => m.entity -> m
//     ).groupBy(
//       _._1.id
//     ).mapValues(
//       _.map(_._2)
//     ).map{
//       case (k, vs) =>
//         tbox("entity "+k) atop indent(4)(vjoinList()(
//           vs.map{
//             case m =>
//               val mtoks = tokensInSpan(m.span, doc.tokens)
//               // find the range of the mention in the original text
//               // val mlen = mtoks.map(_.span.length).sum
//               val start = mtoks.head.textSpan.span.start
//               val end = (mtoks.last.textSpan.span.start+mtoks.last.textSpan.span.length)
//               // println("mention: "+m)
//               // println("    start: "+start)
//               // println("    end: "+end)
//               // println("    mtoks: "+mtoks.mkString(", "))
//               val mentionText = oneLineText.substring(start, end)
//               tbox("("+start+"-"+end+"): "+mentionText)
//           }
//         ))
//     }
//  
//     vjoin()(
//       splitRuled,
//       indent(4)(
//         hjoin(top, "   ")(
//           border(vjoinList()(mentions.toList)),
//           vjoinList()(
//             doc.tokens.map{t =>
//               val tx = t.textSpan
//               tbox(tx.span.start+"-"+tx.span.length+": "+tx.text)
//             }.take(100))
//         )
//       )
//     )
//  
//   }

  // fixed size/span based ruler for text
  def drawRuler(len: Int): Box = {
    // fixed width tics
    // |     |     |     |
    hsep(0)(top)(
      (0 until len by 5).map(
        i => tbox(i.toString)
      ).zip(
        repeat(tbox("|"))
      ) map { case (i, b) =>
          (emptyBox(0)(5) atop b atop i).takeCols(5)
      } toList
    )
  }
}


object TacKbpOps extends TacKbpOps {
  override def restUrl: String = "http://vinci9:9200/"
}


object TacKbpConsole extends MongoAdminOps  {
  class DummyClass()
 
  def main(args: Array[String]) {
 
    val iLoop = new ScalaILoop {
 
      override def printWelcome() {
        echo("""|
                |   TacKbp Console
                |
                |      Type ":examples" for a quick overview, or ":help" for assistance
                |
                |""".stripMargin
        )
      }
 
      override def prompt = "tacKbp> "
 
      import LoopCommand.{nullary, cmd, varargs}
 
      // TODO this should be in a mongo CommonOps trait 
      def useDatabase(args: List[String]) {

        if (args.length==1)
          setMongoContext(_host="localhost", _dbname=args(0), "", "")
        else if (args.length==2)
          setMongoContext(_host=args(1), _dbname=args(0), "", "")
        else if (args.length==3)
          echo("syntax: dbname [host] [user pass]")
        else if (args.length==4)
          setMongoContext(_host=args(1), _dbname=args(0), _user=args(2), _pass=args(3))
        else
          echo("syntax: dbname [host] [user pass]")
        
        registerDbHelpers()
        echo("Collections: ")
        echo(getCollectionNames().mkString("\n  ", "\n  ", "\n"))
      }
 
 
      val additionalCmds = List(
        // :use
        varargs(name="use", usage="<host?> <dbname>", help="set active database", (args:List[String]) =>{
          useDatabase(args);  ()
        }).withLongHelp("""| With one argument, use the specified database on localhost.
                           | With two arguments, specify host and db
                           |""".stripMargin),
 
        // :dbs
        nullary(name="dbs", help="list databases", () => { listDatabasesBox(); () }),
 
        // :v/q verbose/quiet
        nullary(name="v", help="verbose output", () => { setVerbose(true); () }),
        nullary(name="q", help="less output (quiet)", () => { setVerbose(false); () }),
        nullary(name="examples", help="a few samples", () => { println(helpExamples) })
  
      )
 
      override def commands: List[LoopCommand] =
        standardCommands ++ additionalCmds
 
      addThunk {
        intp.beQuietDuring {
          intp.addImports(
            "com.mongodb.casbah.Imports._",
            "scalaz.syntax.id._",
            "scalaz.syntax.monad._",
            "scalaz.std.function._",
            "scalaz.std.option._",
            "scalaz.syntax.std.option._",
            "edu.umass.cs.iesl.tackbp.vis.admin.TacKbpOps._"
          )
          // useDatabase(List("openreview-devdb"))
        }
      }
 
  
      // Try to figure out how to print the last evaluated expression:
      // override def printLastValue(v: Either[Throwable, Object]): Boolean = {
 
      override def printLastValue(replResult: ReplReturnValue) {
        for {
          result <- replResult.res
        } result match {
          case Right(value) =>
            val handleOutput = value match {
              case t:Traversable[_] => pageStart(t)
              case t:Iterator[_]    => pageStart(t.toTraversable)
              case t:Option[_]      => pageStart(t.toTraversable)
              // case o:PagedStreamResult  => pageMore(o); true
              case o:ReplResult  => pageMore(o); true
              case o:DBObject       => pageStart(Stream(o)); true
              case o:UUID           => pageStart(Stream(o)); true
              case o:StringOutput   => println(o.s); true
              case o:JsValue        => println(JsonUtils.prettyPrintJson(o)); true
              case o:Bx.Box         => println(Bx.render(o)); true
                // {
                //   // render a box as a list of lines to prevent strange formatting on line wrap
                //   val sdf = Bx.renderBox(o)
                //   pageStart(Bx.renderBox(o).map(StringOutput(_))); true
                //   // println(Bx.render(o)); true
                // }
              case _ => false
            }
 
            if (!handleOutput) super.printLastValue(replResult)
 
          case Left(throwable) =>
            super.printLastValue(replResult)
            throwable.printStackTrace(out)
        }
      }
    }
 
 
    val settings = ScalaConsole.newSettings[DummyClass]
 
    iLoop.process(settings)
  }
}
