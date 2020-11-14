package io.hat.shootout.user.api

import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json.{Format, Json}

trait UserService extends Service {

  def login: ServiceCall[LoginRequest, LoginResponse]

  override final def descriptor: Descriptor = {
    import Service._
    named("user")
      .withCalls(
        restCall(Method.POST, "/api/user/login", login),
      )
      .withAutoAcl(true)
  }

}

case class LoginRequest(email: String, password: String)
case object LoginRequest {
  implicit val format: Format[LoginRequest] = Json.format[LoginRequest]
}

case class LoginResponse(jwt: String)
case object LoginResponse {
  implicit val format: Format[LoginResponse] = Json.format[LoginResponse]
}
