package viscel.server

import akka.http.scaladsl.model._
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax._
import scalatags.Text.all.raw
import scalatags.Text.attrs.{`for`, `type`, action, attr, content, href, id, rel, src, title, value, name => attrname}
import scalatags.Text.implicits.{Frag, Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{SeqFrag, body, div, form, h1, head, html, input, label, link, meta, script}
import scalatags.Text.tags2.section
import viscel.store.User

class ServerPages() {

  val path_css: String = "css"
  val path_js : String = "js"


  def makeHtml(stuff: Frag*): Tag =
    html(
      head(
        title := "Viscel",
        link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
        link(rel := "manifest", href := "manifest.json"),
        link(rel := "icon", href := "icon.png", attr("sizes") := "192x192"),
        meta(attrname := "viewport",
             content := "width=device-width, initial-scale=1, user-scalable=yes, minimal-ui"),
        script(raw("""if('serviceWorker' in navigator) {
        navigator.serviceWorker
                 .register('serviceworker.js')
                 .then(function(reg) { console.log('Service Worker Registered', reg); })
                 .catch(function(err) { console.log('Service Worker Failed', err); }); }"""))
        )
      )(stuff: _*)

  def htmlResponse(tag: Tag): HttpResponse = HttpResponse(entity = HttpEntity(
    ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
    "<!DOCTYPE html>" + tag.render))

  val fullHtml: Tag = makeHtml(body("if nothing happens, your javascript does not work"),
                               script(src := path_js))

  val landing: HttpResponse = htmlResponse(fullHtml)

  def jsonResponse[T: Encoder](value: T): HttpResponse = HttpResponse(entity = HttpEntity(
    ContentType(MediaTypes.`application/json`),
    value.asJson.noSpaces))

  def bookmarks(user: User): HttpResponse = jsonResponse(user.bookmarks)

  def labelledInput(name: String, inputType: String = "text"): Frag =
    div(label(name, `for` := name),
        input(id := name, `type` := inputType, attrname := name))


  val toolsPage: Tag = makeHtml(
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
  val toolsResponse: HttpResponse = htmlResponse(toolsPage)

}
