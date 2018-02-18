package visceljs

import org.scalajs.dom.KeyboardEvent
import rescala.{Signal, Var}

import scala.scalajs.js
import scalatags.JsDom.Frag
import scalatags.JsDom.implicits.stringFrag

case class Body(frag: Frag = "",
                id: String = "",
                title: Signal[String] = Var(""),
                keypress: js.Function1[KeyboardEvent, _] = js.undefined.asInstanceOf[js.Function1[KeyboardEvent, _]])
