package viscel.server.pages

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.server.{HtmlPage, MaskLocation, MetaNavigation}
import viscel.store._
import viscel.store.coin._

import scala.Predef.{any2ArrowAssoc, conforms}
import scala.annotation.tailrec
import scalatags.Text.Frag
import scalatags.Text.attrs._
import scalatags.Text.implicits.{intFrag, stringAttr, stringFrag}
import scalatags.Text.tags.{SeqFrag, _}
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

class FrontPage(user: User, collection: Collection) extends HtmlPage with MaskLocation with MetaNavigation {
	override def Title = collection.name

	override def bodyId = "front"

	override def maskLocation = path_front(collection)

	lazy val first = collection.first
	lazy val second = first.flatMap(_.nextAsset)
	lazy val third = second.flatMap(_.nextAsset)
	lazy val previewLeft = user.getBookmark(collection).orElse(third).orElse(second).orElse(first)
	lazy val previewMiddle = previewLeft.flatMap { _.prevAsset }
	lazy val previewRight = previewMiddle.flatMap { _.prevAsset }

	def bmRemoveForm(bm: Asset) = form_post(path_nid(collection),
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
		stringFrag(" – "),
		link_node(collection.first, "first"),
		stringFrag(" – "),
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

	def makePageList(chapterName: String, nodes: List[Node]): (Frag, List[Node]) = {
		@tailrec
		def make_nodelist(pos: Int, done: List[Frag], remaining: List[Node]): (List[Frag], List[Node]) = {
			remaining match {
				case Nil => (done.reverse.drop(1), Nil)

				case Coin.isAsset(assetNode) :: rest =>
					make_nodelist(pos + 1, link_node(assetNode, pos) :: stringFrag(" ") :: done, rest)

				case Coin.isPage(pageNode) :: rest =>
					val more = Traversal.layerBelow(pageNode.self)
					make_nodelist(pos, done, more ::: rest)

				case Coin.isChapter(_) :: _ =>
					(done.reverse.drop(1), remaining)

				case Coin.isCore(coreNode) :: rest =>
					make_nodelist(pos, stringFrag(s"Core: ${ coreNode.id }") :: br :: done, rest)

				case _ :: _ => throw new IllegalArgumentException("unknown archive $head")
			}
		}
		val (nodelist, remaining) = make_nodelist(1, Nil, nodes)
		(fieldset(class_group, class_pages).apply(legend(chapterName), nodelist), remaining)
	}

	@tailrec
	final def makeChapterList(nodes: List[Node], headline: Option[String], acc: List[Frag]): List[Frag] = {
		nodes match {
			case Nil => acc

			case Coin.isPage(pageNode) :: rest =>
				val more = Traversal.layerBelow(pageNode.self)
				makeChapterList(more ::: rest, headline, acc)

			case Coin.isChapter(chapterNode) :: rest =>
				val (pagelist, remaining) = makePageList(chapterNode.name, rest)
				val volume = chapterNode.metadataOption("Volume")
				volume match {
					case None => makeChapterList(remaining, volume, pagelist :: acc)
					case _ if volume === headline => makeChapterList(remaining, volume, pagelist :: acc)
					case Some(volumeName) => makeChapterList(remaining, volume, h3(volumeName) :: pagelist :: acc)
				}

			case Coin.isAsset(assetNode) :: rest =>
				val (pageList, remaining) = makePageList("", nodes)
				makeChapterList(remaining, headline, pageList :: acc)

			case Coin.isCore(coreNode) :: rest =>
				makeChapterList(rest, headline, stringFrag(s"Core: ${ coreNode.id }") :: br :: acc)

			case other :: _ => throw new IllegalArgumentException(s"unknown archive ${other.getLabels.asScala.toList}")
		}
	}

	def chapterlist = makeChapterList(Traversal.layerBelow(collection.self), None, Nil).reverse
}
