package net.openreview.scalate

import java.io.{PrintWriter, StringWriter}
import org.fusesource.scalate._
import org.fusesource.scalate.support.AttributesHashMap
import org.fusesource.scalate.util._
import org.fusesource.scalate.util.Strings.isEmpty
import play.api.http.Status._
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import com.weiglewilczek.slf4s.Logging


class Sample() {
  def a = "AAA!!!"
  def b = 34
  def i = 34
  def hi = "Hello?"
}






object ScalateSupportApp extends Logging {
  /**
    Roadmap

    - Today
      - [x] get mustache support pushed 
        - [x] reinstate scalate sbt plugin
        - [ ] get error handling working well
        - make sure exceptions are properly thrown or displayed
        
    - Use validations to return rendered templates instead of exception logic
    - javascript mustache support for client-side template creation

    - basic view rendering working
      - with attribs, bindings

    - figure out what happens when a pre-compiled template is loaded with extra bindings
      - is it re-compiled? wrapped in another template?

    */


  object engineFactory extends ScalateEngineFactory {
    override def getFile(s:String): JFile  = file(s)
    override def classloader: java.lang.ClassLoader  =  this.getClass.getClassLoader()
    override def templateRootPaths: Seq[JFile]  = Seq()
    override val layoutMode = false
    override val mode: ScalateMode = DevMode
  }

  def main(args: Array[String]) = {

    val eng = engineFactory.configureEngine { eng => 
      // This is the default resource loader, just here to show how to override it:
      eng.resourceLoader = CustomResourceLoader(
        sourceDirectories = None, // list of files (dirs) in which to search for templates
        classloader = this.getClass.getClassLoader(), // 
        resourcePrefix = "scalate" // subdirectory withing src/../resources/ directory to look for templates
      )
    }

    try {
      precompileAll(eng)
      // debugLayoutSteps("/samples/helloNesting.jade", eng)
      // basicLayout(eng)
      // errorInTemplate(eng)
      // basicLayoutNested(eng)
      // mustacheSample(eng)
      // mustacheSimpleTemplate(eng)
      basicModelRendering(eng)
    }
    finally {
      eng.shutdown()
    }
  }

  def precompileAll(eng: EngineLike) {


  }

  def mustacheSample(eng: CustomTemplateEngine) {
    val templateText = """|
                          | Simple interpolation: {{a}}
                          |
                          | Html escaped/unescaped: 
                          |   {{htmlEsc}}
                          |   {{{htmlUnesc}}}
                          |
                          |
                          | Show/hide sections based on attributes: 
                          |   {{#existingAtt}}
                          |      This attribute exists: {{existingAtt}}
                          |   {{/existingAtt}}
                          |   {{#nonexistingAtt}}
                          |      This block should not appear
                          |   {{/nonexistingAtt}}
                          |   {{^nonexistingAtt}}
                          |      This attribute doesn't exist: nonexistingAtt
                          |   {{/nonexistingAtt}}
                          |
                          |
                          | List handling:
                          |   {{#names}}
                          |      {{name}}
                          |   {{/names}}
                          |
                          |
                          | Functions as attribute:
                          |   {{#fn-input}}
                          |      {{a}}
                          |   {{/fn-input}}
                          |
                          |
                          |""".stripMargin


    val templateSource = TemplateSource.fromText(uri="email.mustache", templateText)
    println(eng.layout(
      templateSource, Map(
        "a" -> "one",
        "htmlEsc" -> "<a href='/foo/bar'>Qqq</a>",
        "htmlUnesc" -> "<a href='/foo/bar'>Yyyy</a>", 
        "existingAtt" -> "attValue", 
        "names" -> List(Map("name" -> "Alex"), Map("name" -> "Bob"), Map("name" -> "Cathy")), 
        "fn-input" -> ((s:String) => <b>{s.trim}</b>)
      )
    ))

  }

  def mustacheSimpleTemplate(eng: CustomTemplateEngine) {
    val outerText = """|
                       | Outer header
                       |  {{body}}
                       | Outer footer
                       |
                       |""".stripMargin

    val innerText = """|
                       | Inner text with names
                       |   {{#names}}
                       |      {{name}}
                       |   {{/names}}
                       |""".stripMargin

    val outerTemplate = TemplateSource.fromText(uri="outer.mustache", outerText)
    val innerTemplate = TemplateSource.fromText(uri="inner.mustache", innerText)

    println(eng.layout(outerTemplate, Map(
        "body" -> eng.layout(innerTemplate, Map(
          "names" -> List(Map("name" -> "Alex"), Map("name" -> "Bob"), Map("name" -> "Cathy"))
        ))
      )
    ))

  }

  def basicAjaxRendering(eng:EngineLike) {
    println(eng.model(new Sample(), "index"))
  }

  def basicModelRendering(eng:EngineLike) {
    println(eng.model(new Sample(), "index"))
  }

