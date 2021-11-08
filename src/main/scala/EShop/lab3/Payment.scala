package EShop.lab3

import EShop.lab2.TypedCheckout
import EShop.lab3.Payment.{DoPayment, Event}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case object DoPayment extends Command
}

class Payment(
  method: String,
  orderManager: ActorRef[OrderManager.Command],
  checkout: ActorRef[TypedCheckout.Command]
) {

  def start: Behavior[Payment.Command] =
    Behaviors.receive(
      (context, command) =>
        command match {
          case DoPayment =>
            orderManager ! OrderManager.ConfirmPaymentReceived
            checkout ! TypedCheckout.ConfirmPaymentReceived
            Behaviors.stopped
          case _ => Behaviors.same
      }
    )

}
