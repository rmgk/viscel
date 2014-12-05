package viscel.crawler

import spray.http.{HttpRequest, HttpResponse}
import viscel.database.Ntx

import scala.concurrent.Future


sealed trait Request[R]
final case class Req[R](request: HttpRequest, handler:  HttpResponse => Ntx => Future[R]) extends Request[R]
final case class RequestDone[R](result: R) extends Request[R]