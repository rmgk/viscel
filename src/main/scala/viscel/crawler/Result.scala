package viscel.crawler

import org.scalactic.ErrorMessage
import spray.http.{HttpRequest, HttpResponse}
import viscel.database.Ntx

import scala.concurrent.Future


final case class Request[R](request: HttpRequest, handler:  HttpResponse => Ntx => Future[R])
