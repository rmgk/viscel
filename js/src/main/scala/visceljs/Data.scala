package visceljs

import viscel.shared.Gallery
import viscel.shared.{Article, Chapter, Description, Content}

case class Data(description: Description, content: Content,  bookmark: Int) {
	def id: String = description.id
	def pos: Int = content.gallery.pos
	def gallery: Gallery[Article] = content.gallery
	def move(f: Gallery[Article] => Gallery[Article]) = copy(content = content.copy(gallery = f(gallery)))
	def next = move(_.next(1))
	def prev = move(_.prev(1))
}
