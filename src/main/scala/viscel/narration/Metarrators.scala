package viscel.narration

import viscel.crawl.RequestUtil
import viscel.narration.narrators._
import viscel.scribe.Vurl

import scala.collection.Set
import scala.concurrent.Future

object Metarrators {
	val metas: List[Metarrator[_ <: Narrator]] = MangaHere.MetaCore :: Mangafox.Meta :: Comicfury.Meta :: Nil

	def cores(): Set[Narrator] = synchronized(metas.iterator.flatMap[Narrator](_.load()).toSet)

	def add(start: String, requestUtil: RequestUtil): Future[List[Narrator]] = {
		import requestUtil.ec
		def go[T <: Narrator](metarrator: Metarrator[T], url: Vurl): Future[List[Narrator]] =
			requestUtil.request(url).flatMap(requestUtil.extractDocument(url)).map { res =>
				val nars = metarrator.wrap(res).get
				synchronized {
					metarrator.save(nars ++ metarrator.load())
					Narrators.update()
					nars
				}
			}

		try {
			metas.map(m => (m, m.unapply(start)))
				.collectFirst { case (m, Some(uri)) => go(m, uri) }
				.getOrElse(Future.failed(new IllegalArgumentException(s"$start is not handled")))

		}
		catch {
			case e: Exception => Future.failed(e)
		}
	}

}
