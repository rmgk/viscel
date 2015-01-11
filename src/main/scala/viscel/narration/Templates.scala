package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.narration.SelectUtil.storyFromOr
import viscel.shared.Story.More
import viscel.shared.{Story, ViscelUrl}

object Templates {
	case class AP(override val id: String,
								override val name: String,
								start: ViscelUrl,
								wrapArchive: Document => List[Story] Or Every[ErrorMessage],
								wrapPage: Document => List[Story] Or Every[ErrorMessage]) extends Narrator {
		override def archive: List[Story] = More(start, "archive") :: Nil
		override def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
			case "archive" => wrapArchive(doc)
			case "page" => wrapPage(doc)
		})
	}
	case class SF(override val id: String,
								override val name: String,
								start: ViscelUrl,
								wrapPage: Document => List[Story] Or Every[ErrorMessage]) extends Narrator {
		override def archive: List[Story] = More(start, "page") :: Nil
		override def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
			case _ => wrapPage(doc)
		})
	}
}
