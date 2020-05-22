package viscel.narration

import viscel.narration.narrators._
import viscel.selektiv.Narration.WrapPart
import viscel.shared.Vid
import viscel.store.v4.DataRow

object Narrators {

  val metas: List[Metarrator[_]] = List(Mangadex, WebToons, Tapas)

}

case class NarratorADT(id: Vid, name: String, archive: List[DataRow.Content], wrap: WrapPart[List[DataRow.Content]])
  extends Narrator {
  override def wrapper: WrapPart[List[DataRow.Content]] = wrap
}
