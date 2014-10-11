package viscel.crawler

import spray.http.{HttpResponse, HttpRequest}

sealed trait Result[+R] {
	def map[S](f: R => S): Result[S]
}

object Result {
	case object Done extends Result[Nothing] {
		override def map[S](f: (Nothing) => S): Result[S] = this
	}
	final case class Failed(message: String) extends Result[Nothing] {
		override def map[S](f: (Nothing) => S): Result[S] = this
	}
	final case class DelayedRequest[+R](request: HttpRequest, continue: HttpResponse => R) extends Result[R]{
		def map[S](f: R => S): DelayedRequest[S] = copy(continue = continue.andThen(f))
	}
}
