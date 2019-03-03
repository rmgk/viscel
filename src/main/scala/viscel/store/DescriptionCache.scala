package viscel.store

import java.nio.file.Path

import io.circe.generic.auto._
import viscel.shared.Vid
import viscel.shared.Description


/** Caches the [[viscel.shared.Description.size]] so the
  * [[viscel.server.ContentLoader.descriptions]] can be efficiently computed. */
class DescriptionCache(cachedir: Path) {
  private val descriptionpath: Path = cachedir.resolve("descriptions.json")
  private var descriptionCache: Map[Vid, Description] =
    Json.load[Map[Vid, Description]](descriptionpath).getOrElse(Map())

  def invalidate(id: Vid): Unit = synchronized {
    descriptionCache = descriptionCache - id
  }

  def updateSize(id: Vid, sizeDelta: Int): Unit = synchronized {
    descriptionCache.get(id).foreach { desc =>
      descriptionCache = descriptionCache.updated(id, desc.copy(size = desc.size + sizeDelta))
    }
    Json.store[Map[Vid, Description]](descriptionpath, descriptionCache)
  }

  def getOrElse(id: Vid)(orElse: => Description): Description = synchronized {
    descriptionCache.getOrElse(id, {
      val desc = orElse
      descriptionCache = descriptionCache.updated(id, desc)
      Json.store[Map[Vid, Description]](descriptionpath, descriptionCache)
      desc
    })
  }
}
