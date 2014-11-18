import org.scalajs.{dom => jsdom}
import scala.Predef.any2ArrowAssoc
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._

@JSExport
object Keycontrols {

	// workaround because intellijs package object import logic fails me
	object dom extends org.scalajs.dom.Window with scalajs.js.GlobalScope
	import dom.document

	@JSExport var up: String = ""
	@JSExport var down: String = ""
	@JSExport var next: String = ""
	@JSExport var prev: String = ""

	def bindings = List(13, 87, 77).map(_ -> up) :::
		List(40, 83, 66, 78).map(_ -> down) :::
		List(37, 65, 188).map(_ -> prev) :::
		List(39, 68, 190).map(_ -> next)

	@JSExport
	def addKeyhandlers(): Unit = {
		val map = bindings.toMap
		val handler = (ev: jsdom.KeyboardEvent) => {
			val target = map.getOrElse(ev.keyCode, "")
			if (!target.isEmpty && !ev.altKey && !ev.ctrlKey && !ev.shiftKey) {
				ev.preventDefault()
				document.location.assign(target)
				false
			}
			else true
		}
		document.onkeydown = handler
	}

}
