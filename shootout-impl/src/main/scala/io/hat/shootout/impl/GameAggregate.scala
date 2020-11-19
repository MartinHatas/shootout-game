package io.hat.shootout.impl

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import io.hat.shootout.impl.GameState.MaxPlayers
import play.api.libs.json.{Format, Json}

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
  val MaxPlayers = 8
  val typeKey: EntityTypeKey[GameCommand] = EntityTypeKey[GameCommand]("Game")
  def initial: GameState = GameNotExistsState
}

case object GameNotExistsState extends GameState {

  override def applyCommand(cmd: GameCommand): ReplyEffect[GameEvent, GameState] = cmd match {
    case CreateGame(id, name, ownerId, replyTo) =>
      Effect
        .persist( GameCreated(name, ownerId) )
        .thenReply(replyTo) { _ => StatusReply.Ack }
    case c: GameCommand => Effect.reply(c.ref)( StatusReply.error[String]("Game not exists.") )
  }

  override def applyEvent(evt: GameEvent): GameState = evt match {
    case GameCreated(name, owner) => OpenGameState(name, owner, Seq(owner))
    case _ => GameNotExistsState
  }

}

case class OpenGameState(name: String, owner: String, players: Seq[String]) extends GameState {

  override def applyCommand(cmd: GameCommand): ReplyEffect[GameEvent, GameState] = cmd match {

    case GetGame(id, replyTo) => Effect.reply(replyTo)( StatusReply.success(Game(id, name, owner, status, players)) )

    case Join(_, userId, replyTo) if players.contains(userId) => Effect.reply(replyTo)( StatusReply.Ack )
    case Join(_, _, replyTo) if players.size >= MaxPlayers => Effect.reply(replyTo)( StatusReply.Error("Invalid command") )
    case Join(_, userId, replyTo) => Effect.persist( PlayerJoined(userId) ).thenReply(replyTo) { _ => StatusReply.Ack }


//    case StartGame() =>

    case unsupported => throw BadRequest(s"[${unsupported.id}] Command unsupported [$unsupported]")
  }

  override def applyEvent(evt: GameEvent): GameState = evt match {
    case PlayerJoined(userId) => copy(players = players :+ userId)
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

case class PlayerJoined(userId: String) extends GameEvent
object PlayerJoined {
  implicit val format: Format[PlayerJoined] = Json.format[PlayerJoined]
}

/**
 * Commands
 */
sealed trait GameCommand {
  val id: String
  val replyTo: ActorRef[_]
  def ref[T]: ActorRef[T] = replyTo.asInstanceOf[ActorRef[T]]
}

case class GetGame(id: String, replyTo: ActorRef[StatusReply[Game]]) extends GameCommand
case class CreateGame(id: String, name: String, owner: String, replyTo: ActorRef[StatusReply[Done]]) extends GameCommand
case class Join(id: String, userId: String, replyTo: ActorRef[StatusReply[Done]]) extends GameCommand

/**
 * Replies
 */
case class Game(id: String, name: String, owner: String, status: String, players: Seq[String])
case object Game {
  implicit val format: Format[Game] = Json.format[Game]
}

/**
 * Serializers for Events, States and Replies
 */
object GameSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // States
//    JsonSerializer[GameState],
    // Events
    JsonSerializer[PlayerJoined],
    JsonSerializer[GameCreated],
    // Replies
    JsonSerializer[Game],
  )
}
