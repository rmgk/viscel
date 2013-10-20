package viscel.server

import scalatags._
import viscel.store.{ Util => StoreUtil, _ }
import scala.Some

class FrontPage(user: UserNode, collection: CollectionNode) extends HtmlPage with MaskLocation with MetaNavigation {
	override def Title = collection.name
	override def bodyId = "front"
	override def maskLocation = path_front(collection.id)

	val bm = user.getBookmark(collection)
	val bm1 = bm.flatMap { _.prev }
	val bm2 = bm1.flatMap { _.prev }

	def bmRemoveForm(bm: ElementNode) = form_post(path_nid(collection.nid),
		input.ctype("hidden").name("remove_bookmark").value(collection.nid),
		input.ctype("submit").name("submit").value("remove").cls("submit"))

	def mainPart = div.cls("info")(
		make_table(
			"id" -> collection.id,
			"name" -> collection.name //"chapter" -> collection.size.toString,
			//"pages" -> collection.totalSize.toString
			))
	def navigation = Seq[STag](
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
		bm.map { bmRemoveForm(_) }.getOrElse("remove"))
	def sidePart = Seq[STag](div.cls("content")(Seq(
		bm2.map { e => link_node(Some(e), enodeToImg(e)) },
		bm1.map { e => link_node(Some(e), enodeToImg(e)) },
		bm.map { e => link_node(Some(e), enodeToImg(e)) }).flatten[STag]),
		chapterlist)

	override def navPrev = bm2.orElse(bm1).orElse(collection.first).map { en => path_nid(en.nid) }
	override def navNext = bm.orElse(collection.last).map { en => path_nid(en.nid) }
	override def navUp = Some(path_main)

	def make_pagelist(chapter: ChapterNode, elements: Seq[ElementNode]): STag = fieldset.cls("group pages")(legend(chapter.name),
		elements.zipWithIndex.flatMap { case (child, pos) => Seq(link_node(child, (pos + 1).toString), " ": STag) })

	def make_chapterlist(nodes: List[ViscelNode], headline: Option[String]): Seq[STag] = nodes match {
		case List() => Seq()
		case (c: ChapterNode) :: cs =>
			val vol = c.get("Volume")
			val (elts_, next) = cs.span {
				case n: ElementNode => true
				case n: ChapterNode => false
			}
			val elts = elts_.asInstanceOf[Seq[ElementNode]]
			if (vol == headline) make_pagelist(c, elts) +: make_chapterlist(next, headline)
			else Seq(vol.map(h3(_)), Some(make_pagelist(c, elts))).flatten ++ make_chapterlist(next, vol)
	}

	def chapterlist = make_chapterlist(collection.archive.map { _.flatten }.to[List].flatten, None)
}

object FrontPage {
	def apply(user: UserNode, collection: CollectionNode) = new FrontPage(user, collection).response
}
