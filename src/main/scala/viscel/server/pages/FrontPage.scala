package viscel.server.pages

import org.neo4j.graphdb.Node
import viscel.server.{HtmlPage, MaskLocation, MetaNavigation}
import viscel.store._
import viscel.database.{Ntx, Traversal}
import viscel.store.coin._

import scala.Predef.{any2ArrowAssoc, conforms}
import scalatags.Text.{TypedTag, Frag, Tag, RawFrag}
import scalatags.Text.attrs._
import scalatags.Text.implicits.{intFrag, stringAttr, stringFrag}
import scalatags.Text.tags.{SeqFrag, ExtendedString, _}

class FrontPage(user: User, collection: Collection)(implicit ntx: Ntx) extends HtmlPage {
	override def Title = collection.name

	override def bodyId = "front"

	lazy val first = collection.first
	lazy val second = first.flatMap(_.next)
	lazy val third = second.flatMap(_.next)
	lazy val previewLeft = user.bookmarks.get(collection.id).flatMap(collection.apply).orElse(third).orElse(second).orElse(first)
	lazy val previewMiddle = previewLeft.flatMap { _.prev }
	lazy val previewRight = previewMiddle.flatMap { _.prev }

	def bmRemoveForm(bm: Asset) = form_post(path_nid(collection),
		input(`type` := "hidden", name := "remove_bookmark", value := collection.id),
		input(`type` := "submit", name := "submit", value := "remove", class_submit))

	override def fullHtml: Tag = html(header, content, script(src := path_scripts),
		script(RawFrag(s"CollectionPage().assets = $assetList;CollectionPage().main()")))

	override def content: Tag = body()

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
			).flatten[Frag]: _*))

//	override def navNext = previewLeft.orElse(previewMiddle).orElse(previewRight).map { en => path_nid(en) }
//
//	override def navPrev = None
//
//	override def navUp = Some(path_main)


	def assetList: String = {
		def collectAssets(node: Node): List[String] = {
			Traversal.fold(List[String](), node) { (state, node) =>
				node match {
					case Coin.isAsset(asset) => asset.blob.fold("")(b => b.nid.toString) :: state
					case _ => state
				}
			}
		}

		Traversal.next(collection.self).fold[List[String]](Nil)(collectAssets).reverse.mkString("[\"", "\",\"", "\"]")


	}

}
