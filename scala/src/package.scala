import com.typesafe.scalalogging.slf4j.Logging

package object viscel extends Logging {
	def time[T](desc: String = "")(f: => T): T = {
		val start = System.currentTimeMillis()
		val res = f
		logger.info(s"$desc took ${System.currentTimeMillis - start}ms")
		res
	}
}
