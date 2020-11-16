package io.hat.shootout.impl.auth

import java.util

import org.pac4j.core.authorization.authorizer.Authorizer
import org.pac4j.core.context.WebContext
import org.pac4j.core.profile.CommonProfile

class GameOwnerAuthorizer[T <: CommonProfile] extends Authorizer[T]{

  override def isAuthorized(context: WebContext, profiles: util.List[T]): Boolean = ???
}

object GameOwnerAuthorizer {

}
