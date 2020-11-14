package io.hat.shootout.user.api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}

trait UserService extends Service {

  def login: ServiceCall[NotUsed, String]

  override final def descriptor: Descriptor = {
    import Service._
    named("user")
      .withCalls(
        pathCall("/login", login),
      )
      .withAutoAcl(true)
  }

}
