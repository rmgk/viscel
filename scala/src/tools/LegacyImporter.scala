package viscel.tools

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.graphdb.Node
import scala.language.implicitConversions
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{ GetResult, StaticQuery => Q }
import scala.slick.session.Database
import viscel.store._
import viscel.time

class LegacyAdapter(dbdir: String) {
	lazy val db = Database.forURL(s"jdbc:sqlite:$dbdir", driver = "org.sqlite.JDBC")
	lazy val session = db.createSession()

	lazy val qcollections = sql"SELECT name FROM sqlite_master WHERE type='table'".as[String]

	def list: IndexedSeq[String] = qcollections.elements()(session).toIndexedSeq

	def getAll(col: String): Seq[LegacyElement] = {
		implicit val getConversionElement = GetResult { r =>
			LegacyElement(blob = r.<<, mediatype = r.<<, source = r.<<, origin = r.<<, alt = r.<<?, title = r.<<?, width = r.<<?, height = r.<<?)
		}
		val sel = Q[LegacyElement] + "SELECT sha1, type, src, page_url, alt, title, width, height FROM " + col + " ORDER BY position ASC"
		sel.elements()(session).toList
	}

}

class LegacyImporter(dbdir: String) extends Logging {

	val legacyAdapter = new LegacyAdapter(dbdir)

	def importAll() = {
		time("everything") {
			val collections = legacyAdapter.list
			// val bookmarks = getBookmarks
			for (id <- collections) {
				Neo.txt("total") { _ =>
					logger.info(id)
					val cn = CollectionNode.create(id)
					fillCollection(cn)
					// bookmarks.get(id).foreach { cn.bookmark(_) }
				}
			}
			legacyAdapter.session.close()
		}
	}

	def fillCollection(col: CollectionNode) = {
		val elts = time("load elements") { legacyAdapter.getAll(col.id).toList }
		logger.info(elts.size.toString)

		def createLinkedElts(elts: List[LegacyElement]): Unit = elts match {
			case head :: tail =>
				val newElem = ElementNode.create(head.toSeq: _*)
				col.append(newElem)
				createLinkedElts(tail)
			case List() =>
		}

		if (!elts.isEmpty) {
			time("create elements") {
				val first = ElementNode.create(elts.head.toSeq: _*)
				col.append(first)
				createLinkedElts(elts.tail)
			}
		}
	}
}

object BookmarkImporter extends Logging {
	def apply(user: UserNode, bmdir: String) = {
		val extract = """(?x) \s+ (\w+) = (\d+)""".r
		scala.io.Source.fromFile(bmdir).getLines
			.collect { case extract(id, pos) => id -> pos.toInt }.foreach {
				case (id, pos) =>
					CollectionNode(id).map { cn =>
						cn(pos).map { en =>
							logger.info(s"$id: $pos")
							user.setBookmark(en)
						}.getOrElse { logger.warn("$id has no element $pos") }
					}.getOrElse { logger.warn(s"unknown id $id") }
			}

	}
}

case class LegacyElement(
	source: String,
	origin: String,
	blob: String,
	mediatype: String,
	alt: Option[String] = None,
	title: Option[String] = None,
	width: Option[Int] = None,
	height: Option[Int] = None) {

	def toSeq = {
		Seq("source" -> source,
			"origin" -> origin,
			"blob" -> blob,
			"mediatype" -> mediatype) ++
			alt.map { "alt" -> _ } ++
			title.map { "title" -> _ } ++
			width.map { "width" -> _ } ++
			height.map { "height" -> _ }
	}
}
