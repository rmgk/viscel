package viscel.narration

import java.nio.file.Path

import viscel.Viscel
import viscel.crawl.RequestUtil
import viscel.narration.narrators._
import viscel.scribe.Vurl
import viscel.shared.Log
import viscel.store.Json

import scala.collection.Set
import scala.concurrent.Future

object Metarrators {
	val metas: List[Metarrator[_ <: Narrator]] = MangaHere.MetaCore :: Mangafox.Meta :: Comicfury.Meta :: Nil

	def cores(): Set[Narrator] = synchronized(metas.iterator.flatMap[Narrator](load(_)).toSet)

	def add(start: String, requestUtil: RequestUtil): Future[List[Narrator]] = {
		import requestUtil.ec
		def go[T <: Narrator](metarrator: Metarrator[T], url: Vurl): Future[List[Narrator]] =
			requestUtil.request(url).flatMap(requestUtil.extractDocument(url)).map { res =>
				val nars = metarrator.wrap(res).get
				synchronized {
					save(metarrator, nars ++ load(metarrator))
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


	private def path[T <: Narrator](metarrator: Metarrator[T]): Path = Viscel.services.metarratorconfigdir.resolve(s"${metarrator.id}.json")
	def load[T <: Narrator](metarrator: Metarrator[T]): Set[T] = {
		val json = Json.load[Set[T]](path(metarrator))(io.circe.Decoder.decodeTraversable(metarrator.decoder, implicitly))
		json.fold(x => x, err => {
			Log.warn(s"could not load ${path(metarrator)}: $err")
			Set()
		})
	}
	def save[T <: Narrator](metarrator: Metarrator[T], nars: List[T]): Unit = Json.store(path(metarrator), nars)(io.circe.Encoder.encodeList(metarrator.encoder))

}
