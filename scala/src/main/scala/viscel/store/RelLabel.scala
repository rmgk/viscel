package viscel.store

import org.neo4j.graphdb.{Label, RelationshipType}

object label {

	sealed class SimpleLabel(val name: String) extends Label

	case object Blob extends SimpleLabel("Blob")
	case object Bookmark extends SimpleLabel("Bookmark")
	case object Chapter extends SimpleLabel("Chapter")
	case object Collection extends SimpleLabel("Collection")
	case object Config extends SimpleLabel("Config")
	case object Core extends SimpleLabel("Core")
	case object Asset extends SimpleLabel("Element")
	case object Page extends SimpleLabel("Archive")
	case object Unlabeled extends SimpleLabel("Unlabeled")
	case object User extends SimpleLabel("User")

}

object rel {

	sealed class SimpleRelationshipType(val name: String) extends RelationshipType

	case object archive extends SimpleRelationshipType("archive")
	case object blob extends SimpleRelationshipType("blob")
	case object bookmark extends SimpleRelationshipType("bookmark")
	case object bookmarked extends SimpleRelationshipType("bookmarked")
	case object bookmarks extends SimpleRelationshipType("bookmarks")
	case object describes extends SimpleRelationshipType("describes")
	case object first extends SimpleRelationshipType("first")
	case object last extends SimpleRelationshipType("last")
	case object narc extends SimpleRelationshipType("narc")
	case object skip extends SimpleRelationshipType("next")
	case object parent extends SimpleRelationshipType("parent")
	case object payload extends SimpleRelationshipType("payload")
	case object prev extends SimpleRelationshipType("prev")

}
