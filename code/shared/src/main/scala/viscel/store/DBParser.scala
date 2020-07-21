package viscel.store

import java.io.ByteArrayInputStream

import com.github.plokhotnyuk.jsoniter_scala.core.{ReaderConfig, readFromArray, scanJsonValuesFromStream, writeToArray}
import viscel.shared.{DataRow, JsoniterCodecs}

import scala.collection.mutable.ListBuffer

object DBParser {

  private val noEndOfInputCheck = ReaderConfig.withCheckForEndOfInput(false)

  def parse(inputbytes: Array[Byte]): (String, List[DataRow]) = {
    val name        = readFromArray[String](inputbytes, noEndOfInputCheck)(JsoniterCodecs.StringRw)
    val namelength  = writeToArray(name)(JsoniterCodecs.StringRw).length
    val listBuilder = ListBuffer[DataRow]()
    if (namelength < inputbytes.length - 1) {
      val is = new ByteArrayInputStream(inputbytes, namelength, inputbytes.length - namelength)
      scanJsonValuesFromStream[DataRow](is, noEndOfInputCheck) { dr =>
        listBuilder.append(dr)
        true
      }(JsoniterCodecs.DataRowRw)
    }
    val dataRows = listBuilder.toList
    (name, dataRows)

  }
}
