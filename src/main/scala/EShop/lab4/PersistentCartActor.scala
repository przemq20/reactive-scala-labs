package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId,
        Empty,
        commandHandler(context),
        eventHandler(context)
      )
    }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] =
    (state, command) => {
      state match {
        case Empty =>
          command match {
            case AddItem(item) => Effect.persist(ItemAdded(item))
            case GetItems(sender) =>
              sender ! Cart.empty
              Effect.none
            case _ => Effect.none
          }

        case NonEmpty(cart, _) =>
          command match {
            case AddItem(item)                                            => Effect.persist(ItemAdded(item))
            case RemoveItem(item) if cart.contains(item) && cart.size > 1 => Effect.persist(ItemRemoved(item))
            case RemoveItem(item) if cart.contains(item)                  => Effect.persist(CartEmptied)
            case StartCheckout(orderManagerRef) =>
              val checkoutActor = context.spawn(new TypedCheckout(context.self).start, "CheckoutActor")
              Effect.persist(CheckoutStarted(checkoutActor)).thenRun { _ =>
                checkoutActor ! TypedCheckout.StartCheckout
                orderManagerRef ! OrderManager.ConfirmCheckoutStarted(checkoutActor)
              }
            case ExpireCart => Effect.persist(CartExpired)
            case GetItems(sender) =>
              sender ! cart
              Effect.none
            case _ => Effect.none
          }

        case InCheckout(cart) =>
          command match {
            case ConfirmCheckoutCancelled => Effect.persist(CheckoutCancelled)
            case ConfirmCheckoutClosed    => Effect.persist(CheckoutClosed)
            case GetItems(sender) =>
              sender ! cart
              Effect.none
            case _ => Effect.none
          }
      }
    }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State =
    (state, event) => {
      def timer             = state.timerOpt.get
      def stopTimer(): Unit = state.timerOpt.foreach(_.cancel)

      event match {
        case CheckoutStarted(_) =>
          stopTimer()
          InCheckout(state.cart)
        case ItemAdded(item)   => NonEmpty(state.cart.addItem(item), scheduleTimer(context))
        case ItemRemoved(item) => NonEmpty(state.cart.removeItem(item), timer)
        case CartEmptied | CartExpired =>
          stopTimer()
          Empty
        case CheckoutClosed    => Empty
        case CheckoutCancelled => NonEmpty(state.cart, scheduleTimer(context))
      }
    }

}
