package viscel.narration

import spray.client.pipelining.SendReceive
import viscel.crawler.RunnerUtil
import viscel.narration.narrators.{Fakku, MangaHere, CloneManga}
import viscel.shared.ViscelUrl

import scala.collection.Set
import scala.concurrent.{Future, ExecutionContext}

object Metarrators {
	def cores(): Set[Narrator] = synchronized(Set.empty[Narrator] ++ CloneManga.MetaClone.load() ++ MangaHere.MetaCore.load() ++ Fakku.Meta.load())

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
			SelectUtil.stringToVurl(start) match {
				case Fakku.Meta(url) => go(Fakku.Meta, url)
				case MangaHere.MetaCore(url) => go(MangaHere.MetaCore, url)
			}
		}
		catch {
			case e: Exception => Future.failed(e)
		}
	}

}
