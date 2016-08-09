package visceljs

import org.scalajs.dom
import org.scalajs.dom.raw.HashChangeEvent
import upickle.default.{Reader, Writer}
import viscel.shared._

import scala.collection.immutable.Map
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils.encodeURIComponent
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.div
import rescala.engines.Engines.default
import rescala.engines.Engines.default.{Var, Evt, Signal}


@JSExport(name = "Viscel")
object Viscel {

	var offlineMode = false

	def ajax[R: Reader](path: String): Future[R] =
		if (offlineMode) Future.failed(new Throwable("offline mode"))
		else {
			val res = dom.ext.Ajax.get(url = path)
				.map { res => upickle.default.read[R](res.responseText) }
			res.onFailure {
				case e => Console.println(s"request $path failed with ${e.getMessage}")
			}
			res
		}

	implicit val readAssets: Reader[List[ImageRef]] = Predef.implicitly[Reader[List[ImageRef]]]

	var bookmarks: Future[Map[String, Int]] = _

	var descriptions: Future[Map[String, Description]] = _
	var contents: Map[String, Future[Contents]] = Map()

	val triggerDispatch: Evt[Unit] = Evt[Unit]()
	triggerDispatch.observe(_ => Actions.dispatchPath(dom.window.location.hash.substring(1)))
	dom.window.onhashchange = { (ev: HashChangeEvent) =>
		triggerDispatch(())
	}

	def content(nar: Description): Future[Contents] = contents.getOrElse(nar.id, {
		val res = ajax[Contents](s"narration/${encodeURIComponent(nar.id)}")
		contents = contents.updated(nar.id, res)
		res
	})


	def hint(nar: Description, force: Boolean = false): Unit = dom.ext.Ajax.post(s"hint/narrator/${encodeURIComponent(nar.id)}" + (if (force) "?force=true" else ""))

	def postBookmark(nar: Description, pos: Int): Future[Map[String, Int]] = {

		val res = dom.ext.Ajax.post("bookmarks", s"narration=${encodeURIComponent(nar.id)}&bookmark=$pos", headers = Map("Content-Type" -> "application/x-www-form-urlencoded; charset=UTF-8"))
			.map(res => upickle.default.read[Map[String, Int]](res.responseText))
		bookmarks = res
		res
	}


	def setBody(abody: Body, scrolltop: Boolean): Unit = {
		dom.document.onkeydown = abody.keypress
		dom.document.body.innerHTML = ""
		dom.document.title = abody.title
		dom.document.body.setAttribute("id", abody.id)
		dom.document.body.appendChild(abody.frag.render)
		if (scrolltop) Actions.scrollTop()
	}

	def toggleFullscreen(): Unit = {
		val doc = scalajs.js.Dynamic.global.document
		val de = doc.documentElement

		def getDefined[T](ts: T*): Option[T] = ts.find(v => v != null && !scalajs.js.isUndefined(v))

		def fullscreenElement = getDefined(doc.fullscreenElement,
			doc.webkitFullscreenElement,
			doc.mozFullScreenElement,
			doc.msFullscreenElement)

		def requestFullscreen = getDefined(
			de.requestFullscreen,
			de.msRequestFullscreen,
			de.mozRequestFullScreen,
			de.webkitRequestFullscreen)

		def exitFullscreen = getDefined(
			doc.exitFullscreen,
			doc.webkitExitFullscreen,
			doc.mozCancelFullScreen,
			doc.msExitFullscreen)

		fullscreenElement match {
			case None => requestFullscreen.foreach(_.call(de))
			case Some(e) => exitFullscreen.foreach(_.call(doc))
		}
	}

	@JSExport(name = "main")
	def main(): Unit = {

		bookmarks = ajax[Map[String, Int]]("bookmarks")
		descriptions = ajax[List[Description]]("narrations").map(_.map(n => n.id -> n).toMap)

		setBody(Body(frag = div("loading data …")), scrolltop = true)

		triggerDispatch(())

	}

	@JSExport(name = "spore")
	def spore(id: String, dataJson: String): Unit = {

		offlineMode = true

		bookmarks = Future.successful(Map())
		val (desc, content) = upickle.default.read[(Description, Contents)](dataJson)
		descriptions = Future.successful(Map(id -> desc))
		contents = Map(desc.id -> Future.successful(content))

		setBody(Body(frag = div("loading data …")), scrolltop = true)

		triggerDispatch(())


	}


}
