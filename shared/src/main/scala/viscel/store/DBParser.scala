package viscel.store

import java.io.ByteArrayInputStream
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, ReaderConfig, readFromArray, readFromSubArray, scanJsonValuesFromStream, writeToArray}
import viscel.shared.{DataRow, JsoniterCodecs}

import java.nio.charset.StandardCharsets
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

object DBParser {

  def parse(inputbytes: Array[Byte]): (String, List[DataRow]) = {
    def readSep[A](start: Int, codec: JsonValueCodec[A]): (Int, A) =
      val end =
        val res = inputbytes.indexOf('\n', start + 1)
        if res < 0 then inputbytes.length else res
      (end, readFromSubArray(inputbytes, start, end)(using codec))

    val endbytes = inputbytes.lastIndexWhere(_ != '\n')

    @tailrec
    def rec(idx: Int, acc: List[DataRow]): List[DataRow] = {
      if idx >= endbytes then acc.reverse
      else
        val (nidx, dr) = readSep(idx, JsoniterCodecs.DataRowRw)
        rec(nidx, dr :: acc)
    }
    val (startidx, name) = readSep(0, JsoniterCodecs.StringRw)
    val dataRows         = rec(startidx, Nil)
    (name, dataRows)

  }
}
