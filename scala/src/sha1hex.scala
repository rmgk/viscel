package viscel

import java.security.MessageDigest

object sha1hex {
	val digester = MessageDigest.getInstance("SHA1")
	def apply(b: Array[Byte]) = digester.digest(b).map { "%02X" format _ }.mkString
}

