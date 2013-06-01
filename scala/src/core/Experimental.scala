package viscel.core

import spray.client.pipelining._
import org.htmlcleaner._
import scala.concurrent._
import ExecutionContext.Implicits.global

class Experimental(val pipe: spray.client.SendReceive ) {

	val start = "http://carciphona.com/view.php?page=cover&chapter=1"
	lazy val cleaner = new HtmlCleaner();

	def doSomething = {
		for {
			res <- pipe(Get(start))
			node = cleaner.clean(new java.io.ByteArrayInputStream(res.entity.buffer))
			bgimg = node.evaluateXPath("//*[@class='page']@style")(0).toString
		} yield node
	}

}
