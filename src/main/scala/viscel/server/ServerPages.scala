package viscel.server

import scalatags.Text.attrs.{`for`, `type`, action, attr, content, href, id, rel, src, title, value, name => attrname}
import scalatags.Text.implicits.{Frag, Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{SeqFrag, body, div, form, h1, head, html, input, label, link, meta, script}
import scalatags.Text.tags2.section

class ServerPages() {

  def makeHtml(stuff: Frag*): Tag =
    html(
      head(
        title := "Viscel",
        link(rel := "stylesheet", href := "style.css", `type` := "text/css"),
        link(rel := "manifest", href := "manifest.json"),
        link(rel := "icon", href := "icon.png", attr("sizes") := "192x192"),
        meta(attrname := "viewport",
             content := "width=device-width, initial-scale=1, user-scalable=yes, minimal-ui")
      ))(stuff: _*)


  def landingTag: Tag = makeHtml(body("if nothing happens, your javascript does not work"),
                                 script(src := "localforage.min.js"),
                                 script(src := "app-opt.js"))

  def fullrender(tag: Tag): String = "<!DOCTYPE html>" + tag.render

  def labelledInput(name: String, inputType: String = "text"): Frag =
    div(label(name, `for` := name),
        input(id := name, `type` := inputType, attrname := name))


  def toolsPage: Tag = makeHtml(
    body(id := "tools",
         makeToolForm("stop", Nil),
         makeToolForm("import", Seq("id", "name", "path")),
         makeToolForm("add", Seq("url"))
         )
    )

  private def makeToolForm(formAction: String, inputs: Seq[String]): Tag = {
    section(
      h1(formAction.capitalize),
      form(action := formAction,
           SeqFrag(inputs.map(labelledInput(_))),
           input(`type` := "submit", value := formAction)))
  }

}
