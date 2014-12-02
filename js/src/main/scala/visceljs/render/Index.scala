package visceljs.render

import org.scalajs.dom.HTMLElement
import viscel.shared.Story.Narration
import visceljs.{Render, Body}

import scala.Predef.$conforms
import scala.collection.immutable.Map
import scalatags.JsDom.Frag
import scalatags.JsDom.all.bindNode
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{SeqFrag, div}

object Index {
	import Render._

	import visceljs.Definitions._

	case class ColState(id: String, name: String, size: Int, bm: Int, node: String)

	def gen(bookmarks: Map[String, Int], narrations: Map[String, Narration]): Body = {

		val searchResultDiv: HTMLElement = div().render

		def sidePart = make_fieldset("Search", Seq(form_search(narrations.values.toList, searchResultDiv)))(class_info)

		def mainPart: List[Frag] = {

			val result: List[(Narration, Int, Int)] =
				bookmarks.map { case (id, pos) =>
					narrations.get(id).map { nr =>
						(nr, pos, nr.size - pos)
					}
				}.toList.flatten

			val (hasNewPages, isCurrent) = result.partition(_._3 > 0)

			val unreadTags = hasNewPages.sortBy(-_._3).map { case (nr, pos, unread) => link_front(nr, s"${ nr.name } ($unread)") }
			val currentTags = isCurrent.sortBy(_._1.name).map { case (nr, pos, unread) => link_front(nr, s"${ nr.name }") }
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

		Body(id = "index", title = "Viscel",
			frag = List(
				div(class_main)(mainPart),
				div(class_side)(sidePart),
				div(searchResultDiv),
				makeNavigation(link_stop("stop") :: Nil)))


	}
}
