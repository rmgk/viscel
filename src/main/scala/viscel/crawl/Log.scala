package viscel.crawl

object Log {
	def trace(m: String) = /*Console.println(m)*/ ()
	def debug(m: String) = /*Console.println(m)*/ ()
	def info(m: String) = Console.println(m)
	def warn(m: String) = Console.println(m)
	def error(m: String) = Console.println(m)
}
