package visceljs


import org.scalajs.dom.HTMLElement
import viscel.shared.Story.Narration

import scala.Predef.{any2ArrowAssoc, conforms}
import scala.collection.immutable.Map
import scalatags.JsDom.Frag
import scalatags.JsDom.all.bindNode
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{SeqFrag, div}
import scalatags.JsDom.tags2.{nav}

object IndexPage {

	import visceljs.Util._

	case class ColState(id: String, name: String, size: Int, bm: Int, node: String)

	def genIndex(bookmarks: Map[String, Int], narrations: Map[String, Narration]): Frag = {

		val searchResultDiv: HTMLElement = div().render

		def sidePart = make_fieldset("Search", Seq(form_search(narrations.values.toList, searchResultDiv)))(class_info)

		def navigation = link_stop("stop") :: Nil

		def mainPart: List[Frag] = {

			val result: List[(Narration, Int, Int)] =
				bookmarks.map { case (id, pos) =>
					val nr = narrations(id)
					(nr, pos, nr.size - pos)
				}.toList

			val (hasNewPages, isCurrent) = result.partition(_._3 > 0)

			val unreadTags = hasNewPages.sortBy(- _._3).map { case(nr, pos, unread) => link_front(nr, s"${nr.name} ($unread)") }
			val currentTags = isCurrent.sortBy(_._1.name).map { case(nr, pos, unread) => link_front(nr, s"${nr.name}") }
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

		List(
			div(class_main)(mainPart),
			div(class_side)(sidePart),
			div(searchResultDiv),
			nav(class_navigation)(navigation))


	}
}
