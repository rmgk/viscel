package viscel.crawl

import org.scalactic.Every
import viscel.selection.Report
import viscel.store.v4.DataRow

case class RequestException(uri: String, status: String) extends Throwable
case class WrappingException(link: DataRow.Link, reports: Every[Report]) extends Throwable
