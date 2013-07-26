package net.openreview.scalate

import org.fusesource.scalate.util._
import com.weiglewilczek.slf4s.Logging


object ResourceLoaders extends Logging {
  def classpathResource(classloader: ClassLoader, prefix:String, uri: String): Option[Resource] = {
    logger.debug("Trying to load classpath uri: " + uri)
    val relativeUri = prefix+"/"+uri.stripPrefix("/")
    val res = Resource.fromFile(classloader.getResource(relativeUri).getFile())
    if (res != null) Some(res)
    else None
  }

  def fileResource(sourceDirectories: Traversable[JFile], uri: String): Option[Resource] = {
    logger.debug("Trying to load file uri: " + uri)

    def toFile(uri: String): JFile = {
      sourceDirectories.view.map(new JFile(_, uri)).find(_.exists) match {
        case Some(file) => file
        case _ => new JFile(uri)
      }
    }

    val file = toFile(uri)
    if (file != null && file.exists && file.isFile) {
      if (!file.canRead) {
        throw new ResourceNotFoundException(uri, description = "Could not read from " + file.getAbsolutePath)
      }
      Some(Resource.fromFile(file))
    } else None
  }


}

// TODO: make a version that can read from any type of backend storage (mongodb in particular)
case class ClasspathResourceLoader(classloader: ClassLoader, prefixPath: String = "scalate") extends ResourceLoader with Logging {
  def resource(uri: String): Option[Resource] = 
    ResourceLoaders.classpathResource(classloader, prefixPath, uri)
}

case class CustomResourceLoader(sourceDirectories: Traversable[JFile] = None, classloader: ClassLoader, resourcePrefix: String = "scalate") extends ResourceLoader with Logging {
  import ResourceLoaders._

  def resource(uri: String): Option[Resource] = {
    try {
      fileResource(sourceDirectories, uri).orElse(
        classpathResource(classloader, resourcePrefix, uri))
    } catch {
      case e => None
    }
  }

}

/// The default scalate core version of resource loader
// case class FilesysResourceLoader(sourceDirectories: Traversable[File] = None) extends ResourceLoader {
//   def resource(uri: String): Option[Resource] = {
//     debug("Trying to load uri: " + uri)
// 
//     var answer = false
//     if (uri != null) {
//       val file = toFile(uri)
//       if (file != null && file.exists && file.isFile) {
//         if (!file.canRead) {
//           throw new ResourceNotFoundException(uri, description = "Could not read from " + file.getAbsolutePath)
//         }
//         return Some(fromFile(file))
//       }
// 
//       // lets try the ClassLoader
//       val relativeUri = uri.stripPrefix("/")
//       var url = Thread.currentThread.getContextClassLoader.getResource(relativeUri)
//       if (url == null) {
//         url = getClass.getClassLoader.getResource(relativeUri)
//       }
//       if (url != null) {
//         return Some(fromURL(url))
//       }
//     }
//     None
//   }
// 
//   protected def toFile(uri: String): File = {
//     sourceDirectories.view.map(new File(_, uri)).find(_.exists) match {
//       case Some(file) => file
//       case _ => new File(uri)
//     }
//   }
// }
