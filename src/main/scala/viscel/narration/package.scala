package viscel

import org.scalactic.{Every, Or}
import viscel.selection.Report
import viscel.store.WebContent

package object narration {
  /** Contents of a [[org.jsoup.nodes.Document]] as parsed by a [[Narrator]] */
  type Contents = List[WebContent] Or Every[Report]
}
