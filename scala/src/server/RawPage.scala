package viscel.server

import org.neo4j.graphdb.Direction
import scalatags._
import spray.http.HttpResponse
import viscel.store.Neo
import viscel.store.UserNode
import viscel.store.ViscelNode
import viscel.store.{ Util => StoreUtil }
import scala.collection.JavaConversions._

class RawPage(user: UserNode, vnode: ViscelNode) extends HtmlPage {

	override def Title = vnode.toString
	override def bodyId = "raw"

	def mainPart = {
		val properties = Neo.txs { vnode.self.getPropertyKeys.toIndexedSeq.sorted.reverse.map { k => k -> (vnode.self.getProperty(k).toString: STag) } }
		div.cls("info")(make_table(properties: _*))
	}

	def navigation = link_main("index")

	def sidePart = {
		val outgoing = Neo.txs {
			vnode.self.getRelationships(Direction.OUTGOING).map { rel =>
				rel.getType.name.toString -> ViscelNode(rel.getEndNode).map { n => link_raw(n, n.toString) }.recover { case e => e.getMessage: STag }.get
			}.toIndexedSeq.sortBy(_._1)
		}
		val incoming = Neo.txs {
			vnode.self.getRelationships(Direction.INCOMING).map { rel =>
				rel.getType.name.toString -> ViscelNode(rel.getStartNode).map { n => link_raw(n, n.toString) }.recover { case e => e.getMessage: STag }.get
			}.toIndexedSeq.sortBy(_._1)
		}
		Seq[STag](fieldset.cls("info")(legend("Outgoing"), make_table(outgoing: _*)),
			fieldset.cls("info")(legend("Incoming"), make_table(incoming: _*)))
	}
}

object RawPage {
	def apply(user: UserNode, vnode: ViscelNode): HttpResponse = new RawPage(user, vnode).response
}
