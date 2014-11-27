package visceljs

import org.scalajs.dom.KeyboardEvent

import scala.scalajs.js
import scalatags.JsDom.Frag
import scalatags.JsDom.implicits.stringFrag

case class Body(frag: Frag = "", id: String = "", title: String = "", keypress: js.Function1[KeyboardEvent, _] = js.undefined.asInstanceOf[js.Function1[KeyboardEvent, _]])
