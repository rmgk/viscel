package viscel.tests

import java.time.Instant

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import viscel.netzi.VRequest
import viscel.shared.Blob
import viscel.store.v3.{BlobData, ImageRef, Link, Normal, PageData, ScribeDataRow, Volatile}
import viscel.store.v4.{DataRow, Vurl}

object DataGenerators {
  implicit val genBlob: Arbitrary[Blob] = Arbitrary(for {sha <- Gen.alphaNumStr
                                                         mime <- Gen.alphaNumStr}
                                                      yield Blob(sha, mime))
  implicit val genVurl: Arbitrary[Vurl] = Arbitrary(for {str <- Gen.alphaNumStr} yield Vurl.fromString(s"viscel://$str"))
  implicit val genLink: Arbitrary[Link] = Arbitrary(for {
    policy <- Gen.oneOf(Normal, Volatile)
    url <- genVurl.arbitrary
    data <- Gen.listOf(arbitrary[String])
  } yield Link(url, policy, data))
  implicit val genInstant: Arbitrary[Instant] = Arbitrary(arbitrary[Long].map((i: Long) => Instant.ofEpochMilli(i)))
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
  } yield VRequest(href, Nil, origin))

  implicit val genDataRow: Arbitrary[DataRow] = Arbitrary(for {
    vurl <- arbitrary[Vurl]
  } yield DataRow(vurl, contents = Nil))
}
