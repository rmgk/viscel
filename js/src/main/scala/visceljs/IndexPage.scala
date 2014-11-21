package visceljs


import scala.scalajs.js
import scalatags.JsDom.attrs.id
import scalatags.JsDom.Frag
import scala.Predef.conforms
import scalatags.JsDom.short.{HtmlTag}
import scalatags.JsDom.tags.{div, body, SeqFrag}
import scalatags.JsDom.implicits.{stringFrag, stringAttr}

object IndexPage {

	import Util._

	case class ColState(id: String, name: String, size: Int, bm: Int, node: String)

	def genIndex(bookmarks: js.Dictionary[Int], collections: js.Dictionary[js.Dictionary[String]]): Frag = {

		def sidePart = make_fieldset("Search", Seq(form_search("")))(class_info) :: Nil

		def navigation = link_stop("stop") :: Nil

		def mainPart: List[Frag] = {

			val result: List[ColState] =
				keys(bookmarks).map { id =>
					val col = collections(id)
					ColState(id, col("name"), parseInt(col("size")), bookmarks(id), col("node"))
				}

			val (hasNewPages, isCurrent) = result.partition(cs => cs.bm < cs.size)

			val unreadTags = hasNewPages.sortBy(cs => cs.bm - cs.size).map { cs => link_front(cs.id, cs.name, s"${cs.name} (${cs.size - cs.bm})") }
			val currentTags = isCurrent.sortBy(_.name).map { cs => link_front(cs.id, cs.name, s"${cs.name}") }
			//val availableCores = Core.availableCores.map { core => link_core(core) }.toSeq
			//		val allCollections = Neo.nodes(viscel.store.label.Collection).map(CollectionNode(_)).sortBy(_.name).map { collection =>
			//			link_node(collection, s"${ collection.name }")
			//		}

			make_fieldset("New Pages", unreadTags)(class_group) ::
				make_fieldset("Bookmarks", currentTags)(class_group) ::
				//make_fieldset("All Collections", allCollections)(class_group) ::
				//make_fieldset("Available Cores", availableCores)(class_group) ::
				Nil
		}

		def content: List[HtmlTag] = List(
			div(class_main)(mainPart),
			div(class_side)(sidePart),
			div(class_navigation)(navigation))


		content

	}
}
