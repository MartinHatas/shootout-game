package io.hat.shootout.impl

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.pattern.StatusReply.Success
import akka.persistence.typed.PersistenceId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class HelloAggregateSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with AnyWordSpecLike with Matchers {

  "Game Aggregate" should {

    "return GameState " in {
      val probe = createTestProbe[StatusReply[GameReply]]()
      val ref = spawn(GameBehavior.create(PersistenceId("fake-type-hint", "fake-id")))
      ref ! GetGame("123", probe.ref)
      probe.expectMessage(Success(GameReply("", "", "", "pre-start", Seq())))
    }

//    "allow updating the greeting message" in  {
//      val ref = spawn(GameBehavior.create(PersistenceId("fake-type-hint", "fake-id")))
//
//      val probe1 = createTestProbe[Confirmation]()
//      ref ! UseGreetingMessage("Hi", probe1.ref)
//      probe1.expectMessage(Accepted)
//
//      val probe2 = createTestProbe[Greeting]()
//      ref ! Hello("Alice", probe2.ref)
//      probe2.expectMessage(Greeting("Hi, Alice!"))
//    }

  }
}
