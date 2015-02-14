package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation.withGood
import viscel.narration.SelectUtil.{extract, imgIntoAsset, storyFromOr, stringToVurl}
import viscel.narration.{NarratorV1, Selection}
import viscel.shared.Story
import viscel.shared.Story.More
import viscel.shared.Story.More.{Kind, Unused}

object UnlikeMinerva extends NarratorV1 {
	override def id: String = "NX_UnlikeMinerva"
	override def name: String = "Unlike Minerva"
	override def archive: List[Story] = Range.inclusive(1, 25).map(i => More(s"http://www.unlikeminerva.com/archive/phase1.php?week=$i", Unused)).toList :::
		Range.inclusive(26, 130).map(i => More(s"http://www.unlikeminerva.com/archive/index.php?week=$i", Unused)).toList
	override def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(
		Selection(doc).many("center > img[src~=http://www.unlikeminerva.com/archive/]").wrapEach { img =>
			withGood(imgIntoAsset(img), extract(img.parent().nextElementSibling().text())) { (a, txt) =>
				a.updateMeta(_.updated("longcomment", txt))
			}
		})
}
