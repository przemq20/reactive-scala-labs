package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.Effect.{Scheduled, Spawned}
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._
  import TypedCartTest._

  //use GetItems command which was added to make test easier
  it should "synchronous add item properly" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox   = TestInbox[Cart]()

    testKit.run(AddItem(testItem))
    testKit.run(GetItems(inbox.ref))
    inbox.expectMessage(testCart)
  }

  it should "asynchronous add item properly" in {
    val cartActor = testKit.spawn(new TypedCartActor().start)
    val probe     = testKit.createTestProbe[Any]()

    cartActor ! AddItem(testItem)
    cartActor ! GetItems(probe.ref)
    probe.expectMessage(testCart)
  }

  it should "synchronous be empty after adding and removing the same item" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox   = TestInbox[Cart]()

    testKit.run(AddItem(testItem))
    testKit.run(RemoveItem(testItem))
    testKit.run(GetItems(inbox.ref))
    inbox.expectMessage(Cart.empty)
  }

  it should "asynchronous be empty after adding and removing the same item" in {
    val cartActor = testKit.spawn(new TypedCartActor().start)
    val probe     = testKit.createTestProbe[Any]()

    cartActor ! AddItem(testItem)
    cartActor ! RemoveItem(testItem)
    cartActor ! GetItems(probe.ref)
    probe.expectMessage(Cart.empty)
  }

  it should "synchronous start checkout" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox   = TestInbox[OrderManager.Command]()

    testKit.run(AddItem(testItem))
    testKit.run(StartCheckout(inbox.ref))

    testKit.expectEffect(Scheduled(5.seconds, testKit.ref, TypedCartActor.ExpireCart))
    testKit.expectEffectType[Spawned[TypedCheckout]]

    val childInbox = testKit.childInbox[TypedCheckout.Command]("CheckoutActor")
    childInbox.expectMessage(TypedCheckout.StartCheckout)
    inbox.expectMessage(_: OrderManager.ConfirmCheckoutStarted)
  }

  it should "asynchronous start checkout" in {
    val cartActor = testKit.spawn(new TypedCartActor().start)
    val probe     = testKit.createTestProbe[Any]()

    cartActor ! AddItem(testItem)
    cartActor ! StartCheckout(probe.ref)
    probe.expectMessageType[OrderManager.ConfirmCheckoutStarted]
  }

}

object TypedCartTest {
  val testItem: String = "item"
  val testCart: Cart   = Cart.empty.addItem(testItem)
}
