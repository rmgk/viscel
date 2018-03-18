package visceljs

import viscel.shared.{SharedImage, Contents, Description, Gallery}

case class Data(description: Description, content: Contents, bookmark: Int) {
  def id: String = description.id
  def pos: Int = content.gallery.pos
  def gallery: Gallery[SharedImage] = content.gallery
  def move(f: Gallery[SharedImage] => Gallery[SharedImage]): Data = copy(content = content.copy(gallery = f(gallery)))
  def atPos(p: Int): Data = copy(content = content.copy(gallery = gallery.atPos(p)))
  def next: Data = move(_.next(1))
  def prev: Data = move(_.prev(1))
}
