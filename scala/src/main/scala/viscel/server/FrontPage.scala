package viscel.server

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.scalactic.TypeCheckedTripleEquals._
import viscel.store._

import scala.annotation.tailrec
import scala.collection.immutable.LinearSeq
import scalatags._
import scalatags.all._

class FrontPage(user: UserNode, collection: CollectionNode) extends HtmlPage with MaskLocation with MetaNavigation with StrictLogging {
	override def Title = collection.name

	override def bodyId = "front"

	override def maskLocation = path_front(collection)

	val bm = user.getBookmark(collection)
	val bm1 = bm.flatMap { _.prevAsset }
	val bm2 = bm1.flatMap { _.prevAsset }

	def bmRemoveForm(bm: AssetNode) = form_post(path_nid(collection),
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

	def makePageList(chapterName: String, nodes: List[ArchiveNode]): (Node, List[ArchiveNode]) = {
		@tailrec
		def make_nodelist(pos: Int, done: List[Node], remaining: List[ArchiveNode]): (List[Node], List[ArchiveNode]) = {
			remaining match {
				case Nil => (done.reverse.drop(1), Nil)

				case (assetNode: AssetNode) :: rest =>
					make_nodelist(pos + 1, link_node(assetNode, pos) :: StringNode(" ") :: done, rest)

				case (pageNode: PageNode) :: rest =>
					val more = pageNode.describes.fold(List[ArchiveNode]())(_.layer)
					make_nodelist(pos, done, more ::: rest)

				case (chapterNode: ChapterNode) :: _ =>
					(done.reverse.drop(1), remaining)

				case (coreNode: CoreNode) :: rest =>
					make_nodelist(pos, StringNode(s"Core: ${coreNode.id}") :: done, rest)

				case _ :: _ => throw new IllegalArgumentException("unknown archive $head")
			}
		}
		val (nodelist, remaining) = make_nodelist(1, Nil, nodes)
		(fieldset(class_group, class_pages)(legend(chapterName), nodelist), remaining)
	}

	def makeChapterList(nodes: List[ArchiveNode], headline: Option[String]): List[Node] = {
		nodes match {
			case List() => List()

			case (pageNode: PageNode) :: rest =>
				val more = pageNode.describes.map(_.layer).getOrElse(Nil)
				makeChapterList(more ::: rest, headline)

			case (chapterNode: ChapterNode) :: rest =>
				val (pagelist, remaining) = makePageList(chapterNode.name, rest)
				val volume = chapterNode.get("Volume")
				val result = pagelist :: makeChapterList(remaining, volume)
				volume match {
					case None => result
					case _ if volume === headline => result
					case Some(volumeName) => h3(volumeName) :: result
				}

			case (assetNode: AssetNode) :: rest =>
				val (pageList, remaining) = makePageList("", nodes)
				pageList :: makeChapterList(remaining, headline)

			case (coreNode: CoreNode) :: rest =>
				StringNode(s"Core: ${coreNode.id}") :: br :: makeChapterList(rest, headline)

			case _ :: _ => throw new IllegalArgumentException("unknown archive $head")
		}
	}

	def chapterlist = makeChapterList(collection.describes.fold(List[ArchiveNode]()){ _.layer}, None)
}

object FrontPage {
	def apply(user: UserNode, collection: CollectionNode) = new FrontPage(user, collection).response
}
