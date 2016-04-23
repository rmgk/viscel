package visceljs

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import viscel.shared.Description
import visceljs.Definitions.{path_asset, path_front, path_main}
import visceljs.render.{Front, Index, View}

import scala.Predef.$conforms
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils.decodeURIComponent
import scalatags.JsDom.all.{Modifier, bindJsAnyLike, onclick}


object Actions {

	def dispatchPath(path: String): Unit = {
		val paths = List(path.split("/"): _*)
		Console.println(s"dispatch $paths")
		paths match {
			case Nil | "" :: Nil =>
				setBodyIndex()
			case id :: Nil =>
				for (nar <- Viscel.descriptions) {
					setBodyFront(nar(decodeURIComponent(id)))
				}
			case id :: posS :: Nil =>
				val pos = Integer.parseInt(posS)
				for {
					nars <- Viscel.descriptions
					nar = nars(decodeURIComponent(id))
					content <- Viscel.content(nar)
					bm <- Viscel.bookmarks
				} {
					setBodyView(Data(nar, content, bm.getOrElse(nar.id, 0)).move(_.first.next(pos - 1)))
				}
			case _ => setBodyIndex()
		}
	}


	def pushIndex(): Unit = dom.history.pushState(null, "main", path_main)
	def pushFront(nar: Description): Unit = dom.history.pushState(null, "front", path_front(nar))
	def pushView(data: Data): Unit = dom.history.pushState(null, "view", path_asset(data))

	def onLeftClick(a: => Unit): Modifier = onclick := { (e: MouseEvent) =>
		if (e.button == 0) {
			e.preventDefault()
			a
		}
	}

	def onLeftClickPrevNext(node: => dom.html.Element, data: Data): Modifier = onclick := { (e: MouseEvent) =>
		if (e.button == 0) {
			e.preventDefault()
			val relx = e.clientX - node.offsetLeft
			val border = math.max(node.offsetWidth / 10, 100)
			if (relx < border) gotoView(data.prev)
			else if (!data.next.gallery.isEnd) gotoView(data.next)
		}
	}

	def gotoIndex(): Unit = {
		pushIndex()
		setBodyIndex(scrolltop = true)
	}

	def gotoFront(nar: Description, scrolltop: Boolean = false): Unit = {
		pushFront(nar)
		setBodyFront(nar, scrolltop)
		Viscel.hint(nar)
	}

	def gotoView(data: Data, scrolltop: Boolean = true): Unit = {
		pushView(data)
		setBodyView(data, scrolltop)
	}


	def setBodyIndex(scrolltop: Boolean = false) = {
		for (bm <- Viscel.bookmarks; nar <- Viscel.descriptions) {Viscel.setBody(Index.gen(bm, nar), scrolltop)}
	}

	def setBodyFront(nar: Description, scrolltop: Boolean = false): Unit = {
		for (bm <- Viscel.bookmarks; content <- Viscel.content(nar))
			Viscel.setBody(Front.gen(Data(nar, content, bm.getOrElse(nar.id, 0))), scrolltop)
	}

	def setBodyView(data: Data, scrolltop: Boolean = false): Unit = {
		Viscel.setBody(View.gen(data), scrolltop)
	}


}
