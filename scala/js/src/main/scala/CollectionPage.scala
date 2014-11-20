import org.scalajs.dom.{Element, Event}
import scala.scalajs.js
import org.scalajs.{dom => jsdom}
import org.scalajs.dom
import scala.Predef.any2ArrowAssoc
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._
import scala.scalajs.js.Dynamic.global
import scalajs.concurrent.JSExecutionContext.Implicits.runNow


@JSExport
object CollectionPage {

	import HtmlPageUtils._

	// workaround because intellijs package object import logic fails me
	//object dom extends org.scalajs.dom.Window with scalajs.js.GlobalScope
	import dom.document


	@JSExport var up: String = "↑"
	@JSExport var down: String = "↓"
	@JSExport var next: String = "→"
	@JSExport var prev: String = "←"

	@JSExport var assets: js.Array[String] = null

//	{
//		val children = document.getElementById("element_list").children
//		(for (i <- Range(0, children.length)) yield {
//			children.apply(i).asInstanceOf[Element].getAttribute("blob")
//		}).toArray
//	}

	def getPos(): Int = global.parseInt(document.location.hash.substring(1)).asInstanceOf[Int]

	var pos: Int = _

	def bindings = List(13, 87, 77).map(_ -> up) :::
		List(40, 83, 66, 78).map(_ -> down) :::
		List(37, 65, 188).map(_ -> prev) :::
		List(39, 68, 190).map(_ -> next)

	def bodyId = "front"

	def content: Tag = body(id := bodyId)(
		div(class_main)(mainPart: _*),
		div(class_navigation)(navigation: _*),
		div(class_side)(sidePart: _*))

	def mainPart = div(class_info)(
		make_table(
			"id" -> "ID",
			"name" -> "NAME" //"chapter" -> collection.size.toString,
			//"pages" -> collection.totalSize.toString
		)) :: Nil

	def navigation = Seq(
		link_main("index"),
		stringFrag(" – "),
		stringFrag("FIRST"),
		stringFrag(" – "))

	def sidePart = Seq[Frag](
		div(class_content))

	def ajax() =     dom.extensions.Ajax.post(
		url = "/someurl",
		data = "some_data"
	).map(_.responseText)

	@JSExport
	def main(): Unit = {
		val map = bindings.toMap

		val mainImg = img(class_element).render
		val preload = new Image()
		document.body.appendChild(div(class_content)(mainImg).render)

		def updatePosition(p: Int) = {
			pos = p
			document.location.hash = pos.toString
			mainImg.setAttribute("src", path_blob(assets(pos)))
			preload.src = path_blob(assets(pos + 1))
		}

		updatePosition(getPos())

		val handleKeypress = (ev: jsdom.KeyboardEvent) => {
			val target = map.getOrElse(ev.keyCode, "")
			if (!target.isEmpty && !ev.altKey && !ev.ctrlKey && !ev.shiftKey) {
				ev.preventDefault()
				target match {
					case "→" => updatePosition(pos + 1)
					case "←" => updatePosition(pos - 1)
					case _ =>
				}
				false
			}
			else true
		}
		document.onkeydown = handleKeypress

		dom.window.onhashchange = (e: Event) => updatePosition(getPos())
	}

}
