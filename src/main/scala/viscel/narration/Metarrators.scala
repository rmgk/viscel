package viscel.narration

import java.net.URL

import viscel.narration.narrators._
import viscel.scribe.Scribe

import scala.collection.Set
import scala.concurrent.Future

object Metarrators {
	val metas: List[Metarrator[_ <: NarratorV1]] = MangaHere.MetaCore :: Fakku.Meta :: Nil

	def cores(): Set[NarratorV1] = synchronized(metas.iterator.flatMap[NarratorV1](_.load()).toSet)

	def add(start: String, scribe: Scribe): Future[List[NarratorV1]] = {
		def go[T <: NarratorV1](metarrator: Metarrator[T], url: URL): Future[List[NarratorV1]] =
			scribe.sendReceive(scribe.util.request(url)).map { res =>
				val nars = metarrator.wrap(scribe.util.parseDocument(url)(res)).get
				synchronized {
					metarrator.save((metarrator.load() ++ nars).toList)
					Narrators.update()
					nars
				}
			}(scribe.ec)

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
