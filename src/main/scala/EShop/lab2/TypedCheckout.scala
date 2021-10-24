package EShop.lab2

import EShop.lab3.Payment
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(
    payment: String,
    orderManagerCheckoutRef: ActorRef[Event],
    orderManagerPaymentRef: ActorRef[Payment.Event]
  ) extends Command
  case object ExpirePayment          extends Command
  case object ConfirmPaymentReceived extends Command

  sealed trait Event
  case object CheckOutClosed                                       extends Event
  case class PaymentStarted(paymentRef: ActorRef[Payment.Command]) extends Event
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private def checkoutTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)

  private def paymentTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive[TypedCheckout.Command](
    (context, command) =>
      command match {
        case StartCheckout =>
          selectingDelivery(checkoutTimer(context))
        case _ => Behaviors.same
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive[TypedCheckout.Command](
      (context, command) =>
        command match {
          case SelectDeliveryMethod(_) =>
            selectingPaymentMethod(timer)
          case TypedCheckout.CancelCheckout =>
            timer.cancel()
            cancelled
          case TypedCheckout.ExpireCheckout =>
            cancelled
          case _ => Behaviors.same
      }
    )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive[TypedCheckout.Command](
      (context, command) =>
        command match {
          case SelectPayment(payment, orderManagerCheckoutRef, orderManagerPaymentRef) =>
            timer.cancel()
            orderManagerCheckoutRef ! PaymentStarted(
              context.spawn(new Payment(payment, orderManagerPaymentRef, context.self).start, "PaymentActor")
            )
            processingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
          case TypedCheckout.CancelCheckout =>
            timer.cancel()
            cancelled
          case TypedCheckout.ExpireCheckout =>
            cancelled
          case _ => Behaviors.same
      }
    )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive[TypedCheckout.Command](
      (_, command) =>
        command match {
          case ConfirmPaymentReceived =>
            timer.cancel()
            cartActor ! TypedCartActor.ConfirmCheckoutClosed
            closed
          case ExpirePayment =>
            cancelled
          case CancelCheckout =>
            cancelled
          case _ => Behaviors.same
      }
    )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive[TypedCheckout.Command](
    (_, _) => Behaviors.stopped
  )

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive[TypedCheckout.Command](
    (_, _) => Behaviors.stopped
  )

}
