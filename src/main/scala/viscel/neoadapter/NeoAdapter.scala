package viscel.neoadapter

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import viscel.neoadapter.Config.ConfigNode
import viscel.scribe.{ScribeDataRow, Json}
import viscel.shared.Log

import scala.collection.JavaConverters._

object NeoAdapter {

	def apply(basedir: Path): NeoAdapter = {

		Files.createDirectories(basedir)

		val neo = new NeoInstance(basedir.resolve("db2").toString)


		val configNode = neo.tx { implicit ntx =>
			val cfg = Config.get()(ntx)
			if (cfg.version != 2) throw new IllegalStateException(s"config version not supported: ${cfg.version}")
			cfg
		}


		new NeoAdapter(
			basedir = basedir,
			neo = neo,
			cfg = configNode
		)
	}

}

class NeoAdapter(
	val basedir: Path,
	val neo: NeoInstance,
	val cfg: ConfigNode
) {

	def convertToAppendLog()(implicit w: upickle.default.Writer[ScribeDataRow]): Unit = {
		neo.tx { implicit ntx =>

			val dir = basedir.resolve("db3")
			val bookdir = dir.resolve("books")
			Files.createDirectories(bookdir)

			val stats = Map(
				"downloaded" -> cfg.downloaded.toString,
				"downloads" -> cfg.downloads.toString,
				"compressed" -> cfg.downloadsCompressed.toString,
				"failed" -> cfg.downloadsFailed.toString)

			Json.store(dir.resolve("config.json"), stats)

			val allBooks = ntx.nodes(label.Book).map { n => Book.apply(n) }
			allBooks.foreach { (book: Book) =>
				val id = book.id
				Log.info(s"make append log for $id")
				val entries = BookToAppendLog.bookToEntries(book)(ntx)

				val encoded: List[String] = entries.map(upickle.default.write[ScribeDataRow](_))
				val output: List[String] = upickle.default.write[String](book.name(ntx)) :: encoded
				Files.write(bookdir.resolve(s"$id"), output.asJava, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
			}
		}
	}

}
