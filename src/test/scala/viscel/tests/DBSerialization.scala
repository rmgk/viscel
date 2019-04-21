package viscel.tests

import java.time.Instant

import io.circe.syntax._
import org.scalatest.FreeSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import viscel.netzi.Vurl
import viscel.shared.Blob
import viscel.store.v3.CustomPicklers._
import viscel.store.v3.{BlobData, ImageRef, Link, Normal, PageData, ScribeDataRow}
import viscel.tests.DataGenerators._

class DBSerialization extends FreeSpec with ScalaCheckDrivenPropertyChecks {

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

  import viscel.store.v4.V4Codecs.uriWriter

  "Serialization" - {
    "Vurls" - {
      "entrypoint json" in assert(Vurl.entrypoint.asJson === "viscel:///initial".asJson)
      "entrypoint from string" in assert(Vurl.entrypoint === Vurl.fromString("viscel:///initial"))
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
