package io.hat.shootout.user.impl

import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.typesafe.config.ConfigFactory
import io.hat.shootout.user.api.{LoginRequest, LoginResponse, UserService}
import org.pac4j.lagom.jwt.JwtGeneratorHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class UserServiceImpl(clusterSharding: ClusterSharding, persistentEntityRegistry: PersistentEntityRegistry)(implicit ec: ExecutionContext) extends UserService {

  private val generator = JwtGeneratorHelper.parse(ConfigFactory.load().getConfig("pac4j.lagom.jwt.generator"))

  override def login: ServiceCall[LoginRequest, LoginResponse] = ServiceCall { loginRequest =>
    //Dummy impl
    val claims: java.util.Map[String, Object] = Map(
      "sub" -> loginRequest.email,
      "email" -> loginRequest.email
    ).asJava.asInstanceOf[java.util.Map[String, Object]]
    val jwtToken = generator.generate(claims)
    Future.successful(LoginResponse(jwtToken))
  }
}
