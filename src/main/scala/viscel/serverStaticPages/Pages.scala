package viscel.serverStaticPages

import spray.can.server.Stats
import spray.http.HttpResponse
import viscel.serverStaticPages.pages._
import viscel.store.{User, Vault}
import viscel.database.{Neo, Ntx}
import viscel.store.coin.{Asset, Collection}

object Pages {
	def index(user: User)(implicit neo: Neo): HttpResponse = neo.txt(s"create response index") { new IndexPage(user)(_).response  }
	def search(user: User, query: String)(implicit neo: Neo): HttpResponse = neo.txt(s"create response index") { new SearchPage(user, query)(_).response }
	def front(user: User, collection: Collection)(implicit neo: Neo): HttpResponse = neo.txt(s"create response index") { new FrontPage(user, collection)(_).response }
	def view(user: User, asset: Asset)(implicit neo: Neo): HttpResponse = neo.txt(s"create response index") { new ViewPage(user, asset)(_).response }
	def stats(user: User, stats: Stats)(implicit neo: Neo): HttpResponse = neo.txt(s"create response index") { new StatsPage(user, stats, neo)(_).response }
}
