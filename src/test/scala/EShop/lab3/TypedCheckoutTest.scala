package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import EShop.lab3.TypedCheckoutTest._
import akka.actor.Cancellable
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val cartActor            = testKit.createTestProbe[TypedCartActor.Command]()
    val orderManagerCheckout = testKit.createTestProbe[TypedCheckout.Event]
    val orderManagerPayment  = testKit.createTestProbe[Payment.Event]
    val probe                = testKit.createTestProbe[String]()

    val checkoutActor = checkoutActorWithResponseOnStateChange(testKit, probe.ref, cartActor.ref)

    checkoutActor ! TypedCheckout.StartCheckout
    probe.expectMessage(TypedCheckoutTest.selectingDeliveryMsg)
    checkoutActor ! TypedCheckout.SelectDeliveryMethod("Courier")
    probe.expectMessage(TypedCheckoutTest.selectingPaymentMethodMsg)
    checkoutActor ! TypedCheckout.SelectPayment("Transfer", orderManagerCheckout.ref, orderManagerPayment.ref)
    probe.expectMessage(TypedCheckoutTest.processingPaymentMsg)
    orderManagerCheckout.expectMessageType[TypedCheckout.PaymentStarted]
    checkoutActor ! TypedCheckout.ConfirmPaymentReceived
    cartActor.expectMessage(TypedCartActor.ConfirmCheckoutClosed)
  }

}
object TypedCheckoutTest {
  val selectingDeliveryMsg      = "selectingDelivery"
  val selectingPaymentMethodMsg = "selectingPaymentMethod"
  val processingPaymentMsg      = "processingPayment"

  def checkoutActorWithResponseOnStateChange(
    testkit: ActorTestKit,
    probe: ActorRef[String],
    cartActorProbe: ActorRef[TypedCartActor.Command]
  ): ActorRef[TypedCheckout.Command] =
    testkit.spawn {
      val checkout = new TypedCheckout(cartActorProbe) {
        override def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
          Behaviors.setup(_ => {
            val result = super.selectingDelivery(timer)
            probe.ref ! selectingDeliveryMsg
            result
          })

        override def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] =
          Behaviors.setup(_ => {
            probe.ref ! selectingPaymentMethodMsg
            super.selectingPaymentMethod(timer)
          })

        override def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] =
          Behaviors.setup(_ => {
            probe.ref ! processingPaymentMsg
            super.processingPayment(timer)
          })
      }
      checkout.start
    }
}
