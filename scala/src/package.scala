import com.typesafe.scalalogging.slf4j.Logging

package object viscel extends Logging {
	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		logger.info(s"$desc took ${(System.nanoTime - start) / 1000000.0}ms")
		res
	}
}
