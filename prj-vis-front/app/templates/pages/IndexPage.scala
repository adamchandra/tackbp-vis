package pages

import edu.umass.cs.iesl.tackbp.vis.admin._

case class IndexPage(
  val documents: Seq[Document],
  val searchResults: AnyRef // either SearchResults or MongoResults
) {

}
