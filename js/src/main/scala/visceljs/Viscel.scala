package visceljs

import org.scalajs.dom
import org.scalajs.dom.Event
import upickle._
import viscel.shared.JsonCodecs.stringMapR
import viscel.shared.Story._

import scala.Predef.ArrowAssoc
import scala.collection.immutable.Map
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.div


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

	def hint(nar: Narration): Unit = dom.extensions.Ajax.post(s"/hint/narrator/${ nar.id }")

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

	def toggleFullscreen(): Unit = {
		val doc = scalajs.js.Dynamic.global.document
		val de = doc.documentElement

		def getDefined[T](ts: T*): Option[T] = ts.find(v => v != null && !scalajs.js.isUndefined(v))

		getDefined(doc.fullscreenElement,
			doc.webkitFullscreenElement,
			doc.mozFullScreenElement,
			doc.msFullscreenElement) match {
			case None =>
				de.webkitRequestFullscreen()
				getDefined(
					de.requestFullscreen,
					de.msRequestFullscreen,
					de.mozRequestFullScreen,
					de.webkitRequestFullscreen).foreach(_.call(de))
			case Some(e) =>
				getDefined(
					doc.exitFullscreen,
					doc.webkitExitFullscreen,
					doc.mozCancelFullScreen,
					doc.msExitFullscreen).foreach { _.call(doc) }
		}
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
					narration(nrs(id)).flatMap { case _ => /*elm.innerHTML = s"$id … Done";*/ go(rest) }
			}
			go(bm.keys.toList)
		} onComplete {
			case _ if dom.location.hash.substring(1).isEmpty => Actions.dispatchPath(dom.location.hash.substring(1))
			case _ =>
		}

		Actions.dispatchPath(dom.location.hash.substring(1))


	}


}
