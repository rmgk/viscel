package viscel.neoadapter

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import viscel.neoadapter.appendlog.{AppendLogEntry, BookToAppendLog}
import viscel.neoadapter.database.{Books, NeoInstance, label}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

object NeoAdapter {

	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		Console.println(s"$desc took ${(System.nanoTime - start) / 1000000.0} ms")
		res
	}

	def apply(basedir: Path, executionContext: ExecutionContext): NeoAdapter = {

		Files.createDirectories(basedir)

		val neo = new NeoInstance(basedir.resolve("db2").toString)

		neo.tx { implicit ntx =>
			if (!neo.db.schema().getConstraints(label.Book).iterator().hasNext)
				neo.db.schema().constraintFor(label.Book).assertPropertyIsUnique("id").create()
		}



		new NeoAdapter(
			basedir = basedir,
			neo = neo,
			ec = executionContext
		)
	}

}

class NeoAdapter(
	val basedir: Path,
	val neo: NeoInstance,
	val ec: ExecutionContext
	) {

	val books = new Books(neo)


	def convertToAppendLog()(implicit w: upickle.default.Writer[AppendLogEntry]): Unit = {
		val dir = basedir.resolve("db3")
		Files.createDirectories(dir)
		books.all().foreach { book =>
			val id = neo.tx { implicit ntx => book.id }
			Log.info(s"make append log for $id")
			val entries = neo.tx { ntx => BookToAppendLog.bookToEntries(book)(ntx) }

			val encoded = entries.map(upickle.default.write[AppendLogEntry](_))
			Files.write(dir.resolve(s"$id.json"), encoded.asJava, StandardCharsets.UTF_8, StandardOpenOption.CREATE)
		}
	}
}
