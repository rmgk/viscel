package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation.withGood
import viscel.compat.v1.{NarratorV1, SelectUtilV1, SelectionV1, Story}
import viscel.compat.v1.Story.More
import viscel.compat.v1.Story.More.{Kind, Unused}
import SelectUtilV1.{extract, imgIntoAsset, storyFromOr, stringToVurl}

object UnlikeMinerva extends NarratorV1 {
	override def id: String = "NX_UnlikeMinerva"
	override def name: String = "Unlike Minerva"
	override def archive: List[Story] = Range.inclusive(1, 25).map(i => More(s"http://www.unlikeminerva.com/archive/phase1.php?week=$i", Unused)).toList :::
		Range.inclusive(26, 130).map(i => More(s"http://www.unlikeminerva.com/archive/index.php?week=$i", Unused)).toList
	override def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(
		SelectionV1(doc).many("center > img[src~=http://www.unlikeminerva.com/archive/]").wrapEach { img =>
			withGood(imgIntoAsset(img), extract(img.parent().nextElementSibling().text())) { (a, txt) =>
				a.updateMeta(_.updated("longcomment", txt))
			}
		})
}
