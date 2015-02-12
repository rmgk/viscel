package viscel.narration

import spray.client.pipelining.SendReceive
import viscel.crawler.RunnerUtil
import viscel.narration.narrators._
import viscel.shared.ViscelUrl

import scala.collection.Set
import scala.concurrent.{ExecutionContext, Future}

object Metarrators {
	val metas: List[Metarrator[_ <: Narrator]] = MangaHere.MetaCore :: Fakku.Meta :: Nil

	def cores(): Set[Narrator] = synchronized(metas.iterator.flatMap[Narrator](_.load()).toSet)

	def add(start: String, iopipe: SendReceive)(implicit ec: ExecutionContext): Future[List[Narrator]] = {
		def go[T <: Narrator](metarrator: Metarrator[T], url: ViscelUrl): Future[List[Narrator]] = iopipe(RunnerUtil.request(url)).map { res =>
			val nars = metarrator.wrap(RunnerUtil.parseDocument(url)(res)).get
			synchronized {
				metarrator.save((metarrator.load() ++ nars).toList)
				Narrators.update()
				nars
			}
		}

		try {
			val url = SelectUtil.stringToVurl(start)
			metas.map(m => (m, m.unapply(url)))
				.collectFirst { case (m, Some(uri)) => go(m, uri) }
				.getOrElse(Future.failed(new IllegalArgumentException(s"$url is not handled")))

		}
		catch {
			case e: Exception => Future.failed(e)
		}
	}

}