  def basicLayoutNested(eng:CustomTemplateEngine) {
    // val ctx = CustomRenderContext("/samples/helloNesting.jade", eng)
    // val df = ctx.render("/samples/helloNesting.jade", Map())
    val output = eng.layout("/samples/helloNesting.jade")
    println(output)
  }

  def debugLayoutSteps(uri: String, eng:CustomTemplateEngine) {
    doLayoutVeryExplicitly(eng, uri, // "/samples/helloWorld.jade",
      Map("bind1" -> 14),
      List(
        Binding(
          name                   = "bind1",                          // : String,
          className              = "Int",                            // : String = "Any",
          importMembers          = false,                            // : Boolean = false,
          defaultValue           = None,                             // : Option[String] = None,
          kind                   = "val",                            // : String = "val",
          isImplicit             = false                             // : Boolean = false,
        )
      )
    )
  }

  def basicLayout(eng:CustomTemplateEngine) {
    val str = eng.layout("/samples/helloWorld.jade",
      attributes = Map("bind1" -> 14),
      extraBindings = List(
        Binding(
          name                   = "bind1",                          // : String,
          className              = "Int",                            // : String = "Any",
          importMembers          = false,                            // : Boolean = false,
          defaultValue           = None,                             // : Option[String] = None,
          kind                   = "val",                            // : String = "val",
          isImplicit             = false                             // : Boolean = false,
        )
      )
    )
    println(str)
  }


  // def errorInTemplate(eng:CustomTemplateEngine) {
  //   val tsrc = "samples/helloError.jade"
  //   try {
  //     val str = eng.layout(tsrc,
  //       attributes = Map("a" -> 1, "bind1" -> 14),
  //       extraBindings = List(
  //         Binding(
  //           name                   = "bind1",                          // : String,
  //           className              = "Int",                            // : String = "Any",
  //           importMembers          = false,                            // : Boolean = false,
  //           defaultValue           = None,                             // : Option[String] = None,
  //           kind                   = "val",                            // : String = "val",
  //           isImplicit             = false                             // : Boolean = false,
  //         )
  //       )
  //     )
  //     println(str)
  //   }
  //   catch {
  //     case e:Throwable => 
  //       val errorLine = 8
  //       val errorCol = 2
  //       val contextWindowSize = 3
  //       println("Thrown: " + e.getMessage())
  // 
  //       val fileSource = TemplateSource.fromFile("prj-openreview-front/app/views/samples/helloError.jade")
  //       println("fileSource= "+fileSource)
  //       // val sdf  = eng.load("prj-openreview-front/app/views/samples/helloError.jade")
  //       val sdf = eng.source(tsrc)
  // 
  //       println("sdf= "+sdf)
  //       //val src = sdf.toFile.getOrElse(sys.error("no file for template"))
  //       // println("src= "+src)
  //       val helper = new ConsoleHelper(eng, new AttributesHashMap())
  //       val lines = helper.lines("prj-openreview-front/app/views/samples/helloError.jade", errorLine, contextWindowSize)
  //       lines.foreach{l =>
  //         if (l.line==errorLine) {
  //           val (c, pre, post) = l.splitOnCharacter(errorCol)
  //           // println(pre+c+post)
  //           val linePre = l.line+": "
  //           println(l.line+": "+l.source)
  //           println((" "*linePre.length)+(" "*pre.length())+" ^^^")
  //         } else {
  //           println(l.line+": "+l.source)
  //         }
  //       }
  // 
  //   }
  // }

  /**
    This does engine layout step-by-step, mostly so that when things go wrong 
    I can figure out what is happening with all steps spelled out
    */
  def doLayoutVeryExplicitly(eng:CustomTemplateEngine, uri:String, attributes:Map[String, Any], extraBindings: List[Binding]) {
    // Explicitly spelled out layout
    val template = eng.load(uri, extraBindings)
    println("template: "+template)
    // layout(uri, template, attributes)
    val buffer = new StringWriter()
    val out = new PrintWriter(buffer)
    // layout(uri, template, out, attributes)
    val context = eng.pubCreateRenderContext(uri, out)
    for ((key, value) <- attributes) {
      context.attributes(key) = value
    }
    println("context: "+context)
    RenderContext.using(context) {
      val source = template.source
      println("source= "+source)
      if (source != null && source.uri != null) {
        context.withUri(source.uri) {
          println("rendering...")
          template.render(context)
          val crc = context.asInstanceOf[CustomRenderContext]
          println(crc.outputString)
        }
      }
      else {
        eng.layoutStrategy.layout(template, context)
      }
    }

    val output = buffer.toString

    println(output)
  }





  // def consoleHelper(eng:CustomTemplateEngine) {
  //   val attributes: AttributeMap = new AttributesHashMap() {
  //     // update("context", DefaultRenderContext.this)
  //   }
  // 
  //   val helper = new ConsoleHelper(eng, attributes)
  // 
  //   val lines  = helper.lines("prj-openreview-front/app/views/samples/helloWorld.jade", 2, 3)
  //   println("lines: ")
  //   lines.foreach(println(_))
  //   println("==")
  // }

}

