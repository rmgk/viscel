package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import better.files.File
import viscel.scribe.ScribePicklers._
import viscel.shared.Log.{Scribe => Log}

import scala.collection.mutable


case class Book(id: String,
                name: String,
                pagetre: Map[Vurl, PageData],
                blobs: Map[Vurl, BlobData],
               ) {


  def add(entry: ScribeDataRow): Option[Int] = { ???
//    val index = entries.lastIndexWhere(entry.matchesRef)
//    if (index < 0 || entries(index).differentContent(entry)) {
//      val addCount = entry match {
//        case alp@PageData(il, _, _, _) =>
//          pageMap.put(il, alp)
//          val oldCount = if (index < 0) 0 else {
//            assert(entries(index).isInstanceOf[PageData], s"entries matching page data $alp matches ${entries(index)}")
//            entries(index).asInstanceOf[PageData].articleCount
//          }
//          Option(alp.articleCount - oldCount)
//        case alb@BlobData(il, _, _, _) =>
//          blobMap.put(il, alb)
//          Option(0)
//      }
//      if (index >= 0) entries.remove(index)
//      entries += entry
//      addCount
//    }
//    else None
  }

  def beginning: Option[PageData] = pagetre.get(Vurl.entrypoint)
  def hasPage(ref: Vurl): Boolean = pagetre.contains(ref)
  def hasBlob(ref: Vurl): Boolean = blobs.contains(ref)


  private def pageContents: Iterator[WebContent] = {
    pagetre.valuesIterator.flatMap(_.contents)
  }

  def emptyImageRefs(): List[ImageRef] = pageContents.collect {
    case art@ImageRef(ref, _, _) if !hasBlob(ref) => art
  }.toList


  def volatileAndEmptyLinks(): List[Link] = pageContents.collect {
    case link@Link(ref, Volatile, _) => link
    case link@Link(ref, _, _) if !hasPage(ref) => link
  }.toList

  def size(): Int = linearizedContents().count {
    case Article(_, _) => true
    case _ => false
  }

  def allBlobs(): Iterator[BlobData] = blobs.valuesIterator


  def linearizedContents(): List[ReadableContent] = {

    Log.info(s"pages for $id")

    val seen = mutable.HashSet[Vurl]()

    def unseen(contents: List[WebContent]): List[WebContent] = {
      contents.filter {
        case link@Link(loc, policy, data) => seen.add(loc)
        case _ => true
      }
    }

    @scala.annotation.tailrec
    def flatten(remaining: List[WebContent], acc: List[ReadableContent]): List[ReadableContent] = {
      remaining match {
        case Nil => acc
        case h :: t => h match {
          case Link(loc, policy, data) =>
            pagetre.get(loc) match {
              case None => flatten(t, acc)
              case Some(alp) => flatten(unseen(alp.contents) reverse_::: t, acc)
            }
          case art@ImageRef(ref, origin, data) =>
            val blob = blobs.get(ref).map(_.blob)
            flatten(t, Article(art, blob) :: acc)
          case chap@Chapter(_) => flatten(t, chap :: acc)
        }
      }
    }

    beginning match {
      case None =>
        Log.warn(s"Book $id was emtpy")
        Nil
      case Some(initialPage) =>
        flatten(unseen(initialPage.contents.reverse), Nil)
    }

  }

}

object Recheck {
  /** Starts from the entrypoint, traverses the last Link,
    * collect the path, returns the path from the rightmost child to the root. */
  def computeRightmostLinks(book: Book): List[Link] = {

    val seen = mutable.HashSet[Vurl]()

    @scala.annotation.tailrec
    def rightmost(current: PageData, acc: List[Link]): List[Link] = {
      /* Get the last Link for the current PageData  */
      val next = current.contents.reverseIterator.find {
        case Link(loc, _, _) if seen.add(loc) => true
        case _ => false
      } collect { // essentially a typecast …
        case l@Link(_, _, _) => l
      }
      next match {
        case None => acc
        case Some(link) =>
          book.pagetre.get(link.ref) match {
            case None => link :: acc
            case Some(scribePage) =>
              rightmost(scribePage, link :: acc)
          }
      }
    }

    book.beginning match {
      case None =>
        Log.warn(s"Book ${book.id} was emtpy")
        Nil
      case Some(initialPage) =>
        rightmost(initialPage, Nil)
    }

  }
}

object Book {
  def load(path: Path) = {

    Log.info(s"reading $path")

    val lines = File(path).lineIterator(StandardCharsets.UTF_8)
    val name = io.circe.parser.decode[String](lines.next()).toTry.get

    val entryList = lines.zipWithIndex.map { case (line, nr) =>
      io.circe.parser.decode[ScribeDataRow](line) match {
        case Right(s) => s
        case Left(t) =>
          Log.error(s"Failed to decode $path:${nr + 2}: $line")
          throw t
      }
    }.toList

    val pages = entryList.collect{ case pd@PageData(ref, _, date, contents) => (ref, pd) }.toMap
    val blobs = entryList.collect{ case bd@BlobData(ref, loc, date, blob) => (ref, bd) }.toMap


    val id = path.getFileName.toString
    new Book(id, name, pages, blobs)
  }
}
