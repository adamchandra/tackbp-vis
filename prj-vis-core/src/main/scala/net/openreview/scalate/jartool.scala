package net.openreview.scalate

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream;
import java.io.File
import java.util.jar._
import scalaz.Validation, Validation._, scalaz.syntax.validation._

object jartool {
  import java.util.jar.Attributes.Name
  import scalaz.effect.IO
  import scalaz.std.effect.inputStream.inputStreamResource
  import scalaz.std.effect.outputStream.outputStreamResource
  import org.apache.commons.io.IOUtils
  import org.apache.commons.io.{FilenameUtils => FNU}
  import org.apache.commons.io.{FileUtils => FU}


  case class JarBuilder(
    file: File,
    contents: Seq[(File, String)] = Seq()
  ) {
    def addFile(f: File, path: String) = this.copy(
      contents =  contents ++ Seq((f, path))
    )

    def toJar(): File = {
      FU.deleteQuietly(file)
      println("creating jar from files: " + contents.mkString(", "))
      val manifest = createManifest()

      val jarOs = new JarOutputStream(new FileOutputStream(file), manifest)

      try {
        contents.foreach{ case (f, p) => 
          addFileToJar(f, p, jarOs)
        }
      } catch {
        case e => println(e.getMessage())
      } finally {
        jarOs.close();
      }
      file
    }

    def addFileToJar(source: File, path: String, target: JarOutputStream): Validation[String, Unit] = {
      try {
        val name = normalizeFilename(source)
        println("adding file/path: " + source +" -> "+path)
        val entry = new JarEntry(path);
        entry.setTime(source.lastModified());
        target.putNextEntry(entry);

        if (source.isFile) {
          IO {
            new BufferedInputStream(new FileInputStream(source))
          }.using { (in: InputStream) =>
            IO {IOUtils.copy(in, target)}
          }(inputStreamResource) unsafePerformIO

        }
        target.closeEntry();
        ().success
      } catch {
        case e =>
          e.getMessage().failure
      }
    }
    
  }

  def createManifest(properties: (Name, String)*): Manifest = {
    val m = new Manifest()
    val attrs = m.getMainAttributes()
    attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0")

    properties.foreach { case (k, v) => 
      attrs.put(k, v)
    }
    m
  }



}


