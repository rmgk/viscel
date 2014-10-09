package viscel.server

import spray.can.server.Stats
import spray.http.HttpResponse
import viscel.server.pages._
import viscel.store._
import viscel.store.nodes.{AssetNode, CollectionNode, UserNode}

object Pages {
	def apply(user: UserNode, vn: ViscelNode)(implicit neo: Neo) = vn match {
		case n @ CollectionNode(_) => front(user, n)
		case n @ AssetNode(_) => view(user, n)
	}

	def front(user: UserNode, collection: CollectionNode)(implicit neo: Neo): HttpResponse = new FrontPage(user, collection).response
	def index(user: UserNode)(implicit neo: Neo): HttpResponse = new IndexPage(user).response
	def raw(user: UserNode, vnode: ViscelNode)(implicit neo: Neo): HttpResponse = new RawPage(user, vnode).response
	def search(user: UserNode, text: String)(implicit neo: Neo): HttpResponse = new SearchPage(user, text).response
	def selection(user: UserNode)(implicit neo: Neo): HttpResponse = new SelectionPage(user).response
	def stats(user: UserNode, stats: Stats)(implicit neo: Neo): HttpResponse = new StatsPage(user, stats).response
	def view(user: UserNode, enode: AssetNode)(implicit neo: Neo): HttpResponse = new ViewPage(user, enode).response
}
