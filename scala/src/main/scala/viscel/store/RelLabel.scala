package viscel.store

import org.neo4j.graphdb.{Label, RelationshipType}

object label {
	final case class SimpleLabel private[label](name: String) extends Label {
		override def toString = s"Label($name)"
	}
	object SimpleLabel {
		def apply(l: Label): SimpleLabel = apply(l.name)
	}

	val Asset = SimpleLabel("Element")
	val Blob = SimpleLabel("Blob")
	val Bookmark = SimpleLabel("Bookmark")
	val Chapter = SimpleLabel("Chapter")
	val Collection = SimpleLabel("Collection")
	val Config = SimpleLabel("Config")
	val Core = SimpleLabel("Core")
	val Page = SimpleLabel("Archive")
	val Unlabeled = SimpleLabel("Unlabeled")
	val User = SimpleLabel("User")
}

object rel {
	final case class SimpleRel private[rel](name: String) extends RelationshipType {
		override def toString = s"Relation($name)"
	}
	object SimpleRel {
		def apply(r: RelationshipType): SimpleRel = apply(r.name)
	}

	val archive = SimpleRel("archive")
	val blob = SimpleRel("blob")
	val bookmark = SimpleRel("bookmark")
	val bookmarked = SimpleRel("bookmarked")
	val bookmarks = SimpleRel("bookmarks")
	val describes = SimpleRel("describes")
	val first = SimpleRel("first")
	val last = SimpleRel("last")
	val metadata = SimpleRel("metadata")
	val narc = SimpleRel("narc")
	val parent = SimpleRel("parent")
	val prev = SimpleRel("prev")
	val skip = SimpleRel("next")
}
