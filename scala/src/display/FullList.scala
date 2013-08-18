package viscel.display

import scalatags._
import spray.http.{HttpResponse, HttpEntity, MediaTypes}
import viscel.store.Collection

object FullList {

	def apply() = HttpResponse(
		entity = HttpEntity(MediaTypes.`text/html`,
			content().toXML.toString
		)
	)

	def content = html(
		head(),
		body(
			h1("Collections"),
			ul(
				Collection.list.map{id =>
					li(a.href(s"/legacy/$id")(id))
					})))

}
