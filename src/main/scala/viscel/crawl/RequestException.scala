package viscel.crawl

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.scalactic.Every
import viscel.scribe.Link
import viscel.selection.Report

case class RequestException(request: HttpRequest, response: HttpResponse) extends Throwable
case class WrappingException(link: Link, reports: Every[Report]) extends Throwable
