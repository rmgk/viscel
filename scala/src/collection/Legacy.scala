package viscel.collection

import scalax.file.Path
import spray.http.{HttpResponse, HttpEntity, MediaTypes}

import scala.slick.session.Database
//import Database.threadLocalSession
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import Q.interpolation
import scala.slick.driver.SQLiteDriver.simple._

import scalax.io.Resource
import scalax.file.Path

import viscel.Element

class Legacy(val name: String) extends OrderedCollection {

	val folder = Path.fromString("../../viscel/cache").toRealPath()

	def last: Int = Legacy.last(name)

	def get(pos: Int): Element = Legacy.get(name, pos)

}

object Legacy {
	lazy val db = Database.forURL("jdbc:sqlite:collections.db", driver = "org.sqlite.JDBC")
	lazy val session = db.createSession()

	lazy val qcollections = sql"SELECT name FROM sqlite_master WHERE type='table'".as[String]

	def list: IndexedSeq[String] = qcollections.elements()(session).toIndexedSeq

	def apply(col: String): Legacy = new Legacy(col)

	def last(col: String): Int = {
		(Q[Int] + "SELECT MAX(position) FROM " + col).first()(session)
	}

	def get(col:String, pos: Int): Element = {
		implicit val getElement = GetResult{r =>
			Element.fromData(blob = r.<<, mediatype = r.<<, source = r.<<, origin = r.<<, alt = r.<<?, title = r.<<?, width = r.<<?, height = r.<<?)
		}
		val sel = Q[Element] + "SELECT sha1, type, src, page_url, alt, title, width, height FROM " + col + " WHERE position = " +? pos
		sel.first()(session)
	}

}
