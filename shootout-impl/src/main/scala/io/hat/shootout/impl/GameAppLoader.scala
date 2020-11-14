package io.hat.shootout.impl

import java.util

import akka.cluster.sharding.typed.scaladsl.Entity
import com.lightbend.lagom.scaladsl.api.{Descriptor, ServiceLocator}
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.softwaremill.macwire._
import org.pac4j.core.config.Config
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.HttpConstants.{AUTHORIZATION_HEADER, BEARER_HEADER_PREFIX}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.http.client.direct.HeaderClient
import org.pac4j.lagom.jwt.JwtAuthenticatorHelper
import io.hat.shootout.api.GameService

class GameAppLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new GameApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new GameApplication(context) with LagomDevModeComponents

  override def describeService: Some[Descriptor] = Some(readDescriptor[GameService])
}

abstract class GameApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with CassandraPersistenceComponents
    with LagomKafkaComponents
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[GameService](wire[GameServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = GameSerializerRegistry

  // Initialize the sharding of the Aggregate. The following starts the aggregate Behavior under
  // a given sharding entity typeKey.
  clusterSharding.init(
    Entity(GameState.typeKey)(
      entityContext => GameBehavior.create(entityContext)
    )
  )

  lazy val jwtClient: HeaderClient = {
    val headerClient = new HeaderClient
    headerClient.setHeaderName(AUTHORIZATION_HEADER)
    headerClient.setPrefixHeader(BEARER_HEADER_PREFIX)
    headerClient.setAuthenticator(JwtAuthenticatorHelper.parse(config.getConfig("pac4j.lagom.jwt.authenticator")))
    headerClient.setAuthorizationGenerator((_: WebContext, profile: CommonProfile) => {
      if (profile.containsAttribute("roles")) {
        profile.addRoles(profile.getAttribute("roles", classOf[util.Collection[String]]))
      }
      profile
    })
    headerClient.setName("JWT")
    headerClient
  }

  lazy val securityConfig: Config = {
    val config = new Config(jwtClient)
    config.getClients.setDefaultSecurityClients(jwtClient.getName)
    config
  }

}
