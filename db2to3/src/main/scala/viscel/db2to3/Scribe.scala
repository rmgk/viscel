package viscel.db2to3

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import viscel.db2to3.appendlog.{AppendLogEntry, BookToAppendLog}
import viscel.db2to3.database.{Books, NeoInstance, label}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

object Scribe {

	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		Console.println(s"$desc took ${(System.nanoTime - start) / 1000000.0} ms")
		res
	}

	def apply(basedir: Path, system: ActorSystem, materializer: Materializer, executionContext: ExecutionContext): Scribe = {

		Files.createDirectories(basedir)

		val neo = new NeoInstance(basedir.resolve("db2").toString)

		neo.tx { implicit ntx =>
			if (!neo.db.schema().getConstraints(label.Book).iterator().hasNext)
				neo.db.schema().constraintFor(label.Book).assertPropertyIsUnique("id").create()
		}

		val ioHttp: HttpExt = Http(system)
		val iopipe = (request: HttpRequest) => ioHttp.singleRequest(request)(materializer)



		new Scribe(
			basedir = basedir,
			neo = neo,
			sendReceive = iopipe,
			ec = executionContext
		)
	}

}

class Scribe(
	val basedir: Path,
	val neo: NeoInstance,
	val sendReceive: HttpRequest => Future[HttpResponse],
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
