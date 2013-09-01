package viscel.server

import com.typesafe.scalalogging.slf4j.Logging
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scalatags._
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel.store._
import viscel.store.{ Util => StoreUtil }
import viscel.time
import viscel._

class ChapterPage(user: UserNode, chapter: ChapterNode) extends HtmlPage with MaskLocation with JavascriptNavigation {
	override def Title = chapter.name
	override def bodyId = "chapter"
	override def maskLocation = path_chapter(chapter.collection.id, chapter.position)

	override def navPrev = chapter.prev.map { ch => path_nid(ch.nid) }
	override def navNext = chapter.next.map { ch => path_nid(ch.nid) }
	override def navUp = Some(path_nid(collection.nid))
	override def navDown = chapter.first.map { f => path_nid(f.nid) }

	override def mainPart = div.cls("info")(make_table(
		"name" -> chapter.name,
		"chapter" -> chapter.position.toString,
		"pages" -> chapter.size.toString))

	def navigation = Seq[STag](
		link_node(chapter.prev, "prev"),
		" ",
		link_node(chapter.first, "first"),
		" – ",
		link_node(chapter.collection, "front"),
		" – ",
		link_node(chapter.last, "last"),
		" ",
		link_node(chapter.next, "next"))

	override def sidePart = make_fieldset("Pages",
		chapter.children.sortBy(_.position).map { child => link_node(child, child.position.toString) }).cls("group pages")

}

object ChapterPage {
	def apply(user: UserNode, chapter: ChapterNode) = new ChapterPage(user, chapter).response
}
