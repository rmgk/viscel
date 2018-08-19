package viscel.crawl

import org.scalactic.Every
import viscel.scribe.Link
import viscel.selection.Report

case class RequestException(uri: String, status: String) extends Throwable
case class WrappingException(link: Link, reports: Every[Report]) extends Throwable
