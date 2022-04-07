package com.jobcoin.p2p

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.jobcoin.AppConfig
import com.jobcoin.domain.{TransactionHistory, ZoneIdUTC}
import com.jobcoin.mixer.{Account, Mixer}
import com.jobcoin.serialization.CborSerializable
import org.slf4j.Logger

import java.time.{ZoneId, ZonedDateTime}

/**
 *  P2pJobcoin uses Akka Persistence to represent cryptocurrency ledger.  It maintains cryptocurrency's address,
 *  balance, and all transactions executed between itself and other network participants.
 *
 *  To scale out P2P Jobcoin system, use Akka cluster sharding with this Persistence actor.
 */
object P2pJobcoin {

  val name      = "p2p-jobcoin"

  val NewCoin   = "(new)"

  sealed trait Command
  final case class DoCreate(address: String, amount: Double, sender: ActorRef[Account.AcctResp]) extends Command
  final case class DoTransfer(
      fromAddress: String,
      toAddress: String,
      amount: Double,
      transferTime: ZonedDateTime,
      sender: ActorRef[Account.Message]) extends Command
  final case class GetBalance(address: String, sender: ActorRef[Account.AcctResp]) extends Command
  final case class GetHistory(address: String, sender: ActorRef[Account.AcctResp]) extends Command
  final case class GetAllAddresses(replyTo: ActorRef[Mixer.Message])               extends Command

  sealed trait Event extends CborSerializable
  final case class Create(address: String, amount: Double, datetime: ZonedDateTime) extends Event
  final case class Transfer(fromAddress: String, toAddress: String, amount: Double, datetime: ZonedDateTime) extends Event

  final case class AddressState(address: String, balance: Double, transfers: Seq[Transfer]) extends CborSerializable
  type State = Map[String, AddressState]

  def validateTransfer(from: String, to: String, amount: Double, state: State): Option[String] = {
    val fromSnapshot = state.get(from)
    val toSnapshot   = state.get(to)

    (fromSnapshot, toSnapshot) match {
      case (None, None)       => Some(s"Missing both From Address $from and To address $to.")
      case (None, Some(_))    => Some(s"Missing From Address $from.")
      case (Some(_), None)    => Some(s"Missing To address $to.")
      case (Some(f), Some(_)) =>
        if (f.balance >= amount) None
        else Some(s"Not enough fund for the amount $amount")
    }
  }

  /**
   * Consolidate multiple Transfers into TransactionHistory.  All Transfers with the same transferTime
   * belongs to 1 TransactionHistory.
   */
  def toTransactions(transfers: Seq[Transfer]): List[TransactionHistory] = {
    val byTransferTime = transfers.groupBy(_.datetime)

    val transactions   = byTransferTime.map {
      case (time, transfers) =>
        val init = TransactionHistory("", "", 0, time)
        transfers.foldLeft(init){
          (accum, t) => init.copy(fromAddress = t.fromAddress, toAddress = t.toAddress, amount = accum.amount + t.amount )
        }
    }

    transactions.toList
  }

  def commandHandler(logger: Logger)(currentState: State, cmd: Command): ReplyEffect[Event, State] =
    cmd match {
      case DoCreate(address, amount, sender) =>
        currentState.get(address) match {
          case Some(_) =>
            Effect.none.thenReply(sender)(_ => Account.TransactionError(s"Address exists: $address"))
          case None =>
            Effect.persist(Create(address, amount, ZonedDateTime.now(ZoneIdUTC))).thenReply(sender) {
              state: State =>
                logger.info(s"Created $address with $amount Jobcoins, total addresses ${state.size} in system.")
                Account.TransactionDone
            }
        }

      case DoTransfer(from, to, amount, transferTime, sender) =>
        import Account.{ TransactionDone, TransactionError, TransactionResp }
        validateTransfer(from, to, amount, currentState: State) match {
          case Some(error) =>
            Effect.none.thenReply(sender)(_ => TransactionResp(TransactionError(error)))
          case None =>
            Effect.persist(Transfer(from, to, amount, transferTime)).thenReply(sender) {
              state: State =>
                logger.info(s"Transferred $amount Jobcoins from $from to $to, total addresses ${state.size} in system.")
                TransactionResp(TransactionDone)
            }
        }

      case GetBalance(address, sender) =>
        val response = currentState.get(address).map { s =>
          Account.Balance(address, s.balance, toTransactions(s.transfers))
        }
        response match {
          case Some(resp) =>
            Effect.none.thenReply(sender) { _: State =>
              logger.info(s"Got balance for $address, balance = ${resp.address}")
              resp
            }
          case None =>
            Effect.none.thenReply(sender) { _: State =>
              logger.error(s"No balance for $address, address does not exist.")
              Account.TransactionDone
            }
        }

      case GetHistory(address, sender) =>
        val response = currentState.get(address).map { s =>
          // Only return Transfers this `address` initiated with P2pJobcoin
          val filteredTransfers = s.transfers.filter {
            t => t.fromAddress == address || (t.fromAddress == NewCoin && t.toAddress == address)
          }
          Account.P2pHistory(
            address,
            toTransactions(filteredTransfers)
          )
        }
        response match {
          case Some(resp) =>
            Effect.none.thenReply(sender) { _: State =>
              logger.info(s"Got history for $address")
              resp
            }
          case None =>
            Effect.none.thenReply(sender) { _: State =>
              val error = s"No history for $address, address does not exist."
              logger.info(error)
              Account.TransactionError(error)
            }
        }

      case GetAllAddresses(replyTo) =>
        Effect.none.thenReply(replyTo) { s: State =>
          logger.info(s"Got all address, total ${s.size}.")
          Mixer.Initialized(s.keys.toList)
        }
    }

  /**
   * Update state with current Event.  State holds a Map of address to AddressState
   */
  def eventHandler(): (State, Event) => State = {
    (state, evt) =>
      evt match {
        case Create(address, amount, datetime) =>
          val transfer = Transfer(NewCoin, address, amount, datetime)
          val newState = AddressState(address, amount, List(transfer))
          state.updated(address, newState)

        case trans@Transfer(fromAddress, toAddress, amount, _) =>
          val updatedState = for {
            fs <- state.get(fromAddress)
            ts <- state.get(toAddress)
          } yield {
            val updatedFrom =
              state.updated(fromAddress, fs.copy(balance = fs.balance - amount, transfers = trans +: fs.transfers))
            updatedFrom.updated(toAddress, ts.copy(balance = ts.balance + amount, transfers = trans +: ts.transfers))
          }
          updatedState.getOrElse(state)
      }
  }

  def apply(actorName: String = name)(implicit appConf: AppConfig): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior
        .withEnforcedReplies[Command, Event, State](
          persistenceId = PersistenceId.ofUniqueId(actorName),
          emptyState = Map.empty[String, AddressState],
          commandHandler = commandHandler(context.log),
          eventHandler = eventHandler()
        )
        .snapshotWhen {
          case (state, _, _) =>
            val total = state.size
            total != 0 && total % 10 == 0 // take snapshot every 10 cryptocurrency created
        }
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 2))
        .withTagger(_ => Set(appConf.persistenceTagP2P))
    }

}
