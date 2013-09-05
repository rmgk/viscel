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

class ChapterPage(user: UserNode, collection: CollectionNode) extends HtmlPage with MaskLocation {
	override def Title = collection.name
	override def bodyId = "chapter"
	override def maskLocation = path_chapter(collection.id)

	// def mainPart = div.cls("info")(make_table(
	// 	"name" -> chapter.name,
	// 	"chapter" -> chapter.position.toString,
	// 	"pages" -> chapter.size.toString))

	def mainPart = ""

	def navigation = link_node(collection, "front")
	// def navigation = Seq[STag](
	// 	link_node(chapter.prev, "prev"),
	// 	" ",
	// 	link_node(chapter.first, "first"),
	// 	" – ",
	// 	link_node(chapter.collection, "front"),
	// 	" – ",
	// 	link_node(chapter.last, "last"),
	// 	" ",
	// 	link_node(chapter.next, "next"))

	def make_pagelist(chapter: ChapterNode): STag = fieldset.cls("group pages")(legend(chapter.name),
		chapter.children.sortBy(_.position).flatMap { child => Seq(link_node(child, child.position.toString), " ": STag) })

	def make_chapterlist(chapters: List[ChapterNode], headline: Option[String]): Seq[STag] = chapters match {
		case List() => Seq()
		case c :: cs =>
			val vol = c.get("Volume")
			if (vol == headline) make_pagelist(c) +: make_chapterlist(cs, headline)
			else Seq(vol.map(h3(_)), Some(make_pagelist(c))).flatten ++ make_chapterlist(cs, vol)
	}

	override def sidePart = make_chapterlist(collection.children.sortBy(_.position).toList, None)

}

object ChapterPage {
	def apply(user: UserNode, collection: CollectionNode) = new ChapterPage(user, collection).response
}
