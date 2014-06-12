package viscel.core

sealed class CoreStatus(msg: String) extends Throwable(msg)
case class NormalStatus(msg: String) extends CoreStatus(msg)
case class FailedStatus(msg: String) extends CoreStatus(msg)

object EndRun {
	def apply(msg: String) = new NormalStatus(msg)
}

object FailRun {
	def apply(msg: String) = new FailedStatus(msg)
}
