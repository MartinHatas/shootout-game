package io.hat.shootout.impl.auth

import java.util

import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import io.hat.shootout.impl.{GameState, QueryOwner}
import org.pac4j.core.authorization.authorizer.Authorizer
import org.pac4j.core.context.WebContext
import org.pac4j.core.profile.CommonProfile

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.jdk.CollectionConverters._

class GameOwnerAuthorizer[T <: CommonProfile](gameId: String)(implicit clusterSharding: ClusterSharding, ec: ExecutionContext) extends Authorizer[T] {

  implicit val timeout = Timeout(2.seconds)

  override def isAuthorized(context: WebContext, profiles: util.List[T]): Boolean = {
    val isOwnerQuery = clusterSharding
      .entityRefFor(GameState.typeKey, gameId)
      .ask(replyTo => QueryOwner(gameId, replyTo))
      .map(owner => profiles.asScala.iterator.find(owner.id == _.getId))
      .map(_.isDefined)

    Await.result(isOwnerQuery, timeout.duration)
  }
}

object GameOwnerAuthorizer {
  def isGameOwner[T <: CommonProfile](gameId: String)(implicit clusterSharding: ClusterSharding, ec: ExecutionContext) = new GameOwnerAuthorizer[T](gameId)
}
