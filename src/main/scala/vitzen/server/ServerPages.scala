package vitzen.server

import java.nio.file.Path
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.model._
import io.circe.Encoder
import io.circe.syntax._
import vitzen.data.{AsciiData, Post}

import scalatags.Text.all.{frag, raw}
import scalatags.Text.attrs.{`type`, action, cls, content, href, id, rel, src, title, value, name => attrname}
import scalatags.Text.implicits.{Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{body, br, div, form, h1, h2, head, header, html, input, link, meta, script, span, a => anchor}
import scalatags.Text.tags2.{article, main, section}
import scalatags.Text.{Frag, Modifier, TypedTag}

class ServerPages(asciiData: AsciiData, contentPath: Path) {


  val path_css: String = "/css"
  val path_js: String = "/js"


  private def tHead = {
    head(
      title := "Vitzen",
      link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
      meta(attrname := "viewport", content := "width=device-width, initial-scale=1, user-scalable=yes, minimal-ui"))
  }

  private def tBody(content: Frag) = {
    div(cls := "container", id := "mobile-panel",
      main(cls := "main", id := "main",
        div(cls := "content-wrapper",
          div(cls := "content", id := "content",
            content
          )
        )
      )
    )
  }

  private def tMeta(post: Post) = {
    div(cls := "post-meta",
      span(cls := "post-time", s" ${post.date.toLocalDate} ${post.date.toLocalTime}"),
      frag(post.modified.map(mt => span(cls := "post-time", s" Modified ${mt.toLocalDate} ${mt.toLocalTime} ")).toList: _*)
    )
  }

  private def tSingle(title: String, meta: Frag, content: Frag) = {
    article(cls := "post",
      header(cls := "post-header",
        h1(cls := "post-title",
          title
        ),
        meta
      ),
      div(cls := "post-content",
        content
      )
    )
  }

  private def tSection(dhs: List[Post]) = {
    section(id := "archive", cls := "archive",
      div(cls := "collection-title",
        h2(cls := "archive-year", dhs.head.date.format(DateTimeFormatter.ofPattern("YYYY")))
      ),
      frag(
        dhs.map { dh =>
          div(cls := "archive-post",
            span(cls := "archive-post-time", dh.date.format(DateTimeFormatter.ISO_DATE)),
            span(cls := "archive-post-title",
              anchor(cls := "archive-post-link", href := s"content/${dh.path}", dh.title)
            )
          )
        }: _*
      )
    )
  }

  def makeHtml(stuff: Modifier*): TypedTag[String] =
    html(tHead)(stuff: _*)


  def htmlResponse(tag: Tag): HttpResponse = HttpResponse(entity = HttpEntity(
    ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
    "<!DOCTYPE html>" + tag.render))


  def getContent(name: String): HttpResponse = {
    val post = asciiData.getOne(name)
    val res = htmlResponse(makeHtml(tBody(tSingle(post.title, tMeta(post),
      raw(post.content)))))
    res
  }

  def archive(): HttpResponse = {
    val docs = asciiData.getAll()
    htmlResponse(makeHtml(tBody(tSection(docs.sortBy(_.date.toString)))))
  }


  val fullHtml: TypedTag[String] = makeHtml(body("if nothing happens, your javascript does not work"), script(src := path_js))

  val landing: HttpResponse = htmlResponse(fullHtml)

  def jsonResponse[T: Encoder](value: T): HttpResponse = HttpResponse(entity = HttpEntity(
    ContentType(MediaTypes.`application/json`),
    value.asJson.noSpaces))


  val toolsPage: TypedTag[String] = makeHtml(body(section(anchor(href := "stop")("stop")),
    section(
      form(action := "import",
        "id: ", input(`type` := "text", attrname := "id"), br,
        "name: ", input(`type` := "text", attrname := "name"), br,
        "path: ", input(`type` := "text", attrname := "path"), br,
        input(`type` := "submit", value := "import"))),
    section(
      form(action := "add",
        "url: ", input(`type` := "text", attrname := "url"), br,
        input(`type` := "submit", value := "add")))
  ))

  val toolsResponse: HttpResponse = htmlResponse(toolsPage)

}
