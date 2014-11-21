package visceljs

import org.scalajs.dom.{Element, Event}
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

@JSExport(name = "Viscel")
object Viscel {


	import visceljs.Util._

	def ajax(path: String): Future[js.Dynamic] = dom.extensions.Ajax.get(
		url = path
	).map{ res => js.JSON.parse(res.responseText)}

	def fetchBookmarks(): Future[js.Dictionary[Int]] = ajax("/bookmarks").asInstanceOf[Future[js.Dictionary[Int]]]
	def fetchCollections(): Future[js.Dictionary[js.Dictionary[String]]] = ajax("/collections").asInstanceOf[Future[js.Dictionary[js.Dictionary[String]]]]
	def fetchAssetList(col: String): Future[js.Array[AssetStory]] = ajax(s"/collection/$col").asInstanceOf[Future[js.Array[AssetStory]]]


	def setBody(id: String, fragment: Frag): Unit = {
		dom.document.body.innerHTML = ""
		dom.document.body.setAttribute("id", id)
		dom.document.body.appendChild(fragment.render)

	}

	@JSExport
	def main(): Unit = {

		setBody("index", div("loading"))

		val fbm = fetchBookmarks()
		val fcol = fetchCollections()
		for (bm <- fbm; col <- fcol) { setBody("index", IndexPage.genIndex(bm, col)) }
	}



}
