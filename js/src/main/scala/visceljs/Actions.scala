package visceljs

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import viscel.shared.Description
import visceljs.Definitions.{path_asset, path_front, path_main}
import visceljs.render.{Front, Index, View}

import rescala._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils.decodeURIComponent
import scalatags.JsDom.all.{Modifier, bindJsAnyLike, onclick}


object Actions {

	val viewE = Evt[View.Navigate]
	val viewBody = View.gen(viewE)
	val indexBody = Index.gen()

	val frontS = Var("")
	val frontData = reactives.Signals.dynamic(frontS, Viscel.bookmarks, Viscel.descriptions) { implicit t =>
		val id = frontS.apply(t)
		Viscel.descriptions.apply(t).get(id) match {
			case None => Data.empty()
			case Some(description) =>
				val bm = Viscel.bookmarks.apply(t).getOrElse(id, 0)
				val content = Viscel.content(description)
				Data(description, content.apply(t), bm)
		}
	}

	val bodyFront = Front.gen(frontData)



	val viewDispatchS = Var[Option[(String, Int)]](None)
	rescala.reactives.Signals.dynamic(viewDispatchS, Viscel.descriptions, Viscel.bookmarks) { implicit t =>
		val nars = Viscel.descriptions.apply(t)
		viewDispatchS.apply(t).flatMap { case (id, pos) =>
			nars.get(id).map { nar =>
				val content = Viscel.content(nar).apply(t)
				val bm = Viscel.bookmarks.apply(t).getOrElse(nar.id, 0)
				Data(nar, content, bm).move(_.first.next(pos - 1))
			}
		}
	}.observe {
		case Some(data) => viewE.fire(View.Goto(data))
		case None =>
	}

	def dispatchPath(path: String): Unit = {
		val paths = List(path.split("/"): _*)
		Console.println(s"dispatch $paths")
		paths match {
			case Nil | "" :: Nil =>
				setBodyIndex()
			case id :: Nil =>
				setBodyFront(decodeURIComponent(id))
			case id :: posS :: Nil =>
				val pos = Integer.parseInt(posS)
				val nid = decodeURIComponent(id)
				viewDispatchS.set(Some(nid -> pos))
				Viscel.setBody(viewBody, scrolltop = true)
			case _ => setBodyIndex()
		}
	}

	def scrollTop() = dom.window.scrollTo(0, 0)

	def pushIndex(): Unit = dom.window.history.pushState(null, "main", path_main)
	def pushFront(nar: Description): Unit = dom.window.history.pushState(null, "front", path_front(nar))
	def pushView(data: Data): Unit = dom.window.history.pushState(null, "view", path_asset(data))

	def onLeftClick(a: => Unit): Modifier = onclick := { (e: MouseEvent) =>
		if (e.button == 0) {
			e.preventDefault()
			a
		}
	}

	def gotoIndex(): Unit = {
		pushIndex()
		setBodyIndex(scrolltop = true)
	}

	def gotoFront(nar: Description, scrolltop: Boolean = false): Unit = {
		pushFront(nar)
		setBodyFront(nar.id, scrolltop)
		Viscel.hint(nar)
	}

	def gotoView(data: Data, scrolltop: Boolean = true): Unit = {
		pushView(data)
		setBodyView(data, scrolltop)
	}


	def setBodyIndex(scrolltop: Boolean = false) = Viscel.setBody(indexBody, scrolltop)

	def setBodyFront(descriptionid: String, scrolltop: Boolean = false): Unit = {
		frontS.set(descriptionid)
		Viscel.setBody(bodyFront, scrolltop)
	}



	def setBodyView(data: Data, scrolltop: Boolean = false): Unit = {
		viewE.fire(View.Goto(data))
		Viscel.setBody(viewBody, scrolltop)
	}


}
