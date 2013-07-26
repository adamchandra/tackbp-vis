package controllers

import com.weiglewilczek.slf4s.Logging
import lib.template.ScalatePlaySupport
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.Play.current


import edu.umass.cs.iesl.tackbp.vis.admin._


object Index extends Controller with ScalatePlaySupport with ElasticSearcher with Logging {

  import TacKbpOps._

  override val restUrl: String = (for {
      conf <- current.configuration.getConfig("elasticsearch")
      conf <- conf.getConfig("dev")
      url <- conf.getString("url")
    } yield url).getOrElse("http://localhost:9200/")

  // ping searcher, if not available fall back on mongo cursors
  // NOPUSH change list size
  val resultListSize = 10

  def search(query: Option[String], offset: Option[Int]) = Action { implicit request =>
    val q = query.getOrElse("")
    val off = (offset.getOrElse(0) max 0) 
   
    (for {
      results <- doSearch(q, off, resultListSize).toValidationNel[String, SearchResults]
      docs <- TacKbpOps.searchResultsToDocuments(results.hits)
    } yield {
      Ok(pages.IndexPage(docs, results).template)
    }).fold(
      err => {
        val docList = getDocuments(off, resultListSize)
        Ok(pages.IndexPage(docList,
          Pager(new PagerParams(off, resultListSize),
            docCount(),
            docList)
        ).template)
      },
      a => a
    )
  }

  def scroll(offset: Option[Int]) = Action { implicit request =>
    val off = (offset.getOrElse(0) max 0)
    val docList = getDocuments(off, resultListSize)
    Ok(pages.IndexPage(
      docList,
      Pager(
        new PagerParams(off, resultListSize),
        docCount(),
        docList
      )
    ).template)
  }


  val searchForm = Form (
    "query" -> text
  )

  def search = Action { implicit request =>
    searchForm.bindFromRequest.fold(
      formWithErrors => {
        logger.error("formWithErrors: "+formWithErrors.errors.mkString(", "))
        Redirect("/").flashing("error" -> formWithErrors.errors.mkString(", "))
      },
      query => {
        Redirect(
          "/search?query="+java.net.URLEncoder.encode(query, "UTF-8")
        )
      }
    )
  }


}

