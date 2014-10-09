package viscel.server

import spray.can.server.Stats
import spray.http.HttpResponse
import viscel.server.pages._
import viscel.store._
import viscel.store.coin.{Asset, Collection, User}

object Pages {
	def index(user: User)(implicit neo: Neo): HttpResponse = new IndexPage(user).response
	def search(user: User, query: String)(implicit neo: Neo): HttpResponse = new SearchPage(user, query).response
	def front(user: User, collection: Collection)(implicit neo: Neo): HttpResponse = new FrontPage(user, collection).response
	def view(user: User, asset: Asset)(implicit neo: Neo): HttpResponse = new ViewPage(user, asset).response
	def stats(user: User, stats: Stats)(implicit neo: Neo): HttpResponse = new StatsPage(user, stats).response
}
