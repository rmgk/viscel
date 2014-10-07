package viscel.server

import viscel.server.pages.{ViewPage, FrontPage}
import viscel.store._

object PageDispatcher {
	def apply(user: UserNode, vn: ViscelNode) = vn match {
		// case n: ChapterNode => ChapterPage(user, n.collection)
		case n: CollectionNode => FrontPage(user, n)
		//case n: ChapteredCollectionNode => ChapteredFrontPage(user, n)
		case n: AssetNode => ViewPage(user, n)
		//case n: UserNode => IndexPage(user)
	}
}
