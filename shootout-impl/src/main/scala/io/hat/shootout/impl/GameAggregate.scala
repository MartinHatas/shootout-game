package io.hat.shootout.impl

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
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

case class GameState(name: String, owner: String, players: Seq[String], status: String) {

  def applyCommand(cmd: GameCommand): ReplyEffect[GameEvent, GameState] = cmd match {

      case GetGame(id, replyTo)  =>
        Effect
          .reply(replyTo)(Game(id, name, owner, status, players))

      case CreateGame(id, name, ownerId, replyTo) =>
        Effect
          .persist( GameCreated(name, ownerId) )
          .thenReply(replyTo) { state => Game(id, state.name, state.owner, state.status, state.players) }

      case ChangeStatus(newStatus, replyTo) =>

        val statusCanBeUpdated = (status, newStatus) match {
          case ("pre-start", "active")   => true
          case ("pre-start", "finished") => true
          case ("active"   , "finished") => true
          case _                        => false
        }

        if (statusCanBeUpdated) {
          Effect.persist( StatusChanged(newStatus) ).thenReply(replyTo) { _ => Accepted }
        } else {
          Effect.reply(replyTo)( Rejected(s"Current status [$status] can not be updated to [$newStatus]") )
        }

    }

  def applyEvent(evt: GameEvent): GameState =
    evt match {
      case GameCreated(name, owner) => copy(name = name, owner = owner, players = Seq(owner))
      case StatusChanged(status) => copy(status = status)
    }
}

object GameState {

  implicit val format: Format[GameState] = Json.format

  val typeKey: EntityTypeKey[GameCommand] = EntityTypeKey[GameCommand]("GameAggregate")

  def initial: GameState = GameState("", "", Seq(), "pre-start")
}


sealed trait GameEvent extends AggregateEvent[GameEvent] {
  def aggregateTag: AggregateEventTag[GameEvent] = GameEvent.Tag
}

object GameEvent {
  val Tag: AggregateEventTag[GameEvent] = AggregateEventTag[GameEvent]
}

case class GameCreated(name: String, owner: String) extends GameEvent
case class StatusChanged(status: String) extends GameEvent

object GameCreated {
  implicit val format: Format[GameCreated] = Json.format[GameCreated]
}

object StatusChanged {
  implicit val format: Format[StatusChanged] = Json.format[StatusChanged]
}

/**
  * This is a marker trait for commands.
  * We will serialize them using Akka's Jackson support that is able to deal with the replyTo field.
  * (see application.conf)
  */
trait GameCommandSerializable

/**
  * This interface defines all the commands that the HelloAggregate supports.
  */
sealed trait GameCommand
    extends GameCommandSerializable

/**
  * A command to switch the greeting message.
  *
  * It has a reply type of [[Confirmation]], which is sent back to the caller
  * when all the events emitted by this command are successfully persisted.
  */
case class UseGreetingMessage(message: String, replyTo: ActorRef[Confirmation]) extends GameCommand

case class GetGame(id: String, replyTo: ActorRef[Game]) extends GameCommand
case class CreateGame(id: String, name: String, owner: String, replyTo: ActorRef[Game]) extends GameCommand
case class ChangeStatus(status: String, replyTo: ActorRef[Confirmation]) extends GameCommand

final case class Greeting(message: String)

final case class Game(id: String, name: String, owner: String, status: String, players: Seq[String])

object Greeting {
  implicit val format: Format[Greeting] = Json.format
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

object GameSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    JsonSerializer[GameCreated],
    JsonSerializer[StatusChanged],
    JsonSerializer[GameState],
    JsonSerializer[Greeting],
    JsonSerializer[Confirmation],
    JsonSerializer[Accepted],
    JsonSerializer[Rejected]
  )
}
