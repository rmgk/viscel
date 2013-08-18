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
import viscel.store.Collection
import org.neo4j.graphdb.Node


class Legacy(val id: String) extends OrderedCollection {

	def name = id

	val collection = Collection(id)

	def last: Int = collection.size.toInt

	def get(pos: Int): Element = Element.fromNode(Collection.advance(pos-1)(collection.first.get).head)
}

object Legacy {
	def apply(id: String) = new Legacy(id)
}
