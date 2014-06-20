package viscel.server

import org.neo4j.graphdb.Direction
import spray.http.HttpResponse
import viscel.store.{Neo, UserNode, ViscelNode}

import scala.collection.JavaConverters._
import scalatags.Text._
import scalatags.Text.all._

class RawPage(user: UserNode, vnode: ViscelNode) extends HtmlPage {

	override def Title = vnode.toString

	override def bodyId = "raw"

	def mainPart = {
		val properties = Neo.txs { vnode.self.getPropertyKeys.asScala.toIndexedSeq.sorted.reverse.map { k => k -> (vnode.self.getProperty(k).toString: Node) } }
		div(class_info)(make_table(properties: _*)) :: Nil
	}

	def navigation = link_main("index") :: Nil

	def sidePart = {
		val outgoing = Neo.txs {
			vnode.self.getRelationships(Direction.OUTGOING).asScala.map { rel =>
				rel.getType.name -> ViscelNode(rel.getEndNode).fold(n => link_raw(n, n.toString), StringNode)
			}.toIndexedSeq.sortBy(_._1)
		}
		val incoming = Neo.txs {
			vnode.self.getRelationships(Direction.INCOMING).asScala.map { rel =>
				rel.getType.name -> ViscelNode(rel.getStartNode).fold(n => link_raw(n, n.toString), StringNode)
			}.toIndexedSeq.sortBy(_._1)
		}
		Seq[Node](fieldset(class_info)(legend("Outgoing"), make_table(outgoing: _*)),
			fieldset(class_info)(legend("Incoming"), make_table(incoming: _*)))
	}
}

object RawPage {
	def apply(user: UserNode, vnode: ViscelNode): HttpResponse = new RawPage(user, vnode).response
}
