package viscel.core

class CoreStatus(msg: String) extends Throwable(msg)
class NormalStatus(msg: String) extends CoreStatus(msg)
class FailedStatus(msg: String) extends CoreStatus(msg)

object EndRun {
	def apply(msg: String) = new NormalStatus(msg)
}

object FailRun {
	def apply(msg: String) = new FailedStatus(msg)
}
