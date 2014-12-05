package viscel.crawler

import spray.http.{HttpRequest, HttpResponse}
import viscel.database.Ntx

import scala.concurrent.Future


sealed trait Request[R]
object Request {
	final case class Delayed[R](request: HttpRequest, handler: HttpResponse => Ntx => Future[R]) extends Request[R]
	final case class Done[R](result: R) extends Request[R]
}
