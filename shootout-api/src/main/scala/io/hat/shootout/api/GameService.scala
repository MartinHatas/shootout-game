package io.hat.shootout.api

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json._

object GameService  {
  val TOPIC_NAME = "game"
}

trait GameService extends Service {

  /**
    * curl -s http://localhost:9000/api/game/19f0c829-17ff-401d-9c5f-ffc661302dfa | jq .
    */
  def getGame(id: String): ServiceCall[NotUsed, GameMessage]

  /**
    * curl -X POST -s 'http://localhost:9000/api/game?name=wild-west' | jq .
    */
  def createGame(name: String): ServiceCall[NotUsed, GameMessage]

  /**
   * curl 'http://localhost:9000/api/game/19f0c829-17ff-401d-9c5f-ffc661302dfa/stream'
   */
  def gameStream(id: String): ServiceCall[NotUsed, Source[String, NotUsed]]

  /**
   * curl -X PATCH 'http://localhost:9000/api/game/19f0c829-17ff-401d-9c5f-ffc661302dfa/status?value=active'
   */
  def updateGame(id: String, attribute: String, value: String): ServiceCall[NotUsed, ConfirmationMessage]

  /**
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"message":
    * "Hi"}' http://localhost:9000/api/hello/Alice
    */
//  def useGreeting(id: String): ServiceCall[String, Done]

  /**
    * This gets published to Kafka.
    */
//  def greetingsTopic(): Topic[String]

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("game")
      .withCalls(
        restCall(Method.POST ,"/api/game?name", createGame _),
        restCall(Method.GET, "/api/game/:id", getGame _),
        restCall(Method.PATCH, "/api/game/:id/:atribute?value", updateGame _),
        restCall(Method.GET, "/api/game/:id/stream", gameStream _),
      )
//      .withTopics(
//        topic(GameService.TOPIC_NAME, greetingsTopic _)
//          // Kafka partitions messages, messages within the same partition will
//          // be delivered in order, to ensure that all messages for the same user
//          // go to the same partition (and hence are delivered in order with respect
//          // to that user), we configure a partition key strategy that extracts the
//          // name as the partition key.
//          .addProperty(
//            KafkaProperties.partitionKeyStrategy,
//            PartitionKeyStrategy[String](_)
//          )
//      )
      .withAutoAcl(true)
    // @formatter:on
  }
}

/**
  * The greeting message class.
  */
//case class GreetingMessage(message: String)
//
//object GreetingMessage {
//  /**
//    * Format for converting greeting messages to and from JSON.
//    *
//    * This will be picked up by a Lagom implicit conversion from Play's JSON format to Lagom's message serializer.
//    */
//  implicit val format: Format[GreetingMessage] = Json.format[GreetingMessage]
//}

case class GameMessage(id: String, name: String, owner: String, status: String, players: Seq[String])

object GameMessage {
  implicit val format: Format[GameMessage] = Json.format[GameMessage]
}

sealed trait ConfirmationMessage

case object ConfirmationMessage {
  implicit val format: Format[ConfirmationMessage] = new Format[ConfirmationMessage] {
    override def reads(json: JsValue): JsResult[ConfirmationMessage] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[RejectedMessage](json)
      else
        Json.fromJson[AcceptedMessage](json)
    }

    override def writes(o: ConfirmationMessage): JsValue = {
      o match {
        case acc: AcceptedMessage => Json.toJson(acc)
        case rej: RejectedMessage => Json.toJson(rej)
      }
    }
  }
}


sealed trait AcceptedMessage extends ConfirmationMessage

case object AcceptedMessage extends AcceptedMessage {
  implicit val format: Format[AcceptedMessage] = Format(Reads(_ => JsSuccess(AcceptedMessage)), Writes(_ => Json.obj()))
}

case class RejectedMessage(reason: String) extends ConfirmationMessage

object RejectedMessage {
  implicit val format: Format[RejectedMessage] = Json.format
}



