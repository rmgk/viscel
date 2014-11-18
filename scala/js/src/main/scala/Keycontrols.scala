import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.{JSExportNamed, JSExport}
import org.scalajs.dom
import dom.document
import Predef.any2ArrowAssoc

@JSExport
object Keycontrols {

	@JSExport var up: String = ""
	@JSExport var down: String = ""
	@JSExport var next: String = ""
	@JSExport var prev: String = ""

	def bindings = List(13, 87, 77).map(_ -> up) :::
		List(40, 83, 66, 78).map( _ -> down) :::
		List(37, 65, 188).map( _ -> prev) :::
		List(39, 68, 190).map( _ -> next)

	@JSExport
	def addKeyhandlers(): Unit = {
		val map = bindings.toMap
		document.onkeydown = (ev: dom.KeyboardEvent) => {
			val target = map.getOrElse(ev.keyCode, "")
			if (!target.isEmpty && !ev.altKey && !ev.ctrlKey && !ev.shiftKey) {
				ev.preventDefault()
				document.location.assign(target)
				false
			}
			else true
		}
	}

}
