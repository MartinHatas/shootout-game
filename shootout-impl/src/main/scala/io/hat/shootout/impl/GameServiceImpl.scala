package io.hat.shootout.impl

import java.util.UUID

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.pattern.StatusReply.ErrorMessage
import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import io.hat.shootout.api._
import io.hat.shootout.impl.auth.GameOwnerAuthorizer.isGameOwner
import org.pac4j.core.authorization.authorizer.AndAuthorizer.and
import org.pac4j.core.authorization.authorizer.IsAuthenticatedAuthorizer.isAuthenticated
import org.pac4j.core.config.Config
import org.pac4j.core.profile.CommonProfile
import org.pac4j.lagom.scaladsl.SecuredService
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class GameServiceImpl(clusterSharding: ClusterSharding, persistentEntityRegistry: PersistentEntityRegistry, override val securityConfig: Config)(implicit ec: ExecutionContext) extends GameService with SecuredService {

  private final val log: Logger = LoggerFactory.getLogger(classOf[GameServiceImpl])

  private def entityRef(id: String): EntityRef[GameCommand] =
    clusterSharding.entityRefFor(GameState.typeKey, id)

  implicit val timeout = Timeout(2.seconds)

  override def getGame(gameId: String): ServiceCall[NotUsed, GameMessage] = ServiceCall { _ =>

    val game = entityRef(gameId)

    game
      .askWithStatus[GameReply](replyTo => GetGame(gameId, replyTo))
      .map(game => GameMessage(game.id, game.name, game.owner, game.status, game.players))
  }

  /**
   * curl -X POST -s 'http://localhost:9000/api/game?name=wild-west' -H "Authorization: Bearer $JWT" | jq .
   */
  override def createGame(name: String): ServiceCall[NotUsed, ConfirmationMessage] = {
    authorize(isAuthenticated[CommonProfile](), (profile: CommonProfile) =>
      ServerServiceCall { _: NotUsed =>
        val ownerId = profile.getId
        val id = UUID.randomUUID().toString

        val game = entityRef(id)

        game.askWithStatus(replyTo => CreateGame(id, name, ownerId, replyTo))
          .map( _ => AcceptedMessage(id) ).mapTo[ConfirmationMessage]
          .recover {
            case ErrorMessage(reason) => RejectedMessage(reason)
            case _ => RejectedMessage(s"[$id] Failed to create the game.")
          }
      })
  }

  /**
   * curl -X PATCH 'http://localhost:9000/api/game/${GAME_ID}/start' -H "Authorization: Bearer $JWT"  | jq .
   */
  override def startGame(id: String): ServiceCall[NotUsed, ConfirmationMessage] = {
    authorize(and(isAuthenticated[CommonProfile](), isGameOwner(id)(clusterSharding, ec)), (profile: CommonProfile) =>
      ServerServiceCall { _: NotUsed =>

        val game = entityRef(id)

        game.askWithStatus(replyTo => StartGame(id, replyTo))
          .map( _ => AcceptedMessage(id) ).mapTo[ConfirmationMessage]
          .recover {
            case ErrorMessage(reason) => RejectedMessage(reason)
            case _ => RejectedMessage(s"[$id] Failed to start the game.")
          }
      })
  }

  /**
   * curl 'http://localhost:9000/api/game/${GAME_ID}/stream'
   */
  override def gameStream(id: String): ServiceCall[NotUsed, Source[StreamMessage, NotUsed]] = ServiceCall { _ =>
    Future.successful(
      persistentEntityRegistry
      .eventStream(GameEvent.Tag.forEntityId(id), Offset.noOffset)
      .map(streamElement => streamElement.event)
      .collect {
        case e: GameCreated       => StreamMessage(e.getClass.getSimpleName, data = Json.toJson(e))
        case e: PlayerJoined      => StreamMessage(e.getClass.getSimpleName, data = Json.toJson(e))
        case e: GameStateChanged  => StreamMessage(e.getClass.getSimpleName, data = Json.toJson(e))
      }
    )
  }

  /**
   * curl -X PATCH 'http://localhost:9000/api/game/${GAME_ID}/join' -H "Authorization: Bearer $JWT1"  | jq .
   */
  override def joinGame(id: String): ServiceCall[NotUsed, ConfirmationMessage] = {
    authorize(isAuthenticated[CommonProfile](), (profile: CommonProfile) =>
      ServerServiceCall { _: NotUsed =>
        val userId = profile.getId
        val game = entityRef(id)

        game.askWithStatus(replyTo => Join(id, userId, replyTo))
          .map( _ => AcceptedMessage(id) ).mapTo[ConfirmationMessage]
          .recover {
            case ErrorMessage(reason) => RejectedMessage(reason)
            case _ => RejectedMessage(s"[$id] Failed to join the game.")
          }
      })
  }

  /**
   * curl -X PATCH 'http://localhost:9000/api/game/${GAME_ID}/status?value=active' -H "Authorization: Bearer $JWT" | jq .
   */
  override def updateGame(gameId: String, attribute: String, value: String): ServiceCall[NotUsed, ConfirmationMessage] = ServiceCall { _ =>

    //    val game = entityRef(gameId)
    //
    //    val gameResponse: Future[Done] = attribute match {
    ////      case "status" => game ? (self => ChangeStatus(gameId, value, self))
    //      case _ => Future.successful(StatusReply.error[Done](s"[$gameId] Unknown game attribute [$attribute]"))
    //    }
    //
    //    gameResponse map {
    //      case a: Accepted    => AcceptedMessage()
    //      case e: ErrorReply  => RejectedMessage(e.reason)
    //    }

    Future.successful(RejectedMessage("Not implemented yet"))
  }

}
