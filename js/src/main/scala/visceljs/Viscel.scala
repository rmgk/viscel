package visceljs

import org.scalajs.dom
import org.scalajs.dom.{KeyboardEvent, Event}
import upickle._
import viscel.shared.JsonCodecs.stringMapR
import viscel.shared.Story._

import scala.collection.immutable.Map
import scala.Predef.any2ArrowAssoc
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.Frag
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{div, body}


@JSExport(name = "Viscel")
object Viscel {

	def ajax[R: Reader](path: String): Future[R] = dom.extensions.Ajax.get(
		url = path
	).map{ res => upickle.read[R](res.responseText) }

	implicit val readAssets: Reader[List[Asset]] = Predef.implicitly[Reader[List[Asset]]]

	var bookmarks: Future[Map[String, Int]] = ajax[Map[String, Int]]("/bookmarks")
	def narrations: Future[Map[String, Narration]] = ajax[List[Narration]]("/narrations").map(_.map(n => n.id -> n).toMap)
	def completeNarration(nar: Narration): Future[Narration] =
		if (nar.narrates.prev(1).get.isDefined) Future.successful(nar)
		else ajax[Narration](s"/narration/${nar.id}")
	def setBookmark(nar: Narration, pos: Int): Future[Map[String, Int]] = {
		val res = dom.extensions.Ajax.post("/bookmarks", s"narration=${nar.id}&bookmark=$pos", headers = List("Content-Type" -> "application/x-www-form-urlencoded; charset=UTF-8"))
			.map(res => upickle.read[Map[String, Int]](res.responseText))
		bookmarks = res
		res
	}


	def setBody(abody: Body): Unit = {
		dom.document.onkeydown = abody.keypress
		dom.document.body.innerHTML = ""
		dom.document.title = abody.title
		dom.document.body.setAttribute("id", abody.id)
		dom.document.body.appendChild(abody.frag.render)
	}

	@JSExport
	def main(): Unit = {

		dom.onhashchange = { (ev: Event) =>
			Util.dispatchPath(dom.location.hash.substring(1))
		}

		setBody(Body(frag = div("loading")))

		Util.dispatchPath(dom.location.hash.substring(1))

	}



}
