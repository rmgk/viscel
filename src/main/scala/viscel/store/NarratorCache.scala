package viscel.store

import java.nio.file.Path

import org.jsoup.Jsoup
import viscel.crawl.{VRequest, WebRequestInterface}
import viscel.narration.interpretation.NarrationInterpretation
import viscel.narration.interpretation.NarrationInterpretation.NarratorADT
import viscel.narration.{Metarrator, Narrator, Narrators, Vid}
import viscel.scribe.{Link, Vurl}
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


  def loadAll(): Set[Narrator] = synchronized(Narrators.metas.iterator.flatMap[NarratorADT](loadNarrators(_).iterator).toSet)

  def loadNarrators[T](metarrator: Metarrator[T]): Set[NarratorADT] = load(metarrator).map(metarrator.toNarrator)

  def add(start: String, requestUtil: WebRequestInterface): Future[List[Narrator]] = {
    def go[T](metarrator: Metarrator[T], url: Vurl): Future[List[Narrator]] =
      requestUtil.get(VRequest.Text(url)(_ => Nil)).map { resp =>
        val nars = NarrationInterpretation.interpret(metarrator.wrap,
                                                     Jsoup.parse(resp.content, resp.location.uriString()),
                                                     Link(url)).get
        synchronized {
          save(metarrator, nars ++ load(metarrator))
          updateCache()
          nars.map(metarrator.toNarrator)
        }
      }(scala.concurrent.ExecutionContext.global)

    try {
      Narrators.metas.map(m => (m, m.unapply(start)))
        .collectFirst { case (m, Some(uri)) => go(m, uri) }
        .getOrElse(Future.failed(new IllegalArgumentException(s"$start is not handled")))

    }
    catch {
      case e: Exception => Future.failed(e)
    }
  }


  private def path[T](metarrator: Metarrator[T]): Path = metaPath.resolve(s"${metarrator.id}.json")
  def load[T](metarrator: Metarrator[T]): Set[T] = {
    val json = Json.load[Set[T]](path(metarrator))(io.circe.Decoder.decodeTraversable(metarrator.decoder, implicitly))
    json.fold(x => x, err => {
      Log.Store.warn(s"could not load ${path(metarrator)}: $err")
      Set()
    })
  }
  def save[T](metarrator: Metarrator[T], nars: List[T]): Unit = Json.store(path(metarrator), nars)(io.circe.Encoder.encodeList(metarrator.encoder))

}
