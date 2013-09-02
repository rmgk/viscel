import com.typesafe.scalalogging.slf4j.Logging
import java.security.MessageDigest
import scala.util._
import scala.concurrent.Future

package object viscel extends Logging {
	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		logger.info(s"$desc took ${(System.nanoTime - start) / 1000000.0} ms")
		res
	}

	val digester = MessageDigest.getInstance("SHA1")
	def sha1hex(b: Array[Byte]) = digester.digest(b).map { "%02x" format _ }.mkString

	def hashToFilename(h: String): String = (new StringBuilder(h)).insert(2, '/').insert(0, "../cache/").toString

	def failure(x: String) = Failure(new Throwable(x))

	implicit class Identity[T](x: T) {
		def pipe[R](f: T => R) = f(x)
		def tap[R](f: T => R) = { f(x); x }
		def validate(p: T => Boolean, msg: Throwable) = Try { if (p(x)) x else throw msg }
		def validate(p: T => Boolean) = Try { if (p(x)) x else throw new Throwable(s"could not validate property of $x") }
	}

	implicit class TryPimps[T](x: Try[T]) {
		def toFuture = x match {
			case Success(v) => Future.successful(v)
			case Failure(e) => Future.failed(e)
		}
	}

}
