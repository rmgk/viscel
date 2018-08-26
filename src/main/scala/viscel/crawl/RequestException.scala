package viscel.crawl

import org.scalactic.Every
import viscel.selection.Report
import viscel.store.Link

case class RequestException(uri: String, status: String) extends Throwable
case class WrappingException(link: Link, reports: Every[Report]) extends Throwable
