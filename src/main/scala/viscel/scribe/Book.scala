package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.stream.Collectors

import viscel.scribe.ScribePicklers._
import viscel.shared.Log.{Scribe => Log}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


class Book private(val id: String,
                   val name: String,
                   pageMap: mutable.Map[Vurl, PageData],
                   blobMap: mutable.Map[Vurl, BlobData],
                   entries: ArrayBuffer[ScribeDataRow],
                  ) {


  def add(entry: ScribeDataRow): Option[Int] = {
    val index = entries.lastIndexWhere(entry.matchesRef)
    if (index < 0 || entries(index).differentContent(entry)) {
      val addCount = entry match {
        case alp@PageData(il, _, _, _) =>
          pageMap.put(il, alp)
          val oldCount = if (index < 0) 0 else {
            assert(entries(index).isInstanceOf[PageData], s"entries matching page data $alp matches ${entries(index)}")
            entries(index).asInstanceOf[PageData].articleCount
          }
          Option(alp.articleCount - oldCount)
        case alb@BlobData(il, _, _, _) =>
          blobMap.put(il, alb)
          Option(0)
      }
      if (index >= 0) entries.remove(index)
      entries += entry
      addCount
    }
    else None
  }

  def beginning: Option[PageData] = pageMap.get(Vurl.entrypoint)
  def hasPage(ref: Vurl): Boolean = pageMap.contains(ref)
  def hasBlob(ref: Vurl): Boolean = blobMap.contains(ref)

  def emptyImageRefs(): List[ImageRef] = entries.collect {
    case PageData(ref, _, _, contents) => contents
  }.flatten.collect {
    case art@ImageRef(ref, _, _) if !blobMap.contains(ref) => art
  }.toList

  def volatileAndEmptyLinks(): List[Link] = entries.collect {
    case PageData(ref, _, _, contents) => contents
  }.flatten.collect {
    case link@Link(ref, Volatile, _) => link
    case link@Link(ref, _, _) if !pageMap.contains(ref) => link
  }.toList

  def size(): Int = pages().count {
    case Article(_, _) => true
    case _ => false
  }

  def allBlobs(): Iterator[BlobData] = entries.iterator.collect { case sb@BlobData(_, _, _, _) => sb }


  /** Starts from the entrypoint, traverses the last Link,
    * collect the path, returns the path from the rightmost child to the root. */
  def computeRightmostLinks(): List[Link] = {

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
          pageMap.get(link.ref) match {
            case None => link :: acc
            case Some(scribePage) =>
              rightmost(scribePage, link :: acc)
          }
      }
    }

    pageMap.get(Vurl.entrypoint) match {
      case None =>
        Log.warn(s"Book $id was emtpy")
        Nil
      case Some(initialPage) =>
        rightmost(initialPage, Nil)
    }

  }


  def pages(): List[ReadableContent] = {

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
            pageMap.get(loc) match {
              case None => flatten(t, acc)
              case Some(alp) => flatten(unseen(alp.contents) reverse_::: t, acc)
            }
          case art@ImageRef(ref, origin, data) =>
            val blob = blobMap.get(ref).map(_.blob)
            flatten(t, Article(art, blob) :: acc)
          case chap@Chapter(_) => flatten(t, chap :: acc)
        }
      }
    }

    pageMap.get(Vurl.entrypoint) match {
      case None =>
        Log.warn(s"Book $id was emtpy")
        Nil
      case Some(initialPage) =>
        flatten(unseen(initialPage.contents.reverse), Nil)
    }

  }

}

object Book {
  def load(path: Path) = {
    val pageMap: mutable.HashMap[Vurl, PageData] = mutable.HashMap[Vurl, PageData]()
    val blobMap: mutable.HashMap[Vurl, BlobData] = mutable.HashMap[Vurl, BlobData]()

    def putIfAbsent[A, B](hashMap: mutable.HashMap[A, B], k: A, v: B): Boolean = {
      var res = false
      hashMap.getOrElseUpdate(k, {res = true; v})
      res
    }

    Log.info(s"reading $path")

    val fileStream = Files.lines(path, StandardCharsets.UTF_8)
    try {
      val lines = fileStream.collect(Collectors.toList()).asScala
      val name = io.circe.parser.decode[String](lines.head).toTry.get

      val entries = lines.view.drop(1).zipWithIndex.reverseIterator.map { case (line, nr) =>
        io.circe.parser.decode[ScribeDataRow](line) match {
          case Right(s) => s
          case Left(t) =>
            Log.error(s"Failed to decode $path:${nr + 2}: $line")
            throw t
        }
      }.filter {
        case spage@PageData(il, _, _, _) => putIfAbsent(pageMap, il, spage)
        case sblob@BlobData(il, _, _, _) => putIfAbsent(blobMap, il, sblob)
      }.to[ArrayBuffer].reverse

      val id = path.getFileName.toString
      new Book(id, name, pageMap, blobMap, entries)
    }
    finally {
      fileStream.close()
    }


  }
}
