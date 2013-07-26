package edu.umass.cs.iesl.tackbp.vis.admin

import org.bson.types.BasicBSONList
import com.weiglewilczek.slf4s.Logging
import ch.qos.logback.classic.LoggerContext

import java.util.UUID

import org.joda.time.DateTime, org.joda.time.format.DateTimeFormat

import org.slf4j.LoggerFactory
import ch.qos.logback.core.util.StatusPrinter
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoCollection
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json._

import _root_.net.openreview.util.console._
import _root_.net.openreview.util.boxes.Boxes._

import
  scalaz.std.function._,
  scalaz.std.option._,
  scalaz.std.list._,
  scalaz.syntax.id._,
  scalaz.syntax.monad._,
  scalaz.syntax.validation._,
  scalaz.syntax.std.option._,
  scalaz.syntax.traverse._,
  scalaz.{
    ValidationNel, Validation, Success, Failure,
    Traverse, NonEmptyList
  } 


case class StringOutput(s:String)

trait SystemImports  {
 
  val helpExamples = """
 
Switch to database
  :use my-database
 
Show all databases
  :dbs

Set verbosity with :v (verbose) or :q (quiet)
  The console output sometimes inadvertently eats error messages, 
  so use ":v" if some command silently fails 
 
Collection names are:

 
Queries: 
 
      
"""
 
  implicit def str2ops = CasbahOps(_)
 
  trait CasbahOpsLow {
    def lhs: String
    def $eq(v:String) = MongoDBObject(lhs -> v)
    def $eq(uuid:UUID) = MongoDBObject(lhs -> uuid)
    def $equid(uuids:String) = MongoDBObject(lhs -> str2uuid(uuids))
 
    def str2uuid(s:String) = UUID.fromString(s)
 
    def $like(rhs:String): MongoDBObject = 
      MongoDBObject(lhs -> 
        MongoDBObject("$regex" -> rhs, "$options" -> "i"))
 
 
    def ~(lhs:String) = $like(lhs)
 
    def clip(maxlen:Int=80) = clipString(lhs, maxlen)
    def oneLine() = mkOneLine(lhs)
  }
 
  case class CasbahOps(
    override val lhs:String
  ) extends CasbahOpsLow
 
  def clipString(str:String, maxlen:Int=80): String = 
    str.substring(0, math.min(str.length, maxlen)) + (if (str.length > maxlen) "..." else "")
 
  def quoted(s:String) = "\""+s+"\""
 
  def mkOneLine(s:String) = s.replaceAll("[\n\r\t\\s]+", " ")

  // TODO use config values
  // var mongoContext = EasyMongoContext("localhost", "test", user="", pass="")

  //def conn            : MongoConnection  = mongoContext.conn
  //def db              : MongoDB          = mongoContext.db            
  //def dbname          : String           = mongoContext.dbname
  //def host            : String           = mongoContext.host

  
  var dbname          : String           = "test"
  var host            : String           = "localhost"
  var user            : String           = ""
  var pass            : String           = ""
  var conn            : MongoConnection  = null
  var db              : MongoDB          = null

  def connectDb() {
    conn = MongoConnection(host)
    db = conn(dbname)
    db.authenticate(user, pass)
  }

  // Server-side javascript storage (mongo documentation says use with great caution)
  def systemJs  : MongoCollection  = db("system.js")
 
  def setMongoContext(_host:String, _dbname:String, _user: String, _pass: String) {
    dbname =  _dbname
    host   =  _host
    user   =  _user
    pass   =  _pass

    connectDb()
  }
 
 
  def getAsUUID(field:String)(implicit dbo: MongoDBObject): Option[UUID] = 
    dbo.getAs[UUID](field)
 
  def getDateTime(field:String)(implicit dbo: MongoDBObject) = 
    dbo.getAs[DateTime](field)
 
  def getUUID(field:String)(implicit dbo: MongoDBObject):UUID = 
    dbo.getAs[UUID](field).getOrElse(sys.error("required uuid not found in MongoDBObject: "+dbo))

  def getObjectId(field:String="_id")(implicit dbo: MongoDBObject): ObjectId = 
    dbo.getAs[ObjectId](field).getOrElse(sys.error("required ObjectId not found in MongoDBObject: "+dbo))

}
 
trait SystemQueries extends SystemImports  {
 
  def installSystemJavascripts() {
    try {
      import sys.process._
      println("updating server-side javascript")
      val js = "./prj-openreview-core/src/main/javascript/serverJavascript.js";
      val install = "./prj-openreview-core/src/main/javascript/installJs.js";
      ("mongo "+db.name+" "+js+" "+install).!
    } catch {
      case _ => println("error installing system javascript")
    }
  }
 
  def testSSJavascript() {
    evalJavascript("hello()")
  }
 
