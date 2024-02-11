package viscel.store

import viscel.shared.{Description, JsoniterCodecs, Vid}

import java.nio.file.Path

/** Caches the [[viscel.shared.Description.size]] so the
  * [[viscel.server.ContentLoader.descriptions]] can be efficiently computed.
  * That is, we cache the size.
  */
class DescriptionCache(cachedir: Path) {
  private val descriptionpath: Path = cachedir.resolve("descriptions.json")
  private var descriptionCache: Map[Vid, Description] =
    JsoniterStorage.load[Map[Vid, Description]](descriptionpath)(using JsoniterCodecs.MapVidDescriptionCodec).getOrElse(Map())

  def invalidate(id: Vid): Unit =
    synchronized {
      descriptionCache = descriptionCache - id
    }

  def getOrElse(id: Vid)(orElse: => Description): Description =
    synchronized {
      descriptionCache.getOrElse(
        id, {
          val desc = orElse
          descriptionCache = descriptionCache.updated(id, desc)
          JsoniterStorage.store[Map[Vid, Description]](descriptionpath, descriptionCache)(
            using JsoniterCodecs.MapVidDescriptionCodec
          )
          desc
        }
      )
    }
}
