package io.hat.shootout.impl

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.api.transport.{BadRequest, NotFound}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{Format, Json, _}

import scala.collection.immutable.Seq

object GameBehavior {

  def create(entityContext: EntityContext[GameCommand]): Behavior[GameCommand] = {
    val persistenceId: PersistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
    create(persistenceId).withTagger(AkkaTaggerAdapter.fromLagom(entityContext, GameEvent.Tag))
  }

  private[impl] def create(persistenceId: PersistenceId) = EventSourcedBehavior
    .withEnforcedReplies[GameCommand, GameEvent, GameState](
        persistenceId = persistenceId,
        emptyState = GameState.initial,
        commandHandler = (state, cmd) => state.applyCommand(cmd),
        eventHandler = (state, evt) => state.applyEvent(evt)
      )
}


sealed trait GameState {
  lazy val status: String = this.getClass.getSimpleName
  def applyCommand(cmd: GameCommand): ReplyEffect[GameEvent, GameState]
  def applyEvent(evt: GameEvent): GameState
}

object GameState {
  val typeKey: EntityTypeKey[GameCommand] = EntityTypeKey[GameCommand]("Game")
  def initial: GameState = GameNotExistsState
}

case object GameNotExistsState extends GameState {

  override def applyCommand(cmd: GameCommand): ReplyEffect[GameEvent, GameState] = cmd match {
    case CreateGame(id, name, ownerId, replyTo) =>
      Effect
        .persist( GameCreated(name, ownerId) )
        .thenReply(replyTo) { newState => Game(id, name, ownerId, newState.status, Seq(ownerId)) }
    case c: GameCommand => throw NotFound(s"[${c.id}] Game not exists.")
  }

  override def applyEvent(evt: GameEvent): GameState = evt match {
    case GameCreated(name, owner) => OpenGameState(name, owner, Seq(owner))
    case _ => GameNotExistsState
  }

}

case class OpenGameState(name: String, owner: String, players: Seq[String]) extends GameState {

  override def applyCommand(cmd: GameCommand): ReplyEffect[GameEvent, GameState] = cmd match {
    case GetGame(id, replyTo) => Effect.reply(replyTo)(Game(id, name, owner, status, players))
//    case StartGame() =>
//    case Join(playerId: String) =>
    case unsupported => throw BadRequest(s"[${unsupported.id}] Command unsupported [$unsupported]")
  }

  override def applyEvent(evt: GameEvent): GameState = evt match {
    case _ => this
  }

}

sealed trait GameEvent extends AggregateEvent[GameEvent] {
  def aggregateTag: AggregateEventTag[GameEvent] = GameEvent.Tag
}

object GameEvent {
  val Tag: AggregateEventTag[GameEvent] = AggregateEventTag[GameEvent]
}

case class GameCreated(name: String, owner: String) extends GameEvent
object GameCreated {
  implicit val format: Format[GameCreated] = Json.format[GameCreated]
}


/**
 * Commands
 * GameCommandSerializable - need to use Jackson for commands serialization as it copes with `replyTo` field
 */
trait GameCommandSerializable

sealed trait GameCommand extends GameCommandSerializable {
  val id: String
  val replyTo: ActorRef[_]
}

case class GetGame(id: String, replyTo: ActorRef[Game]) extends GameCommand
case class CreateGame(id: String, name: String, owner: String, replyTo: ActorRef[Game]) extends GameCommand


/**
 * Replies
 */
case class Game(id: String, name: String, owner: String, status: String, players: Seq[String])

case class GameNotExists(id: String)
object GameNotExists {
  implicit val format: Format[GameNotExists] = Json.format[GameNotExists]
}

sealed trait Confirmation

case object Confirmation {
  implicit val format: Format[Confirmation] = new Format[Confirmation] {
    override def reads(json: JsValue): JsResult[Confirmation] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[Rejected](json)
      else
        Json.fromJson[Accepted](json)
    }

    override def writes(o: Confirmation): JsValue = {
      o match {
        case acc: Accepted => Json.toJson(acc)
        case rej: Rejected => Json.toJson(rej)
      }
    }
  }
}

sealed trait Accepted extends Confirmation
case object Accepted extends Accepted {
  implicit val format: Format[Accepted] = Format(Reads(_ => JsSuccess(Accepted)), Writes(_ => Json.obj()))
}

case class Rejected(reason: String) extends Confirmation
object Rejected {
  implicit val format: Format[Rejected] = Json.format
}


/**
 * Serializers for Events, States and Replies
 */
object GameSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // States
//    JsonSerializer[GameState],
    // Events
    JsonSerializer[GameCreated],
    // Replies
    JsonSerializer[GameNotExists],
    JsonSerializer[Confirmation],
    JsonSerializer[Accepted],
    JsonSerializer[Rejected]
  )
}
