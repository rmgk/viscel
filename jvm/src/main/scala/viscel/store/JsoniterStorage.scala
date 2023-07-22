package viscel.store

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import viscel.shared.JsoniterCodecs.*

import java.nio.file.StandardOpenOption.*
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.util.Using

object JsoniterStorage {

  private val config = WriterConfig.withIndentionStep(2)

  def store[T: JsonValueCodec](p: Path, data: T): Unit =
    synchronized {
      val bytes = writeToArray(data, config)
      Files.createDirectories(p.getParent)
      Files.write(p, bytes, CREATE, WRITE, TRUNCATE_EXISTING)
      ()
    }

  def load[T: JsonValueCodec](p: Path): Either[Throwable, T] =
    synchronized {
      util.Try(readFromArray[T](Files.readAllBytes(p))).toEither
    }

  def writeLine[T: JsonValueCodec](file: Path, value: T): Unit = {
    val bytes = writeArray(value)
    Using(Files.newOutputStream(file, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) { os =>
      os.write(bytes)
      os.write('\n')
    }.get
  }

  implicit val UserCodec: JsonValueCodec[User] = JsonCodecMaker.make

}
