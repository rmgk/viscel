package viscel.narration

import viscel.crawl.Crawl
import viscel.narration.narrators._
import viscel.scribe.{Narrator, Vuri}

import scala.collection.Set
import scala.concurrent.Future

object Metarrators {
	val metas: List[Metarrator[_ <: Narrator]] = MangaHere.MetaCore :: Mangafox.Meta :: Comicfury.Meta :: Batoto.Meta :: KissManga.Meta :: Nil

	def cores(): Set[Narrator] = synchronized(metas.iterator.flatMap[Narrator](_.load()).toSet)

	def add(start: String, crawl: Crawl): Future[List[Narrator]] = {
		def go[T <: Narrator](metarrator: Metarrator[T], url: Vuri): Future[List[Narrator]] =
			crawl.iopipe(crawl.util.request(url)).map { res =>
				val nars = metarrator.wrap(crawl.util.parseDocument(url)(res)).get
				synchronized {
					metarrator.save(nars ++ metarrator.load())
					Narrators.update()
					nars
				}
			}(crawl.executionContext)

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
