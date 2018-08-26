package viscel.tests

import java.time.Instant

import io.circe.syntax._
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.FreeSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import viscel.scribe.{BlobData, ImageRef, Link, Normal, PageData, ScribeDataRow, Volatile, Vurl}
import viscel.shared.Blob
import viscel.scribe.ScribePicklers._


class DBSerialization extends FreeSpec with GeneratorDrivenPropertyChecks {

  implicit val genBlob: Arbitrary[Blob] = Arbitrary(for {sha <- Gen.alphaNumStr
                                                         mime <- Gen.alphaNumStr}
                                                      yield Blob(sha, mime))
  implicit val genVurl: Arbitrary[Vurl] = Arbitrary(for {blob <- genBlob.arbitrary} yield Vurl.blobPlaceholder(blob))
  implicit val genLink: Arbitrary[Link] = Arbitrary(for {
    policy <- Gen.oneOf(Normal, Volatile)
    url <- genVurl.arbitrary
    data <- Gen.listOf(arbitrary[String])
  } yield Link(url, policy, data))
  implicit val genInstant: Arbitrary[Instant] = Arbitrary(arbitrary[Int].map((i: Int) => Instant.ofEpochSecond(i)))
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
  implicit val scribeDataRow: Arbitrary[ScribeDataRow] = Arbitrary(Gen.oneOf(arbitrary[BlobData], arbitrary[PageData]))


  val pageJson = """{"$type":"Page","ref":"http://xkcd.com/1/","loc":"http://xkcd.com/1/","date":"2016-08-03T20:04:33.010Z","contents":[{"$type":"Article","ref":"http://imgs.xkcd.com/comics/barrel_cropped_(1).jpg","origin":"http://xkcd.com/1/","data":{"alt":"Barrel - Part 1","title":"Don't we all.","longcomment":"Don't we all."}},{"$type":"Link","ref":"http://xkcd.com/2/"}]}"""
  val pageData = PageData(
    Vurl("http://xkcd.com/1/"), Vurl("http://xkcd.com/1/"),
    Instant.parse("2016-08-03T20:04:33.010Z"),
    List(ImageRef(
      Vurl("http://imgs.xkcd.com/comics/barrel_cropped_(1).jpg"),
      Vurl("http://xkcd.com/1/"), Map("alt" -> "Barrel - Part 1", "title" -> "Don't we all.", "longcomment" -> "Don't we all.")),
      Link(Vurl("http://xkcd.com/2/"), Normal, List())))
  def pageDataRow: ScribeDataRow = pageData


  val blobJson = """{"$type":"Blob","ref":"http://imgs.xkcd.com/comics/barrel_cropped_(1).jpg","loc":"http://imgs.xkcd.com/comics/barrel_cropped_(1).jpg","date":"2016-08-03T20:04:33.010Z","blob":{"sha1":"9bf1c63b1a9250baa83effbf4d3826dfe6796e08","mime":"image/jpeg"}}"""
  val blobData: ScribeDataRow = BlobData(
    Vurl("http://imgs.xkcd.com/comics/barrel_cropped_(1).jpg"),
    Vurl("http://imgs.xkcd.com/comics/barrel_cropped_(1).jpg"),
    Instant.parse("2016-08-03T20:04:33.010Z"),
    Blob("9bf1c63b1a9250baa83effbf4d3826dfe6796e08", "image/jpeg"))


  "Serialization" - {
    "Vurls" - {
      "entrypoint json" in assert(Vurl.entrypoint.asJson === "viscel:///initial".asJson)
      "entrypoint from string" in assert(Vurl.entrypoint === Vurl.fromString("viscel:///initial"))
      "blob json" in forAll { blob: Blob =>
        assert(Vurl.blobPlaceholder(blob).asJson === s"viscel:///sha1/${blob.sha1}".asJson)
      }
      "blob from string" in forAll(Gen.alphaNumStr) { sha =>
        assert(Vurl.fromString(s"viscel:///sha1/$sha") === Vurl.blobPlaceholder(Blob(sha, "")))
      }
    }
    "DataRows" - {
      "read page" in assert(io.circe.parser.decode[ScribeDataRow](pageJson) === Right(pageDataRow))
      "round trip page" in assert(io.circe.parser.decode[ScribeDataRow](pageDataRow.asJson.noSpaces) === Right(pageDataRow))
      "read blob" in assert(io.circe.parser.decode[ScribeDataRow](blobJson) === Right(blobData))
      "round trip blob" in assert(io.circe.parser.decode[ScribeDataRow](blobData.asJson.noSpaces) === Right(blobData))
      "arbitrary round trip" in forAll { pd: ScribeDataRow =>
        assert(io.circe.parser.decode[ScribeDataRow](pd.asJson.noSpaces) === Right(pd))
      }
    }
  }

  "Random" - {
    "page data count" in assert(pageData.articleCount === 1)
  }

}
