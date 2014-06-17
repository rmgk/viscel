package viscel.core.impl

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Direction
import org.scalactic.TypeCheckedTripleEquals._
import viscel.core._
import viscel.store._

trait ArchiveManipulation extends StrictLogging with LinkageFixer with ArchiveCreator {

	def deletePart(archivePart: List[ViscelNode]) = archivePart.foreach {
		case sn: StructureNode => sn.deleteNode(warn = false)
		case pn: PageNode =>
			pn.describes.foreach { described =>
				if (described.self.getDegree(rel.describes, Direction.INCOMING) < 2)
					described.flatten.foreach { _.deleteNode(warn = false) }
			}
			pn.deleteNode(warn = false)
	}

	def reuseOldDescriptions(newArchivePart: ArchiveNode, oldArchive: List[ArchiveNode]): Unit = {
		logger.trace(s"collecting old page nodes from $oldArchive")
		val oldPageNodes = oldArchive.collect { case pn: PageNode => pn }

		def reuseDescriptionFor(page: PageNode) = {
			logger.trace(s"search reusable description for $page")
			for {
				matchingOldPage <- oldPageNodes.find(oldPage => oldPage.pointerDescription === page.pointerDescription)
				oldDescription <- matchingOldPage.describes
			} {
				logger.trace(s"found description $oldDescription for $page")
				page.describes = oldDescription
			}
		}

		logger.trace(s"reusing descriptions for $newArchivePart")
		newArchivePart.flatten.foreach {
			case newPage: PageNode => reuseDescriptionFor(newPage)
			case _ => ()
		}
	}

	def replace(oldArchive: Option[ArchiveNode], description: Description): Option[ArchiveNode] = {
		logger.trace(s"replace $oldArchive with $description")
		val newArchivePart = create(description)
		logger.trace(s"extracting relevant parts from $oldArchive")
		val oldRelevantPart = ArchiveNode.flatten(List[ArchiveNode](), oldArchive.toList, shallow = true)
		logger.trace(s"reuse descriptions from $oldRelevantPart in $newArchivePart")
		newArchivePart.foreach(reuseOldDescriptions(_, oldRelevantPart))
		logger.trace(s"delete $oldRelevantPart")
		deletePart(oldRelevantPart)
		logger.trace(s"done replace $oldArchive with $description")
		newArchivePart
	}

	def initialDescription(cn: CollectionNode, description: Description): Option[ArchiveNode] = {
		val archive = ArchiveNode(cn)
		if (description.describes(archive)) archive
		else {
			val newArch = replace(archive, description)
			newArch.foreach { an => cn.self.createRelationshipTo(an.self, rel.archive) }
			newArch
		}
	}

	def applyDescription(pn: PageNode, description: Description): Option[ArchiveNode] = {
		if (description.describes(pn.describes)) pn.describes
		else {
			val newDescription = replace(pn.describes, description)
			newDescription.foreach { an => pn.describes = an }
			newDescription
		}
	}

}
