package viscel.crawl

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

case class RequestException(request: HttpRequest, response: HttpResponse) extends Throwable
