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
import viscel.store.Neo
import org.neo4j.graphdb.Node


class Legacy(val name: String) extends OrderedCollection {

	val collectionNode = {
		Neo.execute("""
			|match col: Collection
			|where col.id = {name}
			|return col
			""".stripMargin.trim,
			"name" -> name).next.apply("col").asInstanceOf[Node]
	}

	def last: Int = {
		Neo.execute("""
			|start col = node({id})
			|match (col) -[r:page]-> (page: Page), (something)
			|where not( (page) -[:next]-> (something) )
			|return r.num
			""".stripMargin.trim,
			"id" -> collectionNode.getId
			).next.apply("r.num").asInstanceOf[Int]
	}

	def get(pos: Int): Element = {
		val node = Neo.execute("""
			|start col = node({id})
			|match (col) -[r:page]-> (page: Page)
			|where r.num = {pos}
			|return page
			""".stripMargin.trim,
			"id" -> collectionNode.getId,
			"pos" -> pos
			).next.apply("page").asInstanceOf[Node]
		Neo.tx{ _ =>
			Element(
				blob = node.getProperty("blob").asInstanceOf[String],
				mediatype = node.getProperty("mediatype").asInstanceOf[String],
				source = node.getProperty("source").asInstanceOf[String],
				origin = node.getProperty("origin").asInstanceOf[String],
				alt = Option(node.getProperty("alt", null).asInstanceOf[String]),
				title = Option(node.getProperty("title", null).asInstanceOf[String]),
				width = Option(node.getProperty("width", null).asInstanceOf[Int]),
				height = Option(node.getProperty("height", null).asInstanceOf[Int])
			)
		}
	}

}

object Legacy {
	def apply(name: String) = new Legacy(name)
}
