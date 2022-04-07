package com.jobcoin.mixer

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import com.jobcoin.domain.{TransactionHistory, ZoneIdUTC}
import com.jobcoin.p2p.P2pJobcoin

import java.time.ZonedDateTime

object Account {

  val prefix = "mixer-account-"

  val stashCapacity     = 100
  val numberOfTransfers = 10

  sealed trait Message
  final case class Create(amount: Double, sender: ActorRef[Account.AcctResp]) extends Message
  final case class Transfer(
     toAddress: String,
     amount: Double,
     sender: ActorRef[Account.AcctResp]) extends Message
  final case class AddressInfo(sender: ActorRef[Account.AcctResp]) extends Message
  final case class AddressHistory(sender: ActorRef[Account.AcctResp]) extends Message
  final case class TransactionResp[T <: Transaction](resp: T) extends Message

  sealed trait AcctResp
  sealed trait Transaction
  final case class Balance(address: String, balance: Double, transactions: Seq[TransactionHistory]) extends AcctResp
  final case class P2pHistory(address: String, transactions: Seq[TransactionHistory]) extends AcctResp
  final case class TransactionError(msg: String) extends AcctResp with Transaction
  final object TransactionDone extends AcctResp with Message with Transaction

  final case class TransferState[T <: Transaction](replyTo: ActorRef[AcctResp], transactions: Seq[T] = Nil)

  // Actor hehaviors
  def ready(address: String, context: ActorContext[Message], buffer: StashBuffer[Message])
           (implicit p2p: ActorRef[P2pJobcoin.Command]): Behavior[Message] =
    Behaviors.receiveMessagePartial[Message] {
      case Create(amount, replyTo) =>
        p2p ! P2pJobcoin.DoCreate(address, amount, replyTo)
        ready(address, context, buffer)

      case Transfer(toAddr, amount, replyTo) =>
        val smallerAmt = amount / numberOfTransfers
        val transferTime = ZonedDateTime.now(ZoneIdUTC)
        (1 to numberOfTransfers).foreach {
          _ => p2p ! P2pJobcoin.DoTransfer(address, toAddr, smallerAmt, transferTime, context.self)
        }
        // TODO: Add fee, need to add a Mixer account in P2pJobcoin

        val newState = TransferState(replyTo)
        waitingTransfers(address, context, buffer, newState)

      case AddressInfo(sender) =>
        p2p ! P2pJobcoin.GetBalance(address, sender)
        ready(address, context, buffer)

      case AddressHistory(sender) =>
        p2p ! P2pJobcoin.GetHistory(address, sender)
        ready(address, context, buffer)
    }

  def waitingTransfers[T <: Transaction](
      address: String,
      context: ActorContext[Message],
      buffer: StashBuffer[Message],
      state: TransferState[T])(implicit p2p: ActorRef[P2pJobcoin.Command]): Behavior[Message] =
    Behaviors.receiveMessagePartial[Message] {
      case TransactionResp(t) =>
        val total = t +: state.transactions
        if (total.size < numberOfTransfers) {
          waitingTransfers(address, context, buffer, state.copy(transactions = total))
        } else {
          total.find(_.isInstanceOf[TransactionError]) match {
            case Some(error: TransactionError) => state.replyTo ! error
            case _                             => state.replyTo ! TransactionDone
          }
          // stashed messages are replayed
          buffer.unstashAll(ready(address, context, buffer))
        }

      case message => // in waiting of response from P2pJobcoin
        buffer.stash(message)
        Behaviors.same
    }


  def apply(address: String)(implicit p2p: ActorRef[P2pJobcoin.Command]): Behavior[Message] =
    Behaviors.withStash(stashCapacity) { buffer =>
      Behaviors.setup[Message] { context =>
        ready(address, context, buffer)
      }
    }

}
