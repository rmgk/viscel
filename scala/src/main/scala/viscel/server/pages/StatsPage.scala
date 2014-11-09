package viscel.server.pages

import spray.can.server.Stats
import viscel.server.HtmlPage
import viscel.database.{Neo, Ntx, label}
import viscel.store.{User, Vault}

import scala.Predef.any2ArrowAssoc
import scalatags.Text.all._

class StatsPage(user: User, stats: Stats, neo: Neo)(implicit ntx: Ntx) extends HtmlPage {

	override def Title = "Statistics"
	override def bodyId = "stats"

	def mainPart = neo.tx { implicit ntx =>
		val cn = Vault.config()(ntx)
		div(class_info)(make_table(
			"Downloaded :" -> cn.downloaded.toString,
			"Downloads :" -> cn.downloads.toString,
			"Compressed Downloads :" -> cn.downloadsCompressed.toString,
			"Failed Downloads :" -> cn.downloadsFailed.toString,
			"Collections :" -> ntx.nodes(label.Collection).size.toString,
			"Chapters : " -> ntx.nodes(label.Chapter).size.toString,
			"Elements : " -> ntx.nodes(label.Asset).size.toString,
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
