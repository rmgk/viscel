package viscel.store

import java.nio.file.Path

import viscel.narration.{Metarrator, Narrator, NarratorADT, Narrators, ViscelDefinition}
import viscel.netzi.{VRequest, WebRequestInterface}
import viscel.selektiv.Narration
import viscel.selektiv.Narration.ContextData
import viscel.shared.{Log, Vid}
import viscel.store.v4.Vurl

import scala.collection.immutable.{Map, Set}
import scala.concurrent.Future

class NarratorCache(metaPath: Path, definitionsdir: Path) {


  private def calculateAll(): Set[Narrator] = loadAll() ++ ViscelDefinition.loadAll(definitionsdir)

  def updateCache(): Unit = {
    cached = calculateAll()
    narratorMap = all.map(n => n.id -> n).toMap
  }

  @volatile private var cached: Set[Narrator] = calculateAll()
  def all: Set[Narrator] = synchronized(cached)

  @volatile private var narratorMap: Map[Vid, Narrator] = all.map(n => n.id -> n).toMap
  def get(id: Vid): Option[Narrator] = narratorMap.get(id)


  def loadAll(): Set[Narrator] = synchronized(Narrators.metas.iterator.flatMap[NarratorADT](loadNarrators(_).iterator).toSet)

  def loadNarrators[T](metarrator: Metarrator[T]): Set[NarratorADT] = load(metarrator).map(metarrator.toNarrator)

  def add(start: String, requestUtil: WebRequestInterface): Future[List[Narrator]] = {
    def go[T](metarrator: Metarrator[T], url: Vurl): Future[List[Narrator]] =
      requestUtil.get(VRequest(url)).map { resp =>
        val respc = resp.copy(content = resp.content.fold(_ => throw new IllegalStateException(s"response for »$url« contains binary data"), identity))
        val contextData = ContextData(respc.content, Nil, respc.location.uriString())
        val nars = Narration.Interpreter(contextData).interpret(metarrator.wrap)
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


  private def path[T](metarrator: Metarrator[T]): Path = metaPath.resolve(s"${metarrator.metarratorId}.json")
  def load[T](metarrator: Metarrator[T]): Set[T] = {
    val json = CirceStorage.load[Set[T]](path(metarrator))(io.circe.Decoder.decodeSet(metarrator.decoder))
    json.fold(err => {
      Log.Store.trace(s"could not load ${path(metarrator)}: $err")
      Set()
    }, identity)
  }
  def save[T](metarrator: Metarrator[T], nars: List[T]): Unit = CirceStorage.store(path(metarrator), nars)(io.circe.Encoder.encodeList(metarrator.encoder))

}
