package visceljs

import org.scalajs.dom
import upickle._
import viscel.shared.JsonCodecs.stringMapR
import viscel.shared.Story._

import scala.collection.immutable.Map
import scala.Predef.any2ArrowAssoc
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.Frag
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.div


@JSExport(name = "Viscel")
object Viscel {

	def ajax[R: Reader](path: String): Future[R] = dom.extensions.Ajax.get(
		url = path
	).map{ res => upickle.read[R](res.responseText) }

	implicit val readAssets: Reader[List[Asset]] = Predef.implicitly[Reader[List[Asset]]]

	var bookmarks: Future[Map[String, Int]] = ajax[Map[String, Int]]("/bookmarks")
	def narrations: Future[List[Narration]] = ajax[List[Narration]]("/narrations")
	def completeNarration(nar: Narration): Future[Narration] =
		if (nar.narrates.prev(1).get.isDefined) Future.successful(nar)
		else ajax[Narration](s"/narration/${nar.id}")
	def setBookmark(nar: Narration, pos: Int): Future[Map[String, Int]] = {
		val res = dom.extensions.Ajax.post("/bookmarks", s"narration=${nar.id}&bookmark=$pos", headers = List("Content-Type" -> "application/x-www-form-urlencoded; charset=UTF-8"))
			.map(res => upickle.read[Map[String, Int]](res.responseText))
		bookmarks = res
		res
	}


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
