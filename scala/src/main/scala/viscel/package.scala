import java.security.MessageDigest

import com.typesafe.scalalogging.slf4j.StrictLogging

import scala.Predef.wrapString

package object viscel extends StrictLogging {
	val digester = MessageDigest.getInstance("SHA1")

	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		logger.info(s"$desc took ${ (System.nanoTime - start) / 1000000.0 } ms")
		res
	}

	def sha1hex(b: Array[Byte]) = Predef.wrapByteArray(digester.digest(b)).map { h => f"$h%02x" }.mkString

	def hashToFilename(h: String): String = new StringBuilder(h).insert(2, '/').insert(0, "./cache/").toString()

}

