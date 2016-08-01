package viscel.shared

object Log {
	def trace(m: String) = /*Console.println(m)*/ ()
	def debug(m: String) = /*Console.println(m)*/ ()
	def info(m: String) = Console.println(m)
	def warn(m: String) = Console.println(m)
	def error(m: String) = Console.println(m)

	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		Console.println(s"$desc took ${(System.nanoTime - start) / 1000000.0} ms")
		res
	}
}
