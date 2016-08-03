package viscel.server

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{Bad, Good}
import viscel.shared.Log

//import spray.routing.authentication.{BasicAuth, BasicHttpAuthenticator, UserPass, UserPassAuthenticator}
import viscel.store.{User, Users}

import scala.Predef.ArrowAssoc
import scala.collection.immutable.Map

class UserStore() {
	var userCache = Map[String, User]()

	def userUpdate(user: User): User = {
		userCache += user.id -> user
		Users.store(user)
		user
	}

	def getUserNode(name: String, password: String): Option[User] =
		userCache.get(name).orElse(
			(Users.load(name) match {
				case Good(g) => Some(g)
				case Bad(e) =>
					Log.warn(s"could not open user $name: $e")
					val firstUser = Users.all().fold(_.isEmpty, _ => false)
					if (firstUser) Some(User(name, password, admin = firstUser, Map()))
					else None
			}).map(userUpdate))


	def authenticate(credentials: Option[BasicHttpCredentials]): Option[User] = credentials match {
		case Some(BasicHttpCredentials(user, password)) =>
			Log.trace(s"login: $user $password")
			// time("login") {
			if (user.matches("\\w+")) {
				getUserNode(user, password).filter(_.password === password)
			}
			else {None}
		// }
		case None => None
	}
}
