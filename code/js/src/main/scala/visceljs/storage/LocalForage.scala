package visceljs.storage

import scala.annotation.nowarn
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

//@JSImport("localforage", JSImport.Namespace)
@JSGlobal
@js.native
object localforage extends js.Object with LocalForageInstance {
  def createInstance(@nowarn config: js.Any): LocalForageInstance = js.native
}

@js.native
trait LocalForageInstance extends js.Object {
  def setItem(@nowarn key: String, @nowarn value: js.Any): js.Promise[Unit] = js.native
  def getItem[T](@nowarn key: String): js.Promise[T]                = js.native
}
