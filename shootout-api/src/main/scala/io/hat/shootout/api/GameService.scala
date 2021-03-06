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
   * curl -s "http://localhost:9000/api/game/${GAME_ID}" | jq .
   */
  def getGame(id: String): ServiceCall[NotUsed, GameMessage]

  /**
   * curl -X POST -s "http://localhost:9000/api/game?name=wild-west" -H "Authorization: Bearer $JWT" | jq .
   */
  def createGame(name: String): ServiceCall[NotUsed, ConfirmationMessage]

  /**
   * curl "http://localhost:9000/api/game/${GAME_ID}/stream"
   */
  def gameStream(id: String): ServiceCall[NotUsed, Source[StreamMessage, NotUsed]]

  /**
   * curl -X PATCH "http://localhost:9000/api/game/${GAME_ID}/status?value=active" -H "Authorization: Bearer $JWT"  | jq .
   */
  def updateGame(id: String, attribute: String, value: String): ServiceCall[NotUsed, ConfirmationMessage]

  /**
   * curl -X PATCH "http://localhost:9000/api/game/${GAME_ID}/join" -H "Authorization: Bearer $JWT1"  | jq .
   */
  def joinGame(id: String): ServiceCall[NotUsed, ConfirmationMessage]

  /**
   * curl -X PATCH "http://localhost:9000/api/game/${GAME_ID}/start" -H "Authorization: Bearer $JWT"  | jq .
   */
  def startGame(id: String): ServiceCall[NotUsed, ConfirmationMessage]


  /**
   * This gets published to Kafka.
   */
  //  def gameStream(id: String): Topic[String]

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("game")
      .withCalls(
        restCall(Method.POST, "/api/game?name", createGame _),
        restCall(Method.GET, "/api/game/:id", getGame _),
        restCall(Method.PATCH, "/api/game/:id/join", joinGame _),
        restCall(Method.PATCH, "/api/game/:id/start", startGame _),
        restCall(Method.PATCH, "/api/game/:id/:atribute?value", updateGame _),
        restCall(Method.GET, "/api/game/:id/stream", gameStream _),
      )
      .withAutoAcl(true)
  }

}

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


case class AcceptedMessage(message: String) extends ConfirmationMessage
case object AcceptedMessage {
  implicit val format: Format[AcceptedMessage] = Json.format
}

case class RejectedMessage(reason: String) extends ConfirmationMessage
object RejectedMessage {
  implicit val format: Format[RejectedMessage] = Json.format
}

case class StreamMessage(`type`: String, data: JsValue)
object StreamMessage {
  implicit val format: Format[StreamMessage] = Json.format
}



