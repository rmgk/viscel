package visceljs

import org.scalajs.dom.{KeyboardEvent, html}
import rescala.{Signal, Var}

import scala.scalajs.js
import scalatags.JsDom.TypedTag

case class Body(bodyTag: Signal[TypedTag[html.Body]],
                id: String = "",
                title: Signal[String] = Var(""),
                keypress: js.Function1[KeyboardEvent, _] = js.undefined.asInstanceOf[js.Function1[KeyboardEvent, _]])
