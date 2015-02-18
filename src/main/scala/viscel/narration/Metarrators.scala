package viscel.narration

import java.net.URL

import viscel.narration.narrators._
import viscel.scribe.Scribe
import viscel.scribe.narration.Narrator

import scala.collection.Set
import scala.concurrent.Future

object Metarrators {
	val metas: List[Metarrator[_ <: Narrator]] = MangaHere.MetaCore :: Fakku.Meta :: Mangafox.Meta :: Comicfury.Meta :: Batoto.Meta :: Nil

	def cores(): Set[Narrator] = synchronized(metas.iterator.flatMap[Narrator](_.load()).toSet)

	def add(start: String, scribe: Scribe): Future[List[Narrator]] = {
		def go[T <: Narrator](metarrator: Metarrator[T], url: URL): Future[List[Narrator]] =
			scribe.sendReceive(scribe.util.request(url)).map { res =>
				val nars = metarrator.wrap(scribe.util.parseDocument(url)(res)).get
				synchronized {
					metarrator.save((nars ++ metarrator.load()).toList)
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
