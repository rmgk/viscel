package visceljs

import org.scalajs.dom.html
import rescala.{Signal, Var}

import scalatags.JsDom.TypedTag

case class Body(bodyTag: Signal[TypedTag[html.Body]],
                id: String = "",
                title: Signal[String] = Var(""))
