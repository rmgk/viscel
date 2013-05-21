package viscel.display

import spray.http.{HttpResponse, HttpEntity, MediaTypes}
import viscel.Element

trait ElementDisplay {
	this: Element =>

	def display = <img src={"/b/" + blob}/>
}
