package visceljs

import org.scalajs.dom.{Element, Event}
import viscel.shared.Story
import viscel.shared.Story._
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.document
import scala.Predef.any2ArrowAssoc
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.Dynamic.global
import scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.concurrent.Future
import scalatags.JsDom.attrs.id
import scalatags.JsDom.short.HtmlTag
import scalatags.JsDom.implicits.{stringFrag, stringAttr}
import scalatags.JsDom.tags.{div, body}
import scalatags.JsDom.Frag
import scala.collection.immutable.Map
import upickle._

@JSExport(name = "Viscel")
object Viscel {


	import visceljs.Util._

	def ajax[R: Reader](path: String): Future[R] = dom.extensions.Ajax.get(
		url = path
	).map{ res => upickle.read[R](res.responseText) }

	implicit val readAssets: Reader[List[Asset]] = Predef.implicitly[Reader[List[Asset]]]

	val bookmarks: Future[Map[String, Int]] = ajax[Map[String, Int]]("/bookmarks")
	def narrations: Future[List[Narration]] = ajax[List[Narration]]("/narrations")
	def completeNarration(nar: Narration): Future[Narration] = ajax[Narration](s"/narration/${nar.id}")


	def setBody(id: String, fragment: Frag): Unit = {
		dom.document.body.innerHTML = ""
		dom.document.body.setAttribute("id", id)
		dom.document.body.appendChild(fragment.render)

	}

	@JSExport
	def main(): Unit = {

		setBody("index", div("loading"))
		
		for (bm <- bookmarks; nar <- narrations) { setBody("index", IndexPage.genIndex(bm, nar)) }
	}



}
