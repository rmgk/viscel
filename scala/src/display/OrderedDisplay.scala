package viscel.display

import spray.http.{HttpResponse, HttpEntity, MediaTypes}
import spray.routing.{HttpService, RequestContext, Directives}
import viscel.collection.OrderedCollection

trait OrderedDisplay extends Directives {
	this: OrderedCollection =>

	def display = HttpResponse(
		entity = HttpEntity(MediaTypes.`text/html`,
			<html>
				<body>
					<h1>{name}</h1>
					<ul>
					{ (1 to last) map {pos =>
						<li>
						<a href={s"/legacy/$name/$pos"}>{pos.toString}</a>
						</li>
					}}
					</ul>
				</body>
			</html>.toString()
		)
	)

	def display(pos: Int) = {
		assume(pos > 0)
		HttpResponse(
			entity = HttpEntity(MediaTypes.`text/html`,
				<html>
					<body>
						{ get(pos).display}
						<br/>
						<a href={(pos-1).toString}>prev</a>
						<a href=".">up</a>
						<a href={(pos+1).toString}>next</a>
					</body>
				</html>.toString()
			)
		)
	}

	def route =
		path("")(complete(display)) ~
		path(IntNumber) {pos => complete(display(pos))}
}
