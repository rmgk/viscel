package viscel.store


import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Path}

import cats.syntax.either._
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import viscel.shared.JsoniterCodecs._

object JsoniterStorage {

  private val config = WriterConfig.withIndentionStep(2)

  def store[T: JsonValueCodec](p: Path, data: T): Unit = synchronized {
    val bytes = writeToArray(data, config)
    Files.createDirectories(p.getParent)
    Files.write(p, bytes, CREATE, WRITE, TRUNCATE_EXISTING)
  }

  def load[T: JsonValueCodec](p: Path): Either[Throwable, T] = synchronized {
    Either.catchNonFatal(readFromArray[T](Files.readAllBytes(p)))
  }

  implicit val UserCodec: JsonValueCodec[User] = JsonCodecMaker.make



}