  def evalJavascript(func: String, args: String*): Validation[String, Object] = {
    val result = db.command(Map(
      "eval" -> func,
      "args" -> args.toList
    ));
 
    if (result.ok) {
      result.get("retval").success
    } else {
      result.getErrorMessage().failure
    }
  }
 
 
  def rawFormat(id:UUID): StringOutput = {
    (for {
      r <- evalJavascript("function (juuid) { return objectToString(findByJuuid(juuid)); }", id.toString)
    } yield {
      r.toString()
    }).fold({
      err => StringOutput(err)
    }, {
      ok => StringOutput(ok)
    })
  }
 
  def getCollectionNames(): List[String] = {
    db.getCollectionNames().toList
  }

}
 
 
 
// record formatting varieties:
sealed trait Format
// case object FullDescription extends Format
// case object ShortDescription extends Format
case object Summary extends Format
 
trait SystemFormatting extends SystemQueries {
  import _root_.net.openreview.util.boxes.Boxes
  val Bx = Boxes
 
  def keyValBox[A](f:String)(implicit dbo: MongoDBObject, m:Manifest[A]): Box = {
    (dbo.getAs[A](f) map (
      (v:A) => tbox(f + ": "+v.toString))
    ) |> boxOrEmpty
  }
 
  def valBox[A](f:String)(implicit dbo: MongoDBObject, m:Manifest[A]): Box = {
    (for {
      v <- dbo.getAs[A](f)
    } yield {
      tbox(v.toString)
    }) getOrElse(
      tbox("<none>")
    )
  }
 
 
 
  def boxOrMessage(msg:String): Option[Box] => Box = ob => ob.getOrElse(tbox(msg))
  def boxOrEmpty: Option[Box] => Box = ob => ob.getOrElse(nullBox)
 
  def asDBList(f:String)(implicit dbo: MongoDBObject) = dbo.getAs[MongoDBList](f)
 
 
  def getTraversable[A](field:String)(implicit dbo: MongoDBObject): Traversable[A] = {
    asDBList(field)(dbo).map (
      _.asInstanceOf[Traversable[A]]
    ).getOrElse(
      Seq[A]()
    )
  }
 
  def indent(n:Int=4)(b:Box): Box = {
    emptyBox(1)(n) + b
  }
 
 
 
  def list(c: Traversable[DBObject])(implicit fmt:Format = Summary) {
    println("\n")
    c.foreach{ o => println(renderDbo(o, fmt)); println() }
  }
 
  def renderDbo(implicit dbo: MongoDBObject, fmt:Format): String = {
    render(mongoDboBox)
  }
 
  def mongoDboBox(implicit dbo: MongoDBObject, fmt:Format): Box = {
    recordBox
  }

  def iconic1Field(field:String)(implicit dbo: MongoDBObject): Box = {
    field match {
      case _ => (for {
        v <- dbo.get(field)
      } yield {
        if (v.isInstanceOf[UUID]) {
          keyValBox(field)
        } else if (v.isInstanceOf[DateTime]) {
          val fmt = DateTimeFormat.forPattern("EEE MMM d yyyy hh:mm:ss aa")
          tbox(fmt.print(v.asInstanceOf[DateTime]))
        } else if (v.isInstanceOf[String]) {
          tbox(v.toString.oneLine.clip())
        } else {
          tbox(v.toString.oneLine.clip())
        }
      }) |> boxOrEmpty
    }
  }

  def recordBox(implicit dbo: MongoDBObject, format: Format): Box = {
    val keys = dbo.keySet
    val uuids = for { 
      k <- keys.toList
      v <- dbo.get(k) if v.isInstanceOf[UUID] 
    } yield k

    val lists = for { 
      k <- (keys -- uuids).toList 
      v <- dbo.get(k) if v.isInstanceOf[BasicBSONList] 
    } yield k

    val dbos = for { 
      k <- (keys -- uuids -- lists).toList 
      v <- dbo.get(k) if v.isInstanceOf[BasicDBObject] || v.isInstanceOf[DBObject] 
    } yield k

    val other = (keys -- Set((uuids ++ lists ++ dbos):_*)).toList

    format match {
      case Summary  =>
        val boxes1 = for {
          k <- uuids
        } yield tbox(k.toString) -> iconic1Field(k)

        val boxes2 = for {
          k <- other
          v <- dbo.get(k)
        } yield tbox(k.toString) -> iconic1Field(k)

        val boxes3 = for {
          k <- dbos
          v <- dbo.get(k)
        } yield {
          recordBox(v.asInstanceOf[DBObject], Summary)
        }

        val boxesLists = for {
          k <- lists
          v <- dbo.get(k)
        } yield tbox(k.toString) -> tbox("size="+v.asInstanceOf[BasicBSONList].size)

        val boxes = boxes1 ++ boxes2 ++ boxesLists


        vjoin()(
          tbox("mongo record") atop
          indent()(
            vjoin()(
              hjoin()(
                borderLeftRight("", " : ")(
                  vjoin()(boxes.map(_._1):_*)
                ),
                vjoin()(boxes.map(_._2):_*)
              ),
              vjoin()(boxes3:_*)
            )
          )
        )
    }
  }
 
}
 
