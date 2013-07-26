package edu.umass.cs.iesl.tackbp.vis.admin

import java.util.UUID
import com.mongodb.casbah.Imports._
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.net.ConnectException
import play.api.libs.json.{JsValue, Json}, Json._
import _root_.net.openreview.util.console._
// import _root_.model._
import com.weiglewilczek.slf4s.Logging


import scala.concurrent.Await, scala.concurrent.util.Duration

import scalaz.std.option._

import scala.xml._

// TODO put search classes elsewhere
case class SearchHit(
  id:String,
  score: Double,
  source: JsValue
)

trait PagerParamsT {
  def page: Int // 0-indexed page
  def pageSize: Int
}

case class PagerParams(
  val page: Int,
  val pageSize: Int
) extends PagerParamsT

case class Pager(
  qps: PagerParams,
  totalHits: Int,
  items: Seq[Document]
) {
  val pages = 0 to totalHits by qps.pageSize
  val from = pages(qps.page)
  val (pre,post) = pages.splitAt(qps.page)
  val to = from + qps.pageSize - 1 min totalHits
  val totalPages = pages.size

  def navLinks(): List[Node] = {
    def link(off:Int, icon:String):Node = {
      def enc(s:String) = java.net.URLEncoder.encode(s, "UTF-8")
      <a href={"/?query=&offset="+off}>{icon}</a>
    }

    val navLen = 4
    val pad = Array.fill(navLen)(-1).toList

    val back = (pre.reverse).zipWithIndex.takeWhile{
      case (p, i) =>
        val step:Int = math.pow(10, i).toInt
        val docn = from - (step*qps.pageSize)
        val inRange = docn >= 0
        i < navLen && inRange
    }.map{
      case (p, i) =>
        val step:Int = math.pow(10, i).toInt
        val docn = from - (step*qps.pageSize)
        link(qps.page-step, ""+docn)
    }.reverse

    val fwd = (post).zipWithIndex.takeWhile{
      case (p, i) =>
        val step:Int = math.pow(10, i).toInt
        val docn = from + (step*qps.pageSize)
        val inRange = docn <= pages.last
        i < navLen && inRange
    }.map{
      case (p, i) =>
        val step:Int = math.pow(10, i).toInt
        val docn = from + (step*qps.pageSize)
        link(qps.page+step, ""+docn+"")
    }

    val middle = <span>{""+from+"-"+to+"/"+totalHits}</span>

    (back ++ middle ++ fwd).toList
  }
}


case class QueryParams(
  val query: String,
  val page: Int,
  val pageSize: Int
) extends PagerParamsT

case class SearchResults(
  qps: QueryParams,
  took: Int,
  totalHits: Int,
  hits: Seq[SearchHit]
) {
  val pages = 0 to totalHits by qps.pageSize
  val from = pages(qps.page)
  val (pre,post) = pages.splitAt(qps.page)
  val to = from + qps.pageSize - 1 min totalHits
  val totalPages = pages.size

  // <<30  [4]/300000   30>>
  def navLinks(): List[Node] = {
    def link(q:String, off:Int, icon:String):Node = {
      def enc(s:String) = java.net.URLEncoder.encode(s, "UTF-8")
      <a href={"/?query="+enc(q)+"&offset="+off}>{icon}</a>
    }

    val navLen = 4
    val pad = Array.fill(navLen)(-1).toList

    val back = (pre.reverse).zipWithIndex.takeWhile{
      case (p, i) =>
        val step:Int = math.pow(10, i).toInt
        val docn = from - (step*qps.pageSize)
        val inRange = docn >= 0
        i < navLen && inRange
    }.map{
      case (p, i) =>
        val step:Int = math.pow(10, i).toInt
        val docn = from - (step*qps.pageSize)
        link(qps.query, qps.page-step, ""+docn)
    }.reverse

    val fwd = (post).zipWithIndex.takeWhile{
      case (p, i) =>
        val step:Int = math.pow(10, i).toInt
        val docn = from + (step*qps.pageSize)
        val inRange = docn <= pages.last
        i < navLen && inRange
    }.map{
      case (p, i) =>
        val step:Int = math.pow(10, i).toInt
        val docn = from + (step*qps.pageSize)
        link(qps.query, qps.page+step, ""+docn+"")
    }

    val middle = <span>{""+from+"-"+to+"/"+totalHits}</span>


    (back ++ middle ++ fwd).toList

  }
}



trait ElasticSearcher extends PlayWebServicing with Logging {
  import org.apache.commons.io.FileUtils
  val DeftIndex = "deft"
  val DeftDoctype = "textdoc"
  def restUrl: String //  = "http://localhost:9200/"

  def isAvailable: Boolean = true

  def elasticEndpoint(index: String, doctype: Option[String]=None): String = {
    (restUrl+index+doctype.map("/"+_).getOrElse(""))
  }

