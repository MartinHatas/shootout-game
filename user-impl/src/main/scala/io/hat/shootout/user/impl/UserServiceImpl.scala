package io.hat.shootout.user.impl

import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.typesafe.config.ConfigFactory
import io.hat.shootout.user.api.{LoginRequest, LoginResponse, UserService}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.profile.definition.CommonProfileDefinition
import org.pac4j.jwt.profile.JwtProfile
import org.pac4j.lagom.jwt.JwtGeneratorHelper

import scala.concurrent.{ExecutionContext, Future}

class UserServiceImpl(clusterSharding: ClusterSharding, persistentEntityRegistry: PersistentEntityRegistry)(implicit ec: ExecutionContext) extends UserService {

  private val generator = JwtGeneratorHelper.parse[CommonProfile](ConfigFactory.load().getConfig("pac4j.lagom.jwt.generator"))

  override def login: ServiceCall[LoginRequest, LoginResponse] = ServiceCall { loginRequest =>
    //Dummy impl
    val profile = new JwtProfile()
    profile.setId(loginRequest.email)
    profile.addAttribute(CommonProfileDefinition.EMAIL, loginRequest.email)
    val jwtToken = generator.generate(profile)
    Future.successful(LoginResponse(jwtToken))
  }
}
