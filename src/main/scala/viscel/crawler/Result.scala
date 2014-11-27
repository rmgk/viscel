package viscel.crawler

import org.scalactic.ErrorMessage
import spray.http.{HttpRequest, HttpResponse}
import viscel.crawler.Result.{Continue, DelayedRequest}
import viscel.database.Ntx

sealed trait Result {
	def map(f: Strategy => Strategy): Result = this match {
		case Continue(next) => Continue(f(next))
		case DelayedRequest(request, continue) => DelayedRequest(request, res => ntx => f(continue(res)(ntx)))
		case other => other
	}
}

object Result {
	final case class Done(message: String) extends Result
	final case class Continue(next: Strategy) extends Result
	final case class Failed(messages: List[ErrorMessage]) extends Result
	final case class DelayedRequest(request: HttpRequest, continue: HttpResponse => Ntx => Strategy) extends Result
}
