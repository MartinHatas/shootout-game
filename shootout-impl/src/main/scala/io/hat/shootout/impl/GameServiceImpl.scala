package io.hat.shootout.impl

import java.util.UUID

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import io.hat.shootout.api._
import org.pac4j.core.authorization.authorizer.IsAuthenticatedAuthorizer.isAuthenticated
import org.pac4j.core.config.Config
import org.pac4j.core.profile.CommonProfile
import org.pac4j.lagom.scaladsl.SecuredService
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class GameServiceImpl(clusterSharding: ClusterSharding, persistentEntityRegistry: PersistentEntityRegistry, override val securityConfig: Config)(implicit ec: ExecutionContext) extends GameService with SecuredService {

  private final val log: Logger = LoggerFactory.getLogger(classOf[GameServiceImpl])

  private def entityRef(id: String): EntityRef[GameCommand] =
    clusterSharding.entityRefFor(GameState.typeKey, id)

  implicit val timeout = Timeout(5.seconds)

  override def getGame(gameId: String): ServiceCall[NotUsed, GameMessage] = ServiceCall { _ =>

    val game = entityRef(gameId)

      game
        .ask[Game](replyTo => GetGame(gameId, replyTo))
        .map(game => GameMessage(game.id, game.name, game.owner, game.status, game.players))
  }

  /**
   * curl -X POST -s 'http://localhost:9000/api/game?name=wild-west' -H "Authorization: Bearer $SHOOTOUT_JWT" | jq .
   */
  override def createGame(name: String): ServiceCall[NotUsed, GameMessage] = {
    authorize(isAuthenticated[CommonProfile](), (profile: CommonProfile) =>
      ServerServiceCall { _: NotUsed =>
        val ownerId = profile.getId
        val gameId = UUID.randomUUID().toString

        val game = entityRef(gameId)

        log.info("[{}] Creating new game [{}]", gameId, profile.toString)

        (game ? (self => CreateGame(gameId, name, ownerId, self)))
          .map(response => GameMessage(response.id, response.name, response.owner, response.status, response.players))
      })
  }

  /**
   * curl 'http://localhost:9000/api/game/19f0c829-17ff-401d-9c5f-ffc661302dfa/stream'
   */
  override def gameStream(id: String): ServiceCall[NotUsed, Source[String, NotUsed]] = ServiceCall { _ =>
    Future.successful(persistentEntityRegistry
      .eventStream(GameEvent.Tag, Offset.noOffset)
      .filter(event => event.entityId == id)
      .map(event => convertEvent(event))
    )
  }

  /**
   * curl -X PATCH 'http://localhost:9000/api/game/19f0c829-17ff-401d-9c5f-ffc661302dfa/status?value=active'
   */
  override def updateGame(gameId: String, attribute: String, value: String): ServiceCall[NotUsed, ConfirmationMessage] = ServiceCall { _ =>

    val game = entityRef(gameId)

    val gameResponse = attribute match {
      case "status" => game ? (self => ChangeStatus(value, self))
      case _ => Future.successful(Rejected(s"[$gameId] Unknown game attribute [$attribute]"))
    }

    gameResponse map {
      case Accepted         => AcceptedMessage
      case Rejected(reason) => RejectedMessage(reason)
    }


  }


//  override def useGreeting(id: String) = ServiceCall { request =>
//    // Look up the sharded entity (aka the aggregate instance) for the given ID.
//    val ref = entityRef(id)
//
//    // Tell the aggregate to use the greeting message specified.
//    ref
//      .ask[Confirmation](
//        replyTo => UseGreetingMessage(request.message, replyTo)
//      )
//      .map {
//        case Accepted => Done
//        case _        => throw BadRequest("Can't upgrade the greeting message.")
//      }
//  }
//
//  override def greetingsTopic(): Topic[String] =
//    TopicProducer.singleStreamWithOffset { fromOffset =>
//      persistentEntityRegistry
//        .eventStream(GameEvent.Tag, fromOffset)
//        .map(ev => (convertEvent(ev), ev.offset))
//    }

  private def convertEvent(gameEvent: EventStreamElement[GameEvent]): String = {
    gameEvent.event match {
//      case event => Json.toJson(Map("action" -> gameEvent.event.getClass.getSimpleName, "data" -> event)).asText()
      case event => event.toString
    }
  }

}