  // def bulkIndexDocuments(docs: Iterable[Document]) {
  def bulkIndexDocuments(docColl: MongoCollection) {
    val docIter = docColl.iterator

    def action(id:String) = obj(
      "index" -> obj(
        "_index" -> DeftIndex,
        "_type" -> DeftDoctype,
        "_id" -> id
      ))

    val batchSize = 1000

    // var batch: Iterator[DBObject] = docIter.take(batchSize)
    val batch =  scala.collection.mutable.ListBuffer[DBObject]()

    def nextBatch() {
      batch.clear()
      var currBatchSize = 0
      while(docIter.hasNext && currBatchSize < batchSize) {
        batch.append(docIter.next())
        currBatchSize += 1
      }
    }

    nextBatch()

    while (! batch.isEmpty) {
      println("creating index batch")
      val indexBatch = ((batch foldLeft List[JsValue]()) {
        case (acc, doc) =>
          val d = Document.fromBson(doc, Seq(), Seq()) // TODO docMentions.find(Map("doc" -> d.as[String]("_id"))).toSeq
          d.toIndexable :: action(d.id) :: acc
      }).reverse.map(
        Json.stringify(_)
      ).mkString("\n", "\n", "\n")

      println("   ... created batch of size "+batch.length)

      val result = webService.url(elasticEndpoint(DeftIndex, some("_bulk"))).post(
        indexBatch
      ) andThen ({
        case Left(t) =>
          println("error: "+t.getMessage())
        case Right(resp) =>
          println("  ... elastic search bulk insert completed")
      })

      // this can happen while we're waiting for the indexing 
      nextBatch()

      Await.result(
        result,
        Duration(5, TimeUnit.MINUTES)
      )
    }
  }


  def dropAndRecreateIndex() {
    val url = elasticEndpoint(DeftIndex)

    val result = webService.url(url).delete().andThen({
      case Left(t) =>
        println("error: "+t.getMessage())
      case Right(resp) =>
        println("deleting index: "+resp.json)
        createIndex()
    })

    Await.result(
      result,
      Duration(5, TimeUnit.MINUTES)
    )
  }


  def createIndex() {
    val url = elasticEndpoint(DeftIndex)

    val result = Await.result(
      for {
        res <- webService.url(url).put(
          obj(
            "settings" -> obj(),
            "mappings" -> obj(
              DeftDoctype -> obj(
                "properties" -> obj(
                  "text" -> obj(
                    "type" -> "string"
                  )))
            )))
      } yield {
        println("creating index: "+res.json)
        res
      },
      Duration(10, TimeUnit.SECONDS)
    )
  }


  def indexDocument(doc:Document) {
    val url = elasticEndpoint(DeftIndex, some(DeftDoctype))+"/"+doc.id
    println("indexDocument url = "+url)
    val indexable = doc.toIndexable()
    println("indexing: "+indexable)
    webService.url(url).put(indexable).andThen({
      case Left(t) =>
        println("error: "+t.getMessage())
      case Right(resp) =>
        println("indexer: "+resp.json)

    })
  }

  import scalaz.Validation, Validation._, scalaz.syntax.validation._

  def doSearch(terms: String, offset: Int, size: Int): Validation[String, SearchResults] = {
    val q = if (terms.trim.length==0) {
      obj(
        "match_all" -> obj()
      )
    } else {
      obj(
        "multi_match" -> obj(
          "query" ->  terms,
          "fields" -> arr("text")
        )
      )
    }
    import scala.util.control.Exception._

    catching(classOf[ConnectException]).opt({
      val pingOpen = for {
        open <- webService.url(elasticEndpoint(DeftIndex)+"/_open").post(obj())
      } yield open
      Await.result(pingOpen, Duration(2, TimeUnit.SECONDS))
    }).map {openResp =>
      Await.result(
        for {
          valid <- webService.url(elasticEndpoint(DeftIndex, some(DeftDoctype))+"/_validate/query?pretty=true&explain=true").post(q)
          res <- webService.url(elasticEndpoint(DeftIndex, some(DeftDoctype))+"/_search?pretty=true").post(obj("query" -> q, "from" -> offset, "size" -> size))
        } yield {
          val jres = res.json

          if ( (jres \ "status").asOpt[Int].getOrElse(200) != 200) {
            println("jres = "+jres)
              (jres \ "error").as[String].failure
          } else {
            new SearchResults(
              QueryParams(
                query = terms, page = offset, pageSize = size
              ),
              (jres \ "took").as[Int],
              (jres \ "hits" \ "total").as[Int],
              for (hit <- (jres \ "hits" \ "hits").as[Seq[JsValue]]) yield {
                SearchHit(
                  (hit \ "_id").as[String],
                  (hit \ "_score").as[Double],
                  (hit \ "_source")
                )
              }
            ).success
          }
        },
        Duration(6, TimeUnit.SECONDS)
      )
    }.getOrElse(
      "index not available".failure
    )
  }

}
