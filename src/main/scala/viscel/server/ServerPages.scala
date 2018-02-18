package viscel.server

import akka.http.scaladsl.model._
import io.circe.Encoder
import io.circe.syntax._
import viscel.scribe.{Article, Chapter, ImageRef, ReadableContent, Scribe}
import viscel.shared.{ChapterPos, Contents, Description, Gallery, SharedImage}
import viscel.store.{NarratorCache, User}

import scalatags.Text.attrs.{`for`, `type`, action, cls, content, href, id, rel, src, title, value, name => attrname}
import scalatags.Text.implicits.{Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{body, div, fieldset, form, frag, h1, head, html, input, label, legend, link, meta, script}
import scalatags.Text.tags2.section
import scalatags.text.Frag

class ServerPages(scribe: Scribe, narratorCache: NarratorCache) {

  def narration(id: String): Option[Contents] = {
    @scala.annotation.tailrec
    def recurse(content: List[ReadableContent], art: List[SharedImage], chap: List[ChapterPos], c: Int): (List[SharedImage], List[ChapterPos]) = {
      content match {
        case Nil => (art, chap)
        case h :: t => {
          h match {
            case Article(ImageRef(ref, origin, data), blob) =>
              val article = SharedImage(origin = origin.uriString, blob, data)
              recurse(t, article :: art, if (chap.isEmpty) List(ChapterPos("", 0)) else chap, c + 1)
            case Chapter(name) => recurse(t, art, ChapterPos(name, c) :: chap, c)
          }
        }
      }
    }

    val pages = scribe.findPages(id)
    if (pages.isEmpty) None
    else {
      val (articles, chapters) = recurse(pages, Nil, Nil, 0)
      Some(Contents(Gallery.fromSeq(articles.reverse), chapters))
    }
  }

  def narrations(): Set[Description] = {
    val books = scribe.allDescriptions()
    var known = books.map(d => d.id -> d).toMap
    val nars = narratorCache.all.map { n =>
      known.get(n.id) match {
        case None => Description(n.id, n.name, 0, unknownNarrator = false)
        case Some(desc) =>
          known = known - n.id
          desc.copy(unknownNarrator = false)
      }
    }
    nars ++ known.values
  }

  val path_css: String = "css"
  val path_js: String = "js"


  def makeHtml(stuff: Frag*): Tag =
    html(
      head(
        title := "Viscel",
        link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
        meta(attrname := "viewport", content := "width=device-width, initial-scale=1, user-scalable=yes, minimal-ui"))
    )(stuff: _*)

  def htmlResponse(tag: Tag): HttpResponse = HttpResponse(entity = HttpEntity(
    ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
    "<!DOCTYPE html>" + tag.render))

  val fullHtml: Tag = makeHtml(body("if nothing happens, your javascript does not work"), script(src := path_js))

  val landing: HttpResponse = htmlResponse(fullHtml)

  def jsonResponse[T: Encoder](value: T): HttpResponse = HttpResponse(entity = HttpEntity(
    ContentType(MediaTypes.`application/json`),
    value.asJson.noSpaces))

  def bookmarks(user: User): HttpResponse = jsonResponse(user.bookmarks)

  def labelledInput(name: String, inputType: String = "text"): Frag =
    div(cls := "pure-control-group",
        label(name, `for` := name), input(id := name, `type` := inputType, attrname := name)
    )


  val toolsPage: Tag = makeHtml(
    body(h1("Tools"),
         makeToolForm("stop", Nil),
         makeToolForm("import", Seq("id", "name", "path")),
         makeToolForm("add", Seq("url"))
    )
  )

  private def makeToolForm(formAction: String, inputs: Seq[String]) = {
    section(
      fieldset(legend(formAction.capitalize),
               form(cls := "pure-form pure-form-aligned", action := formAction,
                    frag(inputs.map(labelledInput(_)): _*),
                    div(cls := "pure-controls",
                        input(`type` := "submit", cls := "pure-button", value := formAction)))))
  }
  val toolsResponse: HttpResponse = htmlResponse(toolsPage)

}
