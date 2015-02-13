package viscel.crawl.narration

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.crawl.narration.SelectUtil._


object Twokinds extends Narrator {

	def archive = More("http://twokinds.keenspot.com/?p=archive", Volatile) :: More("http://twokinds.keenspot.com/index.php", data = List("index")) :: Nil

	def id: String = "NX_Twokinds"

	def name: String = "Twokinds"

	def chapter(s: String): Asset = Asset(None, None, data = List("Chapter", s))

	def wrapArchive(doc: Document): List[Story] Or Every[ErrorMessage] = {
		Selection(doc).many(".archive .chapter").wrapFlat { chap =>
			val title_? = Selection(chap).unique("h4").getOne.map(_.ownText())
			val links_? = Selection(chap).many("a").wrapEach { elementIntoPointer }
			withGood(title_?, links_?) { (title, links) =>
				chapter(title) :: links.take(1)
			}
		}
	}

	def wrap(doc: Document, kind: More): List[Story] Or Every[Problem] = kind match {
		case More(_, Volatile, _) => wrapArchive(doc).badMap(_.map(Problem.apply))
		case More(_, _, List("index")) => Selection(doc).many("#content .comic img[src~=images/\\d+]").wrapEach { imgIntoAsset }.badMap(_.map(Problem.apply))
		case _ => Selection(doc).unique("#cg_img img").wrapEach { imgIntoAsset }.badMap(_.map(Problem.apply))
	}
}
