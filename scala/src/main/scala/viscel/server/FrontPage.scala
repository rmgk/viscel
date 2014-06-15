package viscel.server

import org.scalactic.TypeCheckedTripleEquals._
import viscel.store._

import scala.collection.immutable.LinearSeq
import scalatags._
import scalatags.all._

class FrontPage(user: UserNode, collection: CollectionNode) extends HtmlPage with MaskLocation with MetaNavigation {
	override def Title = collection.name

	override def bodyId = "front"

	override def maskLocation = path_front(collection)

	val bm = user.getBookmark(collection)
	val bm1 = bm.flatMap { _.prev }
	val bm2 = bm1.flatMap { _.prev }

	def bmRemoveForm(bm: ElementNode) = form_post(path_nid(collection),
		input(`type` := "hidden", name := "remove_bookmark", value := collection.nid.toString),
		input(`type` := "submit", name := "submit", value := "remove", class_submit))

	def mainPart = div(class_info)(
		make_table(
			"id" -> collection.id,
			"name" -> collection.name //"chapter" -> collection.size.toString,
			//"pages" -> collection.totalSize.toString
		)) :: Nil

	def navigation = Seq[Node](
		link_main("index"),
		" – ",
		// link_node(collection.first, "first"),
		// " chapter ",
		// link_node(collection.last, "last"),
		// " – ",
		link_node(collection.first, "first"),
		" ",
		link_node(collection.last, "last"),
		" – ",
		bm.map { bmRemoveForm }.getOrElse("remove"))

	def sidePart = Seq[Node](
		div(class_content)(
			Seq[Option[Node]](
				bm2.map { e => link_node(Some(e), enodeToImg(e)) },
				bm1.map { e => link_node(Some(e), enodeToImg(e)) },
				bm.map { e => link_node(Some(e), enodeToImg(e)) }
			).flatten[Node]: _*)) ++ chapterlist

	override def navPrev = bm2.orElse(bm1).orElse(collection.first).map { en => path_nid(en) }

	override def navNext = bm.orElse(collection.last).map { en => path_nid(en) }

	override def navUp = Some(path_main)

	def make_pagelist(chapter: ChapterNode, elements: Seq[ElementNode]): Node = fieldset(class_group, class_pages)(legend(chapter.name),
		elements.zipWithIndex.flatMap { case (child, pos) => Seq(link_node(child, (pos + 1).toString), " ": Node) })

	def make_chapterlist(nodes: LinearSeq[ViscelNode], headline: Option[String]): Seq[Node] = nodes match {
		case List() => Seq()
		case (c: ChapterNode) :: cs =>
			val vol = c.get("Volume")
			val (elts_, next) = cs.span {
				case n: ElementNode => true
				case n: ChapterNode => false
			}
			val elts = elts_.asInstanceOf[Seq[ElementNode]]
			if (vol === headline) make_pagelist(c, elts) +: make_chapterlist(next, headline)
			else Seq(vol.map(h3(_)), Some(make_pagelist(c, elts))).flatten ++ make_chapterlist(next, vol)
	}

	def chapterlist = make_chapterlist(collection.archive.map { _.flatten }.to[LinearSeq].flatten, None)
}

object FrontPage {
	def apply(user: UserNode, collection: CollectionNode) = new FrontPage(user, collection).response
}
