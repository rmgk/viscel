package viscel.scribe.database

import org.neo4j.graphdb.{Label, RelationshipType}

object label {
	final case class SimpleLabel private[label](name: String) extends Label {
		override def toString = s"Label($name)"
	}
	object SimpleLabel {
		def apply(l: Label): SimpleLabel = apply(l.name)
	}

	val Asset = SimpleLabel("Asset")
	val Blob = SimpleLabel("Blob")
	val Book = SimpleLabel("Book")
	val Config = SimpleLabel("Config")
	val More = SimpleLabel("More")
}

object rel {
	final case class SimpleRel private[rel](name: String) extends RelationshipType {
		override def toString = s"Relation($name)"
	}
	object SimpleRel {
		def apply(r: RelationshipType): SimpleRel = apply(r.name)
	}

	val blob = SimpleRel("blob")
	val describes = SimpleRel("describes")
	val narc = SimpleRel("narc")
}
