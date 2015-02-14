package visceljs

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import viscel.shared.Description
import visceljs.Definitions.{path_asset, path_front, path_main}
import visceljs.render.{Front, Index, View}

import scala.Predef.$conforms
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scalatags.JsDom.all.{Modifier, bindJsAnyLike, onclick}


object Actions {

	def dispatchPath(path: String): Unit = {
		val paths = List(path.split("/"): _*)
		paths match {
			case Nil | "" :: Nil =>
				setBodyIndex()
			case id :: Nil =>
				for (nar <- Viscel.descriptions) {
					setBodyFront(nar(id))
				}
			case id :: posS :: Nil =>
				val pos = Integer.parseInt(posS)
				for {
					nars <- Viscel.descriptions
					nar = nars(id)
					content <- Viscel.content(nar)
					bm <- Viscel.bookmarks
				} {
					setBodyView(Data(nar, content, bm.getOrElse(nar.id, 0)).move(_.first.next(pos - 1)))
				}
			case _ => setBodyIndex()
		}
	}


	def pushIndex(): Unit = dom.history.pushState("", "main", path_main)
	def pushFront(nar: Description): Unit = dom.history.pushState("", "front", path_front(nar))
	def pushView(data: Data): Unit = dom.history.pushState("", "view", path_asset(data))

	def onLeftClick(a: => Unit): Modifier = onclick := { (e: MouseEvent) =>
		if (e.button == 0) {
			e.preventDefault()
			a
		}
	}

	def gotoIndex(): Unit = {
		pushIndex()
		setBodyIndex()
	}

	def gotoFront(nar: Description): Unit = {
		pushFront(nar)
		setBodyFront(nar)
		Viscel.hint(nar)
	}

	def gotoView(data: Data): Unit = {
		pushView(data)
		setBodyView(data)
	}


	def setBodyIndex() = {
		for (bm <- Viscel.bookmarks; nar <- Viscel.descriptions) { Viscel.setBody(Index.gen(bm, nar)) }
	}

	def setBodyFront(nar: Description): Unit = {
		for (bm <- Viscel.bookmarks; content <- Viscel.content(nar))
			Viscel.setBody(Front.gen(Data(nar, content, bm.getOrElse(nar.id, 0))))
	}

	def setBodyView(data: Data): Unit = {
		Viscel.setBody(View.gen(data))
	}


}
