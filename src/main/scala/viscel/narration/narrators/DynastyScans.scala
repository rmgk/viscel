package viscel.narration.narrators

import org.jsoup.helper.StringUtil
import viscel.narration.Narrator
import viscel.narration.Queries.queryChapterArchive
import viscel.narration.Templates.archivePage
import viscel.scribe.ImageRef
import viscel.selection.ReportTools.extract
import viscel.selection.Selection

import scala.util.matching.Regex

object DynastyScans {

  val pages: Regex = """/system/releases/\d+/\d+/\d+/[^"]+""".r

  val Citrus: Narrator = archivePage("DS_citrus", "Citrus", "https://dynasty-scans.com/series/citrus",
    queryChapterArchive(s".chapter-list a.name:not(:contains(love panic))"),
    Selection.unique("body > script").wrapFlat { script =>
      extract {
        pages.findAllIn(script.html()).map { url =>
          ImageRef(StringUtil.resolve(script.ownerDocument().baseUri(), url), script.ownerDocument().location())
        }.toList
      }
    }
  )

}
