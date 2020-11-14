package io.hat.shootout.impl

import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import io.hat.shootout.api.{GameMessage, GameService}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class HelloServiceSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  private val server = ServiceTest.startServer(
    ServiceTest.defaultSetup
      .withCassandra()
  ) { ctx =>
    new GameApplication(ctx) with LocalServiceLocator
  }

  val client: GameService = server.serviceClient.implement[GameService]

  override protected def afterAll(): Unit = server.stop()

  "hello service" should {

    "say hello" in {
      client.getGame("123").invoke().map { answer =>
        answer should === (GameMessage("", "", "", "pre-start", Seq()))
      }
    }

//    "allow responding with a custom message" in {
//      for {
//        _ <- client.useGreeting("Bob").invoke(GreetingMessage("Hi"))
//        answer <- client.hello("Bob").invoke()
//      } yield {
//        answer should ===("Hi, Bob!")
//      }
//    }
  }
}
