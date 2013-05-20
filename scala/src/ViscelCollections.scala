import scala.slick.session.Database
//import Database.threadLocalSession
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import Q.interpolation
import scala.slick.driver.SQLiteDriver.simple._

import scalax.io.Resource
import scalax.file.Path

import java.security.MessageDigest

object DoConversion {
	def apply() = {
		val vc = ViscelCollections
		val nc = NewCollections
		val collections = vc.list
		nc.init
		collections foreach {col => nc.newCollection(col)}
		val colposelt = collections map {col => col -> vc.getall(col)}
		for ((col,poselt) <- colposelt) {
			nc.insert(poselt.view map {case (p, elt) => elt})
			nc.addOrderedCollection(col, poselt map {case (p, elt) => elt.id -> p})
		}
	}
}

object ViscelCollections {

	lazy val db = Database.forURL("jdbc:sqlite:collections.db", driver = "org.sqlite.JDBC")
	lazy val session = db.createSession()

	lazy val qcollections = sql"SELECT name FROM sqlite_master WHERE type='table'".as[String]

	def list: Seq[String] = qcollections.elements()(session).toSeq

	def last(col: String): Int = {
		(Q[Int] + "SELECT MAX(position) FROM " + col).first()(session)
	}

	def sha(col: String)(id: Int): String = {
		(Q[String] + "SELECT sha1 FROM " + col + " WHERE position = " +? id).first()(session)
	}

	lazy val digester = MessageDigest.getInstance("SHA1")

	def sha1hex(b: Array[Byte]) = (digester.digest(b) map {"%02X" format _}).mkString

	def getall(col: String): Seq[(Int, Element)] = {
		implicit val getElement = GetResult{r =>
			r.nextInt -> Element.fromOld(blob = r.<<, mediatype = r.<<, source = r.<<, origin = r.<<, alt = r.<<?, title = r.<<?, width = r.<<?, height = r.<<?)
		}
		val sel = Q[(Int, Element)] + "SELECT position, sha1, type, src, page_url, alt, title, width, height FROM " + col
		sel.elements()(session).toSeq
	}

	// def getFiles: IndexedSeq[String] = {
	// 	(Path.fromString("D:/wpc/export") * {(p: Path) => p.name.matches(""".*(jpe?g|gif|png|bmp)$""")}).toIndexedSeq map {p => p.name.toString } sorted
	// }

}

object NewCollections {
	lazy val db = Database.forURL("jdbc:sqlite:newcollections.db", driver = "org.sqlite.JDBC")
	lazy val session = db.createSession()

	def init {
		(TableElements.ddl ++
			TableOrderedCollections.ddl ++
			TableCollections.ddl
		).create(session)
	}

	def insert(els: Seq[Element]) = TableElements.insertAll(els: _*)(session)
	def newCollection(name: String) = TableCollections.insert(Collection(name, "ordered"))(session)
	def addOrderedCollection(col: String, positions: Seq[(String, Int)] ) = {
		val entries = positions map {case (id, pos) =>
			OrderedCollectionEntry(col, pos, id)
		}
		TableOrderedCollections.insertAll(entries: _*)(session)
	}
}

object TableElements extends Table[Element]("Elements") {
	def blob = column[String]("blob")
	def id = column[String]("id", O.PrimaryKey)
	def mediatype = column[String]("mediatype")
	def source = column[String]("source")
	def origin = column[String]("origin")
	def alt = column[Option[String]]("alt")
	def title = column[Option[String]]("title")
	def width = column[Option[Int]]("width")
	def height = column[Option[Int]]("height")

	def * = (blob ~ id ~ mediatype ~ source ~ origin ~ alt ~ title ~ width ~ height).<>(Element.apply _, Element.unapply _)
}

object TableOrderedCollections extends Table[OrderedCollectionEntry]("OrderedCollections") {
	def collection = column[String]("collection")
	def pos = column[Int]("pos")
	def element = column[String]("element")

	def elementFK = foreignKey("element_foreign", element, TableElements)(_.id)
	def collectionFK = foreignKey("collection_foreign", collection, TableCollections)(_.name)

	def pk = primaryKey("pk_a", (collection, pos))

	def * = (collection ~ pos ~ element).<>(OrderedCollectionEntry.apply _, OrderedCollectionEntry.unapply _)
}

object TableCollections extends Table[Collection]("Collections") {
	def name = column[String]("collection", O.PrimaryKey)
	def kind = column[String]("kind")

	def * = (name ~ kind).<>(Collection.apply _, Collection.unapply _)
}

case class OrderedCollectionEntry(collection: String, pos: Int, element: String)

case class Collection(name: String, kind: String)

case class Element(
	blob: String,
	id: String,
	mediatype: String,
	source: String,
	origin: String,
	alt: Option[String],
	title: Option[String],
	width: Option[Int],
	height: Option[Int]
	)

object Element {
	def fromOld(
		blob: String,
		mediatype: String,
		source: String,
		origin: String,
		alt: Option[String] = None,
		title: Option[String] = None,
		width: Option[Int] = None,
		height: Option[Int] = None
	): Element = {
		val idstring = (List(blob, mediatype, source, origin) ++ (List(alt, title, width, height) map {_.getOrElse("")})) mkString "\n"
		val id = ViscelCollections.sha1hex(idstring.getBytes("UTF8"))
		Element(id = id, blob = blob, mediatype = mediatype, source = source, origin = origin, alt = alt, title = title, width = width, height = height)
	}
}
