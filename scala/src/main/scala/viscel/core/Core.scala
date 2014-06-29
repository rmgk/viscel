package viscel.core

import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import viscel.description._
import viscel.store._
import viscel.wrapper._

trait Core {
	def id: String
	def name: String
	def archive: List[Description]
	def wrap(doc: Document, pd: Pointer): List[Description]
	override def equals(other: Any) = other match {
		case o: Core => id === o.id
		case _ => false
	}
	override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}

object Core {
	def metaCores: Set[Core] = Neo.nodes(label.Core).map(CoreNode(_)).map { core =>
		core.kind match {
			case "CloneManga" => CloneManga.getCore(core.description)
			case "MangaHere" => MangaHere.getCore(core.description)
		}
	}.toSet
	def availableCores: Set[Core] = KatBox.cores() ++ PetiteSymphony.cores() ++ WordpressEasel.cores() ++ metaCores ++ staticCores
	def get(id: String) = availableCores.find(_.id === id)

	val collectionCache = scala.collection.concurrent.TrieMap[String, CollectionNode]()

	def getCollection(core: Core): CollectionNode = collectionCache.getOrElseUpdate(core.id, {
		val collection = CollectionNode.getOrCreate(core)
		if (collection.name !== core.name) collection.name = core.name
		collection
	})

	val staticCores = Set(MangaHere.MetaCore, CloneManga.MetaClone, Flipside, Everafter, CitrusSaburoUta, Misfile, Twokinds, JayNaylor.BetterDays, JayNaylor.OriginalLife)
}
