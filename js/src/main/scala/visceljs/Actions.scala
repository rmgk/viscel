package visceljs

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import rescala._
import viscel.shared.Description
import visceljs.Definitions.{path_asset, path_front, path_main}
import visceljs.render.{Front, Index, View}

import scala.scalajs.js.URIUtils.decodeURIComponent
import scalatags.JsDom.all.{Modifier, bindJsAnyLike, onclick}


object Actions {

	val viewE = Evt[View.Navigate]
	val viewDispatchS = Var.empty[(String, Int)]
	val viewDispatchChangeE = Signal {
		val nars = Viscel.descriptions()
		viewDispatchS() match {
			case (id, pos) =>
				val nar = nars.apply(id)
				val content = Viscel.content(nar): @unchecked
				val bm = Viscel.bookmarks().getOrElse(nar.id, 0)
				View.Goto(Data(nar, content(), bm).move(_.first.next(pos - 1)))
		}
	}.changed

	val frontS = Var.empty[String]
	val frontData = Signal {
		val id = frontS()
		val description = Viscel.descriptions()(id)
		val bm = Viscel.bookmarks().getOrElse(id, 0)
		val content = Viscel.content(description): @unchecked
		Data(description, content(), bm)
	}

	val bodyFront = Front.gen(frontData)
	val viewBody = View.gen(viewE || viewDispatchChangeE)
	val indexBody = Index.gen()



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
				viewDispatchS.set(nid -> pos)
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
