package viscel.server.pages

import org.neo4j.graphdb.Node
import viscel.server.{HtmlPage, MaskLocation, MetaNavigation}
import viscel.store._
import viscel.database.Traversal
import viscel.store.coin._

import scala.Predef.{any2ArrowAssoc, conforms}
import scalatags.Text.{TypedTag, Frag}
import scalatags.Text.attrs._
import scalatags.Text.implicits.{intFrag, stringAttr, stringFrag}
import scalatags.Text.tags.{SeqFrag, _}

class FrontPage(user: User, collection: Collection) extends HtmlPage with MaskLocation with MetaNavigation {
	override def Title = collection.name

	override def bodyId = "front"

	override def maskLocation = path_front(collection)

	lazy val first = collection.first
	lazy val second = first.flatMap(_.next)
	lazy val third = second.flatMap(_.next)
	lazy val previewLeft = user.getBookmark(collection).orElse(third).orElse(second).orElse(first)
	lazy val previewMiddle = previewLeft.flatMap { _.prev }
	lazy val previewRight = previewMiddle.flatMap { _.prev }

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


	def chapterlist: List[Frag] = {
		def innerChapterlist(node: Node): List[Frag] = {
			case class State(chapterName: String, headline: Option[String], fragments: List[Frag], finished: List[Frag], counter: Int)

			def makeChapter(name: String, inner: List[Frag]): Frag =
				fieldset(class_group, class_pages).apply(legend(name), inner.reverse.drop(1))

			val res = Traversal.fold(State("No Chapter", None, Nil, Nil, 0), node) { (state, node) =>
				val State(chapterName, headline, fragments, finished, counter) = state
				node match {
					case Coin.isCore(core) => state.copy(fragments = stringFrag(s"Core: ${ core.id }") :: br :: fragments)

					case Coin.isPage(page) => state

					case Coin.isAsset(asset) => state.copy(
						counter = counter + 1,
						fragments = link_node(asset, counter + 1) :: stringFrag(" ") :: fragments)

					case Coin.isChapter(chapter) =>
						val newVolume = chapter.metadataOption("Volume")
						state.copy(
							counter = 0,
							chapterName = chapter.name,
							headline = newVolume,
							fragments = Nil,
							finished =
								if (counter == 0 && chapterName == "No Chapter") Nil
								else {
									val group = makeChapter(chapterName, fragments)
									if (newVolume != headline && newVolume.isDefined) group :: h3(newVolume.get) :: finished
									else group :: finished
								})
				}
			}
			(makeChapter(res.chapterName, res.fragments) :: res.finished).reverse
		}

		Traversal.next(collection.self).fold[List[Frag]](Nil)(innerChapterlist)
	}

}
