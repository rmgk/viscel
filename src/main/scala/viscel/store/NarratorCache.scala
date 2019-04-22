package viscel.store

import java.nio.file.Path

import viscel.narration.{Metarrator, Narrator, NarratorADT, Narrators, ViscelDefinition}
import viscel.selektiv.Narration.ContextData
import viscel.netzi.{VRequest, Vurl, WebRequestInterface}
import viscel.selektiv.Narration
import viscel.shared.{Log, Vid}

import scala.collection.immutable.{Map, Set}
import scala.concurrent.Future

class NarratorCache(metaPath: Path, definitionsdir: Path) {


  private def calculateAll(): Set[Narrator] = Narrators.staticV2 ++ loadAll() ++ ViscelDefinition.loadAll(definitionsdir)

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
        val respc = resp.copy(content = resp.content.right.get)
        val contextData = ContextData(respc.content, Nil, respc.location.uriString())
        val nars = Narration.Interpreter(contextData).interpret(metarrator.wrap).get
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
    val json = Json.load[Set[T]](path(metarrator))(io.circe.Decoder.decodeSet(metarrator.decoder))
    json.fold(x => x, err => {
      Log.Store.trace(s"could not load ${path(metarrator)}: $err")
      Set()
    })
  }
  def save[T](metarrator: Metarrator[T], nars: List[T]): Unit = Json.store(path(metarrator), nars)(io.circe.Encoder.encodeList(metarrator.encoder))

}
