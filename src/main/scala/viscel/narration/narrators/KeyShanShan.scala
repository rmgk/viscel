package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Good
import viscel.narration.Narrator
import viscel.narration.SelectUtil._
import viscel.shared.Story
import viscel.shared.Story.More.{Kind, Unused}
import viscel.shared.Story.{Chapter, More}

object KeyShanShan {
	class Common(cid: String, cname: String, url: String) extends Narrator {

		def archive = More(url, Unused) :: Nil

		def id: String = cid

		def name: String = cname

		def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr {
			val imagenext_? = queryImageInAnchor("img[src~=chapter/\\d+/\\d+", Unused)(doc)
			doc.baseUri() match {
				case rex"pageid=001&chapterid=($cid\d+)" => cons(Good(Chapter(s"Chapter $cid")), imagenext_?)
				case _ => imagenext_?
			}
		}
	}

	object Key extends Common("NX_Key", "Key", "http://key.shadilyn.com/view.php?pageid=001&chapterid=1")
	object ShanShan extends Common("NX_ShanShan", "Shan Shan", "http://shanshan.upperrealms.com/view.php?pageid=001&chapterid=1")

}
