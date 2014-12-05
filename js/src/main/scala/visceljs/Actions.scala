package visceljs

import org.scalajs.dom
import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}
import visceljs.Definitions.{path_asset, path_front, path_main}
import visceljs.render.{Front, Index, View}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow


object Actions {

	def dispatchPath(path: String): Unit = {
		val paths = List(path.split("/"): _*)
		paths match {
			case Nil | "" :: Nil =>
				setBodyIndex()
			case id :: Nil =>
				for (nar <- Viscel.narrations) {
					setBodyFront(nar(id))
				}
			case id :: posS :: Nil =>
				val pos = Integer.parseInt(posS)
				for {
					nars <- Viscel.narrations
					nar = nars(id)
					fullNarration <- Viscel.narration(nar)
				} {
					setBodyView(fullNarration.narrates.first.next(pos - 1), nar)
				}
			case _ => setBodyIndex()
		}
	}


	def pushIndex(): Unit = dom.history.pushState("", "main", path_main)
	def pushFront(nar: Narration): Unit = dom.history.pushState("", "front", path_front(nar))
	def pushView(gallery: Gallery[Asset], nar: Narration): Unit = dom.history.pushState("", "view", path_asset(nar, gallery))


	def gotoIndex(): Unit = {
		pushIndex()
		setBodyIndex()
	}

	def gotoFront(nar: Narration): Unit = {
		pushFront(nar)
		setBodyFront(nar)
	}

	def gotoView(gallery: Gallery[Asset], nar: Narration): Unit = {
		pushView(gallery, nar)
		setBodyView(gallery, nar)
	}


	def setBodyIndex() = {
		for (bm <- Viscel.bookmarks; nar <- Viscel.narrations) { Viscel.setBody(Index.gen(bm, nar)) }
	}

	def setBodyFront(nar: Narration): Unit = {
		for (bm <- Viscel.bookmarks; fullNarration <- Viscel.narration(nar))
			Viscel.setBody(Front.gen(bm.getOrElse(nar.id, 0), fullNarration))
	}

	def setBodyView(gallery: Gallery[Asset], nar: Narration): Unit = {
		Viscel.setBody(View.gen(gallery, nar))
	}


}