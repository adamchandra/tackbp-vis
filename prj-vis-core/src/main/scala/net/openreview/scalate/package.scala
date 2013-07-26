package net.openreview

package object scalate {
  import org.apache.commons.io.{FilenameUtils=>FNU}
  import org.apache.commons.io.FileUtils

  type JFile = java.io.File

  def file(s:String) = new JFile(s)

  implicit def stringToSuper(in: String): SuperString = Helpers.stringToSuper(in)

  implicit def listStringToSuper(in: List[String]): SuperListString = Helpers.listStringToSuper(in)

  // Ensure directories end with "/" and use unix sep
  def normalizeFilename(f: JFile): String = {
    if (f.isDirectory) FNU.normalizeNoEndSeparator(f.getPath, /*unixSeparator=*/true) + "/"
    else               FNU.normalize(f.getPath, /*unixSeparator=*/true)
  }

  def relativize(base: JFile, file: JFile): Option[String] = {
    val baseNormal = normalizeFilename(base)
    val fileNormal = normalizeFilename(file)

		if(fileNormal.startsWith(baseNormal))
			Some(fileNormal.substring(baseNormal.length))
		else
      None
	}


  def tree(root: JFile, skipHidden: Boolean = false): Stream[JFile] =
    if (!root.exists || (skipHidden && root.isHidden)) Stream.empty
    else root #:: (
      root.listFiles match {
        case null => Stream.empty
        case files => files.toStream.flatMap(tree(_, skipHidden))
      })

}
