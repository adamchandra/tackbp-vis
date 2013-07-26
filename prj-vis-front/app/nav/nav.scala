package nav

import controllers._
import org.bson.types.ObjectId
import play.navigator._
import scala.xml._
import play.api.mvc._
import java.util.UUID
import play.api.Play.current
import play.api.Play

import java.io.UnsupportedEncodingException
import java.net.{URLEncoder, URLDecoder}
import scala.util.control.Exception._


object nav extends PlayNavigator {

  implicit val UUIDPathParam: PathParam[UUID] = new PathParam[UUID] {
    def apply(t: UUID) = t.toString

    def unapply(s: String) =
      catching(classOf[IllegalArgumentException]) opt (UUID.fromString(s))
  }


  implicit val ObjectPathParam: PathParam[ObjectId] = new PathParam[ObjectId] {
    def apply(t: ObjectId) = t.toString

    def unapply(s: String) = 
      catching(classOf[IllegalArgumentException]) opt (ObjectId.massageToObjectId(s))
  }


  GET   on "mockup" / ** to ((view: String) => pages.Mockups.index(view))

  // val index = GET on root to Index.index()
  GET   on "admin" to pages.AdminPage.index 
  GET   on "scalate" to admin.scalate.ScalatePage.index

  
  GET   on "document" / * to ((oid: String) => DocumentController.index(oid))
  POST  on "search" to Index.search _
  
  val assetsVersion = "13"
  val assets = GET on "assets" / assetsVersion / ** to ((p: String) => controllers.Assets.at(path = "/public", p))
  // val assets = GET on "assets" / ** to ((p: String) => controllers.Assets.at(path = "/public", p))
}

