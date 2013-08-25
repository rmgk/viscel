package viscel.store

import org.neo4j.graphdb.Label
import org.neo4j.graphdb.RelationshipType

package label {
	class SimpleLabel(val name: String) extends Label

	case object Collection extends SimpleLabel("Collection")
	case object Element extends SimpleLabel("Element")
	case object User extends SimpleLabel("User")
	case object Bookmark extends SimpleLabel("Bookmark")
}

package rel {
	class SimpleRelationshipType(val name: String) extends RelationshipType

	case object next extends SimpleRelationshipType("next")
	case object prev extends SimpleRelationshipType("prev")
	case object parent extends SimpleRelationshipType("parent")
	case object bookmarked extends SimpleRelationshipType("bookmarked")
	case object bookmarks extends SimpleRelationshipType("bookmarks")
	case object bookmark extends SimpleRelationshipType("bookmark")
	case object first extends SimpleRelationshipType("first")
	case object last extends SimpleRelationshipType("last")

}
