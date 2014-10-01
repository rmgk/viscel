package viscel.server

import org.scalactic.TypeCheckedTripleEquals._
import viscel.store._

import scala.Predef.{any2ArrowAssoc, _}
import scala.annotation.tailrec
import scalatags.Text.all._


class FrontPage(user: UserNode, collection: CollectionNode) extends HtmlPage with MaskLocation with MetaNavigation {
	override def Title = collection.name

	override def bodyId = "front"

	override def maskLocation = path_front(collection)

	val first = collection.first
	val second = first.flatMap(_.nextAsset)
	val third = second.flatMap(_.nextAsset)
	val previewLeft = user.getBookmark(collection).orElse(third).orElse(second).orElse(first)
	val previewMiddle = previewLeft.flatMap { _.prevAsset }
	val previewRight = previewMiddle.flatMap { _.prevAsset }

	def bmRemoveForm(bm: AssetNode) = form_post(path_nid(collection),
		input(`type` := "hidden", name := "remove_bookmark", value := collection.nid.toString),
		input(`type` := "submit", name := "submit", value := "remove", class_submit))

	def mainPart = div(class_info)(
		make_table(
			"id" -> collection.id,
			"name" -> collection.name //"chapter" -> collection.size.toString,
			//"pages" -> collection.totalSize.toString
		)) :: Nil

	def navigation = Seq(
		link_main("index"),
		StringFrag(" – "),
		link_node(collection.first, "first"),
		StringFrag(" – "),
		previewLeft.map { bmRemoveForm }.getOrElse("remove"))

	def sidePart = Seq[Frag](
		div(class_content)(
			Seq[Option[Frag]](
				previewRight.map { e => link_node(Some(e), enodeToImg(e)) },
				previewMiddle.map { e => link_node(Some(e), enodeToImg(e)) },
				previewLeft.map { e => link_node(Some(e), enodeToImg(e)) }
			).flatten[Frag]: _*)) ++ chapterlist

	override def navNext = previewLeft.orElse(previewMiddle).orElse(previewRight).map { en => path_nid(en) }

	override def navPrev = None

	override def navUp = Some(path_main)

	def makePageList(chapterName: String, nodes: List[ArchiveNode]): (Frag, List[ArchiveNode]) = {
		@tailrec
		def make_nodelist(pos: Int, done: List[Frag], remaining: List[ArchiveNode]): (List[Frag], List[ArchiveNode]) = {
			remaining match {
				case Nil => (done.reverse.drop(1), Nil)

				case (assetNode: AssetNode) :: rest =>
					make_nodelist(pos + 1, link_node(assetNode, pos) :: StringFrag(" ") :: done, rest)

				case (pageNode: PageNode) :: rest =>
					val more = pageNode.describes.fold(List[ArchiveNode]())(_.layer)
					make_nodelist(pos, done, more ::: rest)

				case (chapterNode: ChapterNode) :: _ =>
					(done.reverse.drop(1), remaining)

				case (coreNode: CoreNode) :: rest =>
					make_nodelist(pos, StringFrag(s"Core: ${ coreNode.id }") :: br :: done, rest)

				case _ :: _ => throw new IllegalArgumentException("unknown archive $head")
			}
		}
		val (nodelist, remaining) = make_nodelist(1, Nil, nodes)
		(fieldset(class_group, class_pages).apply(legend(chapterName), nodelist), remaining)
	}

	@tailrec
	final def makeChapterList(nodes: List[ArchiveNode], headline: Option[String], acc: List[Frag]): List[Frag] = {
		nodes match {
			case Nil => acc

			case (pageNode: PageNode) :: rest =>
				val more = pageNode.describes.map(_.layer).getOrElse(Nil)
				makeChapterList(more ::: rest, headline, acc)

			case (chapterNode: ChapterNode) :: rest =>
				val (pagelist, remaining) = makePageList(chapterNode.name, rest)
				val volume = chapterNode.metadataOption("Volume")
				volume match {
					case None => makeChapterList(remaining, volume, pagelist :: acc)
					case _ if volume === headline => makeChapterList(remaining, volume, pagelist :: acc)
					case Some(volumeName) => makeChapterList(remaining, volume, h3(volumeName) :: pagelist :: acc)
				}

			case (assetNode: AssetNode) :: rest =>
				val (pageList, remaining) = makePageList("", nodes)
				makeChapterList(remaining, headline, pageList :: acc)

			case (coreNode: CoreNode) :: rest =>
				makeChapterList(rest, headline, StringFrag(s"Core: ${ coreNode.id }") :: br :: acc)

			case _ :: _ => throw new IllegalArgumentException("unknown archive $head")
		}
	}

	def chapterlist = makeChapterList(collection.describes.fold(List[ArchiveNode]()) { _.layer }, None, Nil).reverse
}

object FrontPage {
	def apply(user: UserNode, collection: CollectionNode) = new FrontPage(user, collection).response
}
