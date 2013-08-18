package viscel.store

import scala.slick.session.Database
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import Q.interpolation
import scala.slick.driver.SQLiteDriver.simple._
import com.typesafe.scalalogging.slf4j.Logging
import scala.language.implicitConversions
import viscel.Element
import org.neo4j.graphdb.Node

object LegacyAdapter {
	lazy val db = Database.forURL("jdbc:sqlite:../data/collections.db", driver = "org.sqlite.JDBC")
	lazy val session = db.createSession()

	lazy val qcollections = sql"SELECT name FROM sqlite_master WHERE type='table'".as[String]

	def list: IndexedSeq[String] = qcollections.elements()(session).toIndexedSeq

	def getAll(col:String): Seq[Element] = {
		implicit val getConversionElement = GetResult{r =>
			Element(blob = r.<<, mediatype = r.<<, source = r.<<, origin = r.<<, alt = r.<<?, title = r.<<?, width = r.<<?, height = r.<<?)
		}
		val sel = Q[Element] + "SELECT sha1, type, src, page_url, alt, title, width, height FROM " + col + " ORDER BY position ASC"
		sel.elements()(session).toList
	}

}

object LegacyImporter extends Logging {

	def main(args: Array[String]) = {
		sys.addShutdownHook{
			Neo.shutdown()
		}
		importAll()
	}

	def time[T](desc: String = "")(f: => T): T = {
		val start = System.currentTimeMillis()
		val res = f
		logger.info(s"$desc took ${System.currentTimeMillis - start}ms")
		res
	}

	def importAll() = {
		val collections = LegacyAdapter.list

		time("everything") {
			for (id <- collections) {
				time("total") {
					Neo.tx{ _ =>
						logger.info(id)
						val node = Collection.create(id)
						fillCollection(Collection(id))
					}
				}
			}
		}
	}

	def fillCollection(col: Collection) = {
		val elts = time("load elements") {LegacyAdapter.getAll(col.id).toList}
		logger.info(elts.size.toString)

		def createLinkedElts(elts: List[Element], last: Node): Unit = elts match {
			case head :: tail => createLinkedElts(tail, col.add(head, Some(last)))
			case List() =>
		}

		if (!elts.isEmpty) {
			time("create elements") {
				val first = col.add(elts.head, None)
				createLinkedElts(elts.tail, first)
			}
		}
	}
}
