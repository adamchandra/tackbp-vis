package test
 
import play.api.test._
import play.api.test.Helpers._
import play.api.http.Status        
import play.api.mvc._
 
import org.specs2.mutable._
import nav.nav


class ApplicationSpec extends Specification {
  
  def runInApp(b: => org.specs2.execute.Result): org.specs2.execute.Result = {
    running(FakeApplication(
      additionalConfiguration = (
        Map(
          "application.secret" -> "x<04CFWG=EWmTOy13EC^ErsbUT:tSUZ8B^4p5let9EcvV?dMm7G5LdxJylb`/f2[",
          "smtp.mock" -> "true"
        )
      ),
      withoutPlugins = Seq(
        "securesocial.core.providers.GoogleProvider",
        "service.InMemoryUserService", 
        "securesocial.core.providers.UsernamePasswordProvider",
        "com.typesafe.plugin.CommonsMailerPlugin"
      )
    )) { b }
  }
  

  // import play.navigator._
  // import org.specs2.mutable._
  // import play.api.test._
  // import play.api.test.Helpers.{routeAndCall, contentAsString}

  def routeNav[T](method: String, path: String) = {
    val req = FakeRequest(method, path).asInstanceOf[FakeRequest[T]]
    nav.routes.lift(req).map {
      case action: Action[_] => contentAsString(action.asInstanceOf[Action[T]](req))
    }
  }

  def getNav(path: String) = routeNav("GET", path)


  "URL redirection" should {
    
    "redirect profile page to login w/o valid user" in { runInApp {
      val Some(result) = route(FakeRequest(GET, "/profile"))
      status(result) must equalTo(Status.SEE_OTHER)
    }}

    "allow profile page access with valid user" in { runInApp {
      val Some(result) = route(FakeRequest(GET, "/profile").withSession("login" -> "someone"))
      // status(result) must equalTo(Status.OK)
      todo
    }}

    "smokescreen 2" in { runInApp {
      val fakePost: FakeRequest[scala.xml.NodeSeq] = FakeRequest(
        method = "POST",
        uri = "",
        headers = FakeHeaders(Seq(CONTENT_TYPE -> Seq("application/xml"))),
        body =  (<xml>fake data</xml>)
      )

      val fakeGet = FakeRequest(
        GET,
        ""
      )

      // Can either directly route to api
      /// val result = asyncToResult(controllers.api.Index.submit("delving")(fakeRequest))
      // status(result) must equalTo(OK)


      // Or go through routing engine (nav)
      // val app = play.Play.application
      // def asyncToResult(response: Result) = response.asInstanceOf[AsyncResult].result.await.get

      val Some(result) = route(FakeRequest(GET, "/profile"))

      // // await(result)
      println("result: " + result)

      status(result) must equalTo(Status.SEE_OTHER)


      // val result = route(FakeRequest(GET, "/library"))
      // nav.library(FakeRequest(GET, "/library"))
      //val result2 = asyncToResult(controllers.api.Index.submit("delving")(fakeRequest))

      // val result  = get("/library/")
      

      // status(result.get) must equalTo(200)

    }}

    // "list the secured product page with credentials" in { runInApp {
    //   val result  = route( FakeRequest( GET, "/").withSession("email"->"guillaume@sample.com")).get
    //   status(result) must equalTo(200)
    // }}

  }
}

