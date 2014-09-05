package viscel.server

import spray.can.server.Stats
import spray.http.HttpResponse
import viscel.store.{ConfigNode, Neo, UserNode, label}

import scala.Predef.any2ArrowAssoc
import scalatags.Text.all._

class StatsPage(user: UserNode, stats: Stats) extends HtmlPage {

	override def Title = "Statistics"
	override def bodyId = "stats"

	def mainPart = {
		val cn = ConfigNode()
		div(class_info)(make_table(
			"Downloaded :" -> cn.downloaded.toString,
			"Downloads :" -> cn.downloads.toString,
			"Compressed Downloads :" -> cn.downloadsCompressed.toString,
			"Failed Downloads :" -> cn.downloadsFailed.toString,
			"Collections :" -> Neo.nodes(label.Collection).size.toString,
			"Chapters : " -> Neo.nodes(label.Chapter).size.toString,
			"Elements : " -> Neo.nodes(label.Asset).size.toString,
			"Uptime                : " -> stats.uptime.toString,
			"Total requests        : " -> stats.totalRequests.toString,
			"Open requests         : " -> stats.openRequests.toString,
			"Max open requests     : " -> stats.maxOpenRequests.toString,
			"Total connections     : " -> stats.totalConnections.toString,
			"Open connections      : " -> stats.openConnections.toString,
			"Max open connections  : " -> stats.maxOpenConnections.toString,
			"Requests timed out    : " -> stats.requestTimeouts.toString)) :: Nil
	}

	def navigation = link_main("index") :: Nil

	def sidePart = "" :: Nil
}

object StatsPage {
	def apply(user: UserNode, stats: Stats): HttpResponse = new StatsPage(user, stats).response
}
