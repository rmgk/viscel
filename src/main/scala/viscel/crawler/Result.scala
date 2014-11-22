package viscel.crawler

import spray.http.{HttpRequest, HttpResponse}

sealed trait Result[+R] {
	def map[S](f: R => S): Result[S]
}

object Result {
	sealed trait ResultNothing extends Result[Nothing] {
		override def map[S](f: (Nothing) => S): Result[S] = this
	}
	case object Done extends ResultNothing
	case object Continue extends ResultNothing
	final case class Failed(message: String) extends ResultNothing
	final case class DelayedRequest[+R](request: HttpRequest, continue: HttpResponse => R) extends Result[R]{
		def map[S](f: R => S): DelayedRequest[S] = copy(continue = continue.andThen(f))
	}
}
