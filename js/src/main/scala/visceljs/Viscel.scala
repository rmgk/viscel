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

	var offlineMode = false

	def ajax[R: Reader](path: String): Future[R] =
		if (offlineMode) Future.failed(new Throwable("offline mode"))
		else {
			val res = dom.extensions.Ajax.get(url = path)
				.map { res => upickle.read[R](res.responseText) }
			res.onFailure {
				case e => Console.println(s"request $path failed with $e")
			}
			res
		}

	implicit val readAssets: Reader[List[Asset]] = Predef.implicitly[Reader[List[Asset]]]

	var bookmarks: Future[Map[String, Int]] = _

	var narrations: Future[Map[String, Narration]] = _

	def narration(nar: Narration): Future[Narration] =
		if (!nar.narrates.isEmpty) Future.successful(nar)
		else {
			narrations = narrations.flatMap {
				case store if store(nar.id).narrates.isEmpty => ajax[Narration](s"narration/${ nar.id }").map(store.updated(nar.id, _))
				case store => Future.successful(store)
			}
			narrations.map(_(nar.id))
		}

	def hint(nar: Narration): Unit = dom.extensions.Ajax.post(s"hint/narrator/${ nar.id }")

	def postBookmark(nar: Narration, pos: Int): Future[Map[String, Int]] = {
		val res = dom.extensions.Ajax.post("bookmarks", s"narration=${ nar.id }&bookmark=$pos", headers = List("Content-Type" -> "application/x-www-form-urlencoded; charset=UTF-8"))
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

		bookmarks = ajax[Map[String, Int]]("bookmarks")
		narrations = ajax[List[Narration]]("narrations").map(_.map(n => n.id -> n).toMap)

		setBody(Body(frag = div("loading data …")))

		Actions.dispatchPath(dom.location.hash.substring(1))


	}

	@JSExport(name = "spore")
	def spore(id: String, narationJson: String): Unit = {

		offlineMode = true

		dom.onhashchange = { (ev: Event) =>
			Actions.dispatchPath(dom.location.hash.substring(1))
		}

		bookmarks = Future.successful(Map())
		narrations = Future.successful(Map(id -> upickle.read[Narration](narationJson)))

		setBody(Body(frag = div("loading data …")))

		Actions.dispatchPath(id)


	}


}
