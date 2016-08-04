package viscel.neoadapter

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import viscel.scribe.{Json, ScribeDataRow}
import viscel.shared.Log

import scala.collection.JavaConverters._

object NeoAdapter {

	def convertToAppendLog(db2dir: Path, db3dir: Path, configdir: Path)(implicit w: upickle.default.Writer[ScribeDataRow]): Unit = {

		Files.createDirectories(db2dir)

		val neo = new NeoInstance(db2dir.toString)

		val configNode = neo.tx { implicit ntx =>
			val cfg = Config.get()(ntx)
			if (cfg.version != 2) throw new IllegalStateException(s"config version not supported: ${cfg.version}")
			cfg
		}

		neo.tx { implicit ntx =>

			val stats = Map(
				"downloaded" -> configNode.downloaded.toString,
				"downloads" -> configNode.downloads.toString,
				"compressed" -> configNode.downloadsCompressed.toString,
				"failed" -> configNode.downloadsFailed.toString)

			Json.store(configdir.resolve("download-stats.json"), stats)

			val allBooks = ntx.nodes(label.Book).map { n => Book.apply(n) }
			allBooks.foreach { (book: Book) =>
				val id = book.id
				Log.info(s"make append log for $id")
				val entries = BookToAppendLog.bookToEntries(book)(ntx)

				val encoded: List[String] = entries.map(upickle.default.write[ScribeDataRow](_))
				val output: List[String] = upickle.default.write[String](book.name(ntx)) :: encoded
				Files.write(db3dir.resolve(s"$id"), output.asJava, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
			}
		}
		neo.shutdown()
	}

}
