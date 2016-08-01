package viscel.db2to3.database

class Books(neo: Neo) {

	def all(): List[Book] = neo.tx { implicit ntx =>
		ntx.nodes(label.Book).map { n => Book.apply(n) }
	}

}
