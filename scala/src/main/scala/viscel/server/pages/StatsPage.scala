package viscel.server.pages

import spray.can.server.Stats
import viscel.server.HtmlPage
import viscel.store.archive.Neo
import viscel.store.coin.{Config, User}
import viscel.store.{Vault, archive}

import scala.Predef.any2ArrowAssoc
import scalatags.Text.all._

class StatsPage(user: User, stats: Stats, neo: Neo) extends HtmlPage {

	override def Title = "Statistics"
	override def bodyId = "stats"

	def mainPart = {
		val cn = Vault.config()(neo)
		div(class_info)(make_table(
			"Downloaded :" -> cn.downloaded.toString,
			"Downloads :" -> cn.downloads.toString,
			"Compressed Downloads :" -> cn.downloadsCompressed.toString,
			"Failed Downloads :" -> cn.downloadsFailed.toString,
			"Collections :" -> neo.nodes(archive.label.Collection).size.toString,
			"Chapters : " -> neo.nodes(archive.label.Chapter).size.toString,
			"Elements : " -> neo.nodes(archive.label.Asset).size.toString,
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
