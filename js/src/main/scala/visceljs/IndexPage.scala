package visceljs


import viscel.shared.Story.Narration

import scala.scalajs.js
import scalatags.JsDom.attrs.id
import scalatags.JsDom.Frag
import scala.Predef.conforms
import scalatags.JsDom.short.HtmlTag
import scalatags.JsDom.tags.{div, body, SeqFrag}
import scalatags.JsDom.implicits.{stringFrag, stringAttr}
import scala.collection.immutable.Map
import scala.Predef.any2ArrowAssoc

object IndexPage {

	import visceljs.Util._

	case class ColState(id: String, name: String, size: Int, bm: Int, node: String)

	def genIndex(bookmarks: Map[String, Int], narrations: List[Narration]): Frag = {

		val collections = narrations.map(n => n.id -> n).toMap

		def sidePart = make_fieldset("Search", Seq(form_search("")))(class_info) :: Nil

		def navigation = link_stop("stop") :: Nil

		def mainPart: List[Frag] = {

			val result: List[(Narration, Int, Int)] =
				bookmarks.map { case (id, pos) =>
					val nr = collections(id)
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
			div(class_navigation)(navigation))


	}
}
