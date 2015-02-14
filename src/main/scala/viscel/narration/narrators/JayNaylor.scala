package viscel.narration.narrators

import viscel.compat.v1.Story.More.Page
import viscel.compat.v1.{TemplatesV1, SelectUtilV1, SelectionV1, ViscelUrl}
import SelectUtilV1.{elementIntoChapterPointer, queryImages, stringToVurl}


object JayNaylor {
	def common(id: String, name: String, archiveUri: ViscelUrl) = TemplatesV1.AP(id, name, archiveUri,
		doc => SelectionV1(doc).many("#chapters li > a").wrapFlat { elementIntoChapterPointer(Page) },
		queryImages("#comicentry .content img"))

	def BetterDays = common("NX_BetterDays", "Better Days", "http://jaynaylor.com/betterdays/archives/chapter-1-honest-girls/")

	def OriginalLife = common("NX_OriginalLife", "Original Life", "http://jaynaylor.com/originallife/")

}