//  

trait SystemAdminOps extends SystemFormatting with Logging {
 
 
  def registerDbHelpers() {
    val lc:LoggerContext  =LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
    logger.info("using MongoDB backend storage: %s".format(this.getClass))
    com.mongodb.casbah.commons.conversions.scala.RegisterConversionHelpers()
    com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers()
  }
 
 
  def copyDatabase(fromHost: String, fromDb: String, toDb: String) {
    val result = conn("admin").command(Map(
      "copydb" -> 1,
      "fromhost" -> fromHost,
      "fromdb" -> fromDb,
      "todb" -> toDb
    ))
 
    val rdbo = new MongoDBObject(result)
    println(rdbo)
  }
 
  def listDatabasesBox() {
 
    def dblistBox(implicit dbo: MongoDBObject) = {
      keyValBox[String]("serverUsed") % indent()(
        vjoin()(
          getTraversable[DBObject]("databases").toList map (
            o => dbBox(new MongoDBObject(o))
          ):_*
        )
      )
    }
 
    def dbBox(implicit dbo: MongoDBObject) = vjoin()(
      valBox[String]("name"), indent()( vjoin()(
        keyValBox[Int]("sizeOnDisk"),
        keyValBox[Boolean]("empty")
      ))
    )
 
    val result = conn("admin").command(
      Map("listDatabases" -> 1 )
    )
    val rdbo = new MongoDBObject(result)
 
    dblistBox(rdbo)  |> render |> println
  }
 
  case class MongoDBInfo(name: String, sizeOnDisk: Double, empty: Boolean)
 
  def listDatabases(): List[MongoDBInfo] = {
    val result = conn("admin").command(
      Map("listDatabases" -> 1)
    );
    (getTraversable[DBObject]("databases")(result)).toList.map{dbo =>
      MongoDBInfo(
        dbo.getAs[String]("name").get,
        dbo.getAs[Double]("sizeOnDisk").get,
        dbo.getAs[Boolean]("empty").get
      )
    }
  }
 
 
  sealed trait ReplResult {
    def header: Option[String] = None
    def footer: Option[String] = None 
    def renderer: Any => String = _.toString
  }
 
  type RenderFunction = Any => String
 
  case object EmptyResult extends ReplResult
 
  case class PagedStreamResult(
    curr: Stream[_], 
    rest: Stream[_], 
    currOffset: Int,
    override val renderer: RenderFunction, 
    ctype: String, 
    vtype: String
  ) extends ReplResult {
    override def header = {
      Some("Displaying results "+(currOffset+1)+"-"+(currOffset+curr.length)+" of type "+vtype)
    }
    override def footer = {
      if (rest.isEmpty) Some("No more results")
      else Some("Type `more` for more results")
    }
  }
 
  var pagerSize = 8
 
  var _replResult: ReplResult = EmptyResult
 
  def more: ReplResult = {
    _replResult = _replResult match {
      case EmptyResult => EmptyResult
      case PagedStreamResult(curr, rest, offset, r, tc, vc) =>
        if (rest.isEmpty) 
          EmptyResult
        else 
          PagedStreamResult(rest.take(pagerSize), rest.drop(pagerSize), offset+pagerSize, r, tc, vc)
    }
    _replResult
  }
 
 
  // Called whenever repl is trying output a value of type Traversable[_]
  def pageStart(t:Traversable[_]): Boolean = {
    val tclass = t.getClass.getName()
    val tvclass = t.headOption.map(_.getClass().getName).getOrElse("<empty>")
 
    val (renderer: RenderFunction, handle:Boolean) = (tvclass match {
      case "com.mongodb.BasicDBObject" => 
        ((v:Any) => renderDbo(new MongoDBObject(v.asInstanceOf[DBObject]), Summary)) -> true

      case "edu.umass.cs.iesl.tackbp.vis.admin.StringOutput" =>
        ((v:Any) => v.asInstanceOf[StringOutput].s) -> true
  
      case _ =>
        ((v:Any) => render(tbox("<not handled>"))) -> false
    }).asInstanceOf[(RenderFunction, Boolean)]
 
    if (handle) {
      _replResult = PagedStreamResult(t.toStream.take(pagerSize), t.toStream.drop(pagerSize), 0, renderer, tclass, tvclass)
      pageMore(_replResult)
    }
 
    handle
  }
 
 
  def pageMore(rr:ReplResult) {
    rr match {
      case m@PagedStreamResult(curr, rest, offset, r, tc, vc) => // stream, r) =>
        m.header.foreach(println(_))
        curr foreach {x => println(r(x))}
        m.footer.foreach(println(_))
      case _ => 
        println("no more results")
    }
  }
 
}

