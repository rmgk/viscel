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

class Memory(val name: String) extends OrderedCollection {

	private[this] var elements = Vector[Element]()

	def last: Int = elements.size

	def get(pos: Int): Element = elements(pos - 1)

	def store(pos: Int, el: Element) {
		elements :+= el
	}

}
