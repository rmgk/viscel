package viscel.store

import java.nio.file.Path

import viscel.crawl.RequestUtil
import viscel.narration.{Metarrator, Narrator, Narrators, Vid}
import viscel.scribe.Vurl
import viscel.shared.Log

import scala.collection.immutable.{Map, Set}
import scala.concurrent.Future

class NarratorCache(metaPath: Path, definitionsdir: Path) {


	private def calculateAll(): Set[Narrator] = Narrators.staticV2 ++ loadAll() ++ Vid.loadAll(definitionsdir)

	def updateCache(): Unit = {
		cached = calculateAll()
		narratorMap = all.map(n => n.id -> n).toMap
	}

	@volatile private var cached: Set[Narrator] = calculateAll()
	def all: Set[Narrator] = synchronized(cached)

	@volatile private var narratorMap: Map[String, Narrator] = all.map(n => n.id -> n).toMap
	def get(id: String): Option[Narrator] = narratorMap.get(id)


	def loadAll(): Set[Narrator] = synchronized(Narrators.metas.iterator.flatMap[Narrator](load(_)).toSet)

	def add(start: String, requestUtil: RequestUtil): Future[List[Narrator]] = {
		import requestUtil.ec
		def go[T <: Narrator](metarrator: Metarrator[T], url: Vurl): Future[List[Narrator]] =
			requestUtil.requestDocument(url).map { case (doc, _) =>
				val nars = metarrator.wrap(doc).get
				synchronized {
					save(metarrator, nars ++ load(metarrator))
					updateCache()
					nars
				}
			}

		try {
			Narrators.metas.map(m => (m, m.unapply(start)))
				.collectFirst { case (m, Some(uri)) => go(m, uri) }
				.getOrElse(Future.failed(new IllegalArgumentException(s"$start is not handled")))

		}
		catch {
			case e: Exception => Future.failed(e)
		}
	}


	private def path[T <: Narrator](metarrator: Metarrator[T]): Path = metaPath.resolve(s"${metarrator.id}.json")
	def load[T <: Narrator](metarrator: Metarrator[T]): Set[T] = {
		val json = Json.load[Set[T]](path(metarrator))(io.circe.Decoder.decodeTraversable(metarrator.decoder, implicitly))
		json.fold(x => x, err => {
			Log.warn(s"could not load ${path(metarrator)}: $err")
			Set()
		})
	}
	def save[T <: Narrator](metarrator: Metarrator[T], nars: List[T]): Unit = Json.store(path(metarrator), nars)(io.circe.Encoder.encodeList(metarrator.encoder))

}
