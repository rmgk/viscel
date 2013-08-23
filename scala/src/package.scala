import com.typesafe.scalalogging.slf4j.Logging
import java.security.MessageDigest

package object viscel extends Logging {
	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		logger.info(s"$desc took ${(System.nanoTime - start) / 1000000.0} ms")
		res
	}

	val digester = MessageDigest.getInstance("SHA1")
	def sha1hex(b: Array[Byte]) = digester.digest(b).map { "%02X" format _ }.mkString
}
