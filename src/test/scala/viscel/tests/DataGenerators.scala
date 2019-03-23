package viscel.tests

import java.time.Instant

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import viscel.crawl.{CrawlTask, VRequest}
import viscel.shared.Blob
import viscel.store.v4.DataRow
import viscel.store.{BlobData, ImageRef, Link, Normal, PageData, ScribeDataRow, Volatile, Vurl}

object DataGenerators {
  implicit val genBlob: Arbitrary[Blob] = Arbitrary(for {sha <- Gen.alphaNumStr
                                                         mime <- Gen.alphaNumStr}
                                                      yield Blob(sha, mime))
  implicit val genVurl: Arbitrary[Vurl] = Arbitrary(for {blob <- genBlob.arbitrary} yield Vurl.blobPlaceholder(blob))
  implicit val genLink: Arbitrary[Link] = Arbitrary(for {
    policy <- Gen.oneOf(Normal, Volatile)
    url <- genVurl.arbitrary
    data <- Gen.listOf(arbitrary[String])
  } yield Link(url, policy, data))
  implicit val genInstant: Arbitrary[Instant] = Arbitrary(arbitrary[Long].map((i: Long) => Instant.ofEpochSecond(i)))
  implicit val genImageRef: Arbitrary[ImageRef] = Arbitrary(for {
    ref <- genVurl.arbitrary
    origin <- genVurl.arbitrary
    data <- arbitrary[Map[String, String]]
  } yield ImageRef(ref, origin, data))
  implicit val genPageData: Arbitrary[PageData] = Arbitrary(for {
    ref <- arbitrary[Vurl]
    loc <- arbitrary[Vurl]
    date <- arbitrary[Instant]
    contents <- Gen.listOf(Gen.oneOf(arbitrary[ImageRef], arbitrary[Link]))
  } yield PageData(ref, loc, date, contents))
  implicit val genBlobData: Arbitrary[BlobData] = Arbitrary(for {
    ref <- arbitrary[Vurl]
    loc <- arbitrary[Vurl]
    date <- arbitrary[Instant]
    blob <- arbitrary[Blob]
  } yield BlobData(ref, loc, date, blob))
  implicit val genScribeDataRow: Arbitrary[ScribeDataRow] = Arbitrary(Gen.oneOf(arbitrary[BlobData], arbitrary[PageData]))
  implicit val genVRequest: Arbitrary[VRequest] = Arbitrary(for {
    href <- arbitrary[Vurl]
    origin <- arbitrary[Option[Vurl]]
  } yield VRequest(href, origin))
  implicit val genCrawlTaskPage: Arbitrary[CrawlTask.Page] = Arbitrary(for {
    req <- arbitrary[VRequest]
    from <- arbitrary[Link]
  } yield CrawlTask.Page(req, from))
  implicit val genCrawlTask: Arbitrary[CrawlTask] = Arbitrary(arbitrary[CrawlTask.Page])

  implicit val genDataRow: Arbitrary[DataRow] = Arbitrary(for {
    vurl <- arbitrary[Vurl]
  } yield DataRow(vurl, contents = Nil))
}
