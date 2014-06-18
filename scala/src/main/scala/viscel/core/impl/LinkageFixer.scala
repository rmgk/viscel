package viscel.core.impl

import com.typesafe.scalalogging.slf4j.StrictLogging
import viscel.store._

import scala.annotation.tailrec

trait LinkageFixer extends StrictLogging {

	/**
	 * this takes an archive links all elements to their chapters,
	 * all chapters to the collection,
	 * creates the first and last pointers
	 * and links the elements in their order
	 */
	def fixLinkage(an: ArchiveNode, collection: CollectionNode) = {
		@tailrec
		def link(vns: List[ViscelNode], currentChapter: ChapterNode): Unit = vns match {
			case Nil => ()
			case vn :: vntail =>
				vn match {
					case cn: ChapterNode =>
						cn.collection = collection
						link(vntail, cn)
					case en: ElementNode =>
						en.chapter = currentChapter
						link(vntail, currentChapter)
					case coreNode: CoreNode => link(vntail, currentChapter)
				}
		}
		val nodes = an.flatPayload
		require(nodes.headOption.fold(true)(_.isInstanceOf[ChapterNode]), "illegal archive, first node is not a chapter")
		link(nodes.to[List], null)
		val elements = nodes.collect { case en: ElementNode => en }
		elements.reduceLeftOption { (prev, next) =>
			prev.next = next
			next
		}
		elements.headOption.foreach(collection.first = _)
		elements.lastOption.foreach(collection.last = _)
	}
}
