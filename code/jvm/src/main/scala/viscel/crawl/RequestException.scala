package viscel.crawl

import viscel.netzi.{VRequest, VResponse}
import viscel.selektiv.Report

case class RequestException(uri: String, status: String)                                 extends Throwable
case class WrappingException(request: VRequest, response: VResponse[_], reports: Report) extends Throwable
