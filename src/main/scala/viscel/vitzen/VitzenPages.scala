package viscel.vitzen

import java.nio.file.Path
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.model._

import scalatags.Text.all.{frag, raw}
import scalatags.Text.attrs.{`type`, cls, content, href, id, rel, title, name => attrname}
import scalatags.Text.implicits.{Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{div, h1, h2, head, header, html, link, meta, span, a => anchor}
import scalatags.Text.tags2.{article, main, section}
import scalatags.Text.{Frag, Modifier, TypedTag}

class VitzenPages(asciiData: AsciiData, contentPath: Path) {


  val path_css: String = "/vitzen.css"


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
              anchor(cls := "archive-post-link", href := s"posts/${dh.path}", dh.title)
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
    htmlResponse(makeHtml(tBody(tSection(docs.sortBy(_.date.toString).reverse))))
  }


}
