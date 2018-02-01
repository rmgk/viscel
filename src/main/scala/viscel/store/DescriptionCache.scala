package viscel.store

import java.nio.file.Path

import io.circe.generic.auto._
import viscel.shared.Description


/** Caches the [[viscel.shared.Description.size]] so the [[viscel.server.ServerPages.narrations]] can be efficiently computed. */
class DescriptionCache(configdir: Path) {
  private val descriptionpath: Path = configdir.resolve("descriptions.json")
  private var descriptionCache: Map[String, Description] =
    Json.load[Map[String, Description]](descriptionpath).getOrElse(Map())

  def invalidate(id: String): Unit = synchronized {
    descriptionCache = descriptionCache - id
  }

  def updateSize(id: String, sizeDelta: Int): Unit = synchronized {
    descriptionCache.get(id).foreach { desc =>
      descriptionCache = descriptionCache.updated(id, desc.copy(size = desc.size + sizeDelta))
    }
    Json.store[Map[String, Description]](descriptionpath, descriptionCache)
  }

  def getOrElse(id: String)(orElse: => Description): Description = synchronized {
    descriptionCache.getOrElse(id, {
      val desc = orElse
      descriptionCache = descriptionCache.updated(id, desc)
      Json.store[Map[String, Description]](descriptionpath, descriptionCache)
      desc
    })
  }
}
