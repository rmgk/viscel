package viscel

import java.nio.file.{Files, Path, Paths}

import akka.actor.ActorSystem
import akka.io.IO
import org.scalactic.TypeCheckedTripleEquals._
import spray.can.Http
import spray.client.pipelining
import spray.client.pipelining.SendReceive
import spray.http.HttpEncodings
import viscel.database.{NeoInstance, label}
import viscel.store.Config

import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success}

object Viscel {

	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		Console.println(s"$desc took ${ (System.nanoTime - start) / 1000000.0 } ms")
		res
	}

	var neo: NeoInstance = _
	var iopipe: SendReceive = _

	var basepath: Path = _

	def main(args: Array[String]): Unit = run(args(0))

	def run(basedir: String) = {

		basepath = Paths.get(basedir)
		Files.createDirectories(basepath)

		val configNode = neo.tx { implicit ntx =>
			val cfg = Config.get()(ntx)
			if (cfg.version != 2) throw new IllegalStateException(s"config version not supported: ${ cfg.version }")

			if (!neo.db.schema().getConstraints(label.Book).iterator().hasNext)
				neo.db.schema().constraintFor(label.Book).assertPropertyIsUnique("id").create()

			cfg
		}

		implicit val system = ActorSystem()

		val ioHttp = IO(Http)
		iopipe = pipelining.sendReceive(ioHttp)(system.dispatcher, 300.seconds)



		Deeds.responses = {
			case Success(res) => neo.tx { ntx =>
				configNode.download(
					size = res.entity.data.length,
					success = res.status.isSuccess,
					compressed = res.encoding === HttpEncodings.deflate || res.encoding === HttpEncodings.gzip)(ntx)
			}
			case Failure(_) => neo.tx { ntx => configNode.download(0, success = false)(ntx) }
		}

		(system, ioHttp, iopipe)
	}


}

