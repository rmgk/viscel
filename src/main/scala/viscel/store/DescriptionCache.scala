package viscel.store

import java.nio.file.Path

import io.circe.generic.auto._
import viscel.shared.Vid
import viscel.shared.Description
import viscel.shared.CirceCodecs._



/** Caches the [[viscel.shared.Description.size]] so the
  * [[viscel.server.ContentLoader.descriptions]] can be efficiently computed.
  * That is, we cache the size. */
class DescriptionCache(cachedir: Path) {
  private val descriptionpath: Path = cachedir.resolve("descriptions.json")
  private var descriptionCache: Map[Vid, Description] =
    CirceStorage.load[Map[Vid, Description]](descriptionpath).getOrElse(Map())

  def invalidate(id: Vid): Unit = synchronized {
    descriptionCache = descriptionCache - id
  }

  def getOrElse(id: Vid)(orElse: => Description): Description = synchronized {
    descriptionCache.getOrElse(id, {
      val desc = orElse
      descriptionCache = descriptionCache.updated(id, desc)
      CirceStorage.store[Map[Vid, Description]](descriptionpath, descriptionCache)
      desc
    })
  }
}
