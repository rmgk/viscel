package viscel

import scala.slick.session.Database
//import Database.threadLocalSession
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import Q.interpolation
import scala.slick.driver.SQLiteDriver.simple._

import scalax.io.Resource
import scalax.file.Path

import java.security.MessageDigest

object Storage {
	lazy val db = Database.forURL("jdbc:sqlite:newcollections.db", driver = "org.sqlite.JDBC")
	lazy implicit val session = db.createSession()

	val folder = Path.fromString("../cache").toRealPath()

	def init {
		TableElements.ddl.create
		folder.createDirectory(createParents = false, failIfExists = true)
	}

	lazy val digester = MessageDigest.getInstance("SHA1")
	def sha1hex(b: Array[Byte]): String = (digester.digest(b) map {"%02X" format _}).mkString


	def put(els: Element*): Option[Int] = TableElements.insertAll(els: _*)
	def get(id: String): Option[String] = TableElements.filter(_.id === id).map(_.id).firstOption

	def store(blob: Array[Byte]): String = {
		val sha1 = sha1hex(blob)
		val path = (folder / sha1)
		if (!path.exists) path.write(blob)
		sha1
	}
	def fetch(sha1: String): Option[Array[Byte]] = {
		val path = (folder / sha1)
		if (path.exists) Some(path.byteArray)
		else None
	}

	object TableExperimental extends Table[(Int, String, String)]("Experimental") {
		def position = column[Int]("position")
		def state = column[String]("state")
		def element = column[String]("element")

		def elementFK = foreignKey("element_foreign", element, TableElements)(_.id)

		def * = (position ~ state ~ element)
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
}
