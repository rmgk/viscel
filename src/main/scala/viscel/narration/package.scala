package viscel

import org.scalactic.{Every, Or}
import viscel.scribe.WebContent
import viscel.selection.Report

package object narration {
	/** Contents of a [[org.jsoup.nodes.Document]] as parsed by a [[Narrator]] */
	type Contents = List[WebContent] Or Every[Report]
}
