package viscel.crawl

import org.scalactic.Every
import viscel.netzi.{Report, VRequest}

case class RequestException(uri: String, status: String) extends Throwable
case class WrappingException(link: VRequest, reports: Every[Report]) extends Throwable
