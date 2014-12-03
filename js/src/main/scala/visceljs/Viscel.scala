package visceljs

import org.scalajs.dom
import org.scalajs.dom.Event
import upickle._
import viscel.shared.JsonCodecs.stringMapR
import viscel.shared.Story._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.Predef.{ArrowAssoc, implicitly}
import scala.collection.immutable.Map
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{div, p}


@JSExport(name = "Viscel")
object Viscel {

	def ajax[R: Reader](path: String): Future[R] = dom.extensions.Ajax.get(
		url = path
	).map { res => upickle.read[R](res.responseText) }

	implicit val readAssets: Reader[List[Asset]] = Predef.implicitly[Reader[List[Asset]]]

	var bookmarks: Future[Map[String, Int]] = ajax[Map[String, Int]]("/bookmarks")

	var narrations: Future[Map[String, Narration]] = ajax[List[Narration]]("/narrations").map(_.map(n => n.id -> n).toMap)

	def narration(nar: Narration): Future[Narration] =
		if (!nar.narrates.isEmpty) Future.successful(nar)
		else {
			narrations = narrations.flatMap {
				case store if store(nar.id).narrates.isEmpty => ajax[Narration](s"/narration/${ nar.id }").map(store.updated(nar.id, _))
				case store => Future.successful(store)
			}
			narrations.map(_(nar.id))
		}


	def postBookmark(nar: Narration, pos: Int): Future[Map[String, Int]] = {
		val res = dom.extensions.Ajax.post("/bookmarks", s"narration=${ nar.id }&bookmark=$pos", headers = List("Content-Type" -> "application/x-www-form-urlencoded; charset=UTF-8"))
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
		dom.window.scrollTo(0, 0)
	}

	@JSExport(name = "main")
	def main(): Unit = {

		dom.onhashchange = { (ev: Event) =>
			Actions.dispatchPath(dom.location.hash.substring(1))
		}

		setBody(Body(frag = div("loading bookmarked …")))

		for (bm <- bookmarks; nrs <- narrations) yield {
			def go(ids: List[String]): Future[Unit] = ids match {
				case Nil => Future.successful(Unit)
				case id :: rest =>
					//val elm = p(s"$id …").render
					//dom.document.body.appendChild(elm)
					narration(nrs(id)).flatMap { case _ => /*elm.innerHTML = s"$id … Done";*/ go(rest)}
			}
			go(bm.keys.toList)
		} onComplete  { _ => Actions.dispatchPath(dom.location.hash.substring(1)) }

		Actions.dispatchPath(dom.location.hash.substring(1))




	}


}
