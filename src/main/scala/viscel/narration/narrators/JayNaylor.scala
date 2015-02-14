package viscel.narration.narrators

import viscel.compat.v1.ViscelUrl
import viscel.narration.SelectUtil.{elementIntoChapterPointer, queryImages, stringToVurl}
import viscel.narration.{Selection, Templates}
import viscel.compat.v1.Story.More.Page


object JayNaylor {
	def common(id: String, name: String, archiveUri: ViscelUrl) = Templates.AP(id, name, archiveUri,
		doc => Selection(doc).many("#chapters li > a").wrapFlat { elementIntoChapterPointer(Page) },
		queryImages("#comicentry .content img"))

	def BetterDays = common("NX_BetterDays", "Better Days", "http://jaynaylor.com/betterdays/archives/chapter-1-honest-girls/")

	def OriginalLife = common("NX_OriginalLife", "Original Life", "http://jaynaylor.com/originallife/")

}
