package viscel.server

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scalatags._
import spray.can.server.Stats
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel._
import viscel.store.CollectionNode
import viscel.store.ConfigNode
import viscel.store.ElementNode
import viscel.store.Neo
import viscel.store.label
import viscel.store.UserNode
import viscel.store.ViscelNode
import viscel.store.{ Util => StoreUtil }

class StatsPage(user: UserNode, stats: Stats) extends HtmlPage {

	override def Title = "Statistics"
	override def bodyId = "stats"

	def mainPart = {
		val cn = ConfigNode()
		div.cls("info")(make_table(
			"Downloaded :" -> cn.downloaded.toString,
			"Downloads :" -> cn.downloads.toString,
			"Compressed Downloads :" -> cn.downloadsCompressed.toString,
			"Failed Downloads :" -> cn.downloadsFailed.toString,
			"Collections :" -> Neo.nodes(label.Collection).size.toString,
			"Chapters : " -> Neo.nodes(label.Chapter).size.toString,
			"Elements : " -> Neo.nodes(label.Element).size.toString,
			"Uptime                : " -> stats.uptime.toString,
			"Total requests        : " -> stats.totalRequests.toString,
			"Open requests         : " -> stats.openRequests.toString,
			"Max open requests     : " -> stats.maxOpenRequests.toString,
			"Total connections     : " -> stats.totalConnections.toString,
			"Open connections      : " -> stats.openConnections.toString,
			"Max open connections  : " -> stats.maxOpenConnections.toString,
			"Requests timed out    : " -> stats.requestTimeouts.toString))
	}

	def navigation = link_main("index")

	def sidePart = ""
}

object StatsPage {
	def apply(user: UserNode, stats: Stats): HttpResponse = new StatsPage(user, stats).response
}
