/**
 * Copyright (C) 2009-2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.openreview.scalate

import org.fusesource.scalate.support._
import org.fusesource.scalate._
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.util._
import scala.util.parsing.input.OffsetPosition
import collection.mutable.ListBuffer
import java.io.{PrintWriter, StringWriter, File}

import com.weiglewilczek.slf4s.Logging
import util.ClassPathBuilder

object ScalaCompiler extends Logging {

  def create(engine: TemplateEngine) : ScalaCompiler = {
    Thread.currentThread.getContextClassLoader match {
      case _ => new ScalaCompiler(engine.bytecodeDirectory, engine.classpath, engine.combinedClassPath)
    }
  }

}

import ScalaCompiler._

class ScalaCompiler(bytecodeDirectory: File, classpath: String, combineClasspath: Boolean = false) extends Compiler with Logging {

  val settings = generateSettings(bytecodeDirectory, classpath, combineClasspath)

  val compiler = createCompiler(settings)

  def compile(file: File): Unit = {
    synchronized {
      val messageCollector = new StringWriter
      val messageCollectorWrapper = new PrintWriter(messageCollector)

      var messages = List[CompilerError]()
      val reporter = new ConsoleReporter(settings, Console.in, messageCollectorWrapper) {

        override def printMessage(posIn: Position, msg: String) {
          val pos = if (posIn eq null) NoPosition
                    else if (posIn.isDefined) posIn.inUltimateSource(posIn.source)
                    else posIn
          pos match {
            case FakePos(fmsg) =>
              super.printMessage(posIn, msg);
            case NoPosition =>
              super.printMessage(posIn, msg);
            case _ =>
              messages = CompilerError(posIn.source.file.file.getPath, msg, OffsetPosition(posIn.source.content, posIn.point)) :: messages
              super.printMessage(posIn, msg);
          }

        }
      }
      compiler.reporter = reporter

      // Attempt compilation
      (new compiler.Run).compile(List(file.getCanonicalPath))

      // Bail out if compilation failed
      if (reporter.hasErrors) {
        reporter.printSummary
        messageCollectorWrapper.close
        throw new CompilerException("Compilation failed:\n" +messageCollector, messages)
      }
    }
  }

  override def shutdown() = compiler.askShutdown()

  private def errorHandler(message: String): Unit = throw new TemplateException("Compilation failed:\n" + message)

  protected def generateSettings(bytecodeDirectory: File, classpath: String, combineClasspath: Boolean): Settings = {
    bytecodeDirectory.mkdirs

    val pathSeparator = File.pathSeparator

    val classPathFromClassLoader = (new ClassPathBuilder)
            .addEntry(classpath)
            .addPathFromContextClassLoader()
            .addPathFrom(classOf[Product])
            .addPathFrom(classOf[Global])
            .addPathFrom(getClass)
            .addPathFromSystemClassLoader()
            .addJavaPath()
            .classPath

    var useCP = if (classpath != null && combineClasspath) {
      classpath + pathSeparator + classPathFromClassLoader
    } else {
      classPathFromClassLoader
    }

    // def formatClassloader()

    //logger.debug("using classpath: " + useCP)
    //logger.debug("system class loader: " + ClassLoader.getSystemClassLoader)
    //logger.debug("context class loader: " + Thread.currentThread.getContextClassLoader)
    //logger.debug("scalate class loader: " + getClass.getClassLoader)

    val settings = new Settings(errorHandler)
    settings.classpath.value = useCP
    settings.outdir.value = bytecodeDirectory.toString
    settings.deprecation.value = true
    //settings.unchecked.value = true

    // from play-scalate
    settings.debuginfo.value = "vars"
    settings.dependenciesFile.value = "none"
    settings.debug.value = false

    // TODO not sure if these changes make much difference?
    //settings.make.value = "transitivenocp"
    settings
  }

  protected def createCompiler(settings: Settings): Global = {
    new Global(settings, null)
  }
}
