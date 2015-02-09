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
			val res = dom.ext.Ajax.get(url = path)
				.map { res => upickle.read[R](res.responseText) }
			res.onFailure {
				case e => Console.println(s"request $path failed with $e")
			}
			res
		}

	implicit val readAssets: Reader[List[Asset]] = Predef.implicitly[Reader[List[Asset]]]

	var bookmarks: Future[Map[String, Int]] = _

	var descriptions: Future[Map[String, Description]] = _
	var contents: Map[String, Future[Content]] = Map()

	def content(nar: Description): Future[Content] = contents.getOrElse(nar.id, {
		val res = ajax[Content](s"narration/${ nar.id }")
		contents = contents.updated(nar.id, res)
		res
	})


	def hint(nar: Description): Unit = dom.ext.Ajax.post(s"hint/narrator/${ nar.id }")

	def postBookmark(nar: Description, pos: Int): Future[Map[String, Int]] = {

		val res = dom.ext.Ajax.post("bookmarks", s"narration=${ nar.id }&bookmark=$pos", headers = Map("Content-Type" -> "application/x-www-form-urlencoded; charset=UTF-8"))
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
		descriptions = ajax[List[Description]]("narrations").map(_.map(n => n.id -> n).toMap)

		setBody(Body(frag = div("loading data …")))

		Actions.dispatchPath(dom.location.hash.substring(1))


	}

	@JSExport(name = "spore")
	def spore(id: String, dataJson: String): Unit = {

		offlineMode = true

		dom.onhashchange = { (ev: Event) =>
			Actions.dispatchPath(dom.location.hash.substring(1))
		}

		bookmarks = Future.successful(Map())
		val (desc, content) = upickle.read[(Description, Content)](dataJson)
		descriptions = Future.successful(Map(id -> desc))
		contents = Map(desc.id -> Future.successful(content))

		setBody(Body(frag = div("loading data …")))

		Actions.dispatchPath(id)


	}


}
