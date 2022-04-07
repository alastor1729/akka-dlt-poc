package com.jobcoin.mixer

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import com.jobcoin.domain.TransactionHistory
import com.jobcoin.p2p.P2pJobcoin

object Mixer {

  val name = "mixer-system"

  val stashCapacity = 1000

  sealed trait Message
  final object Initialization                           extends Message
  final case class Initialized(addresses: List[String]) extends Message
  final case class WrappedResp(resp: Account.AcctResp)  extends Message
  final case class Create(address: String, amount: Double, replyTo: ActorRef[Account.AcctResp]) extends Message
  final case class Transfer(
      fromAddress: String,
      toAddress: String,
      amount: Double,
      replyTo: ActorRef[Account.AcctResp]) extends Message
  final case class AddressInfo(sender: ActorRef[List[Account.Balance]]) extends Message
  final case class AddressHistory(sender: ActorRef[List[TransactionHistory]]) extends Message

  final case class AddressInfoState(accounts: Int, replyTo: ActorRef[List[Account.Balance]], balances: List[Account.Balance] = Nil)
  final case class AddressHistoryState(accounts: Int, replyTo: ActorRef[List[TransactionHistory]], transactions: List[TransactionHistory] = Nil)

  private var accountActors: Map[String, ActorRef[Account.Message]] = Map.empty

  // Actor hehaviors
  def ready(context: ActorContext[Message], buffer: StashBuffer[Message])
           (implicit p2p: ActorRef[P2pJobcoin.Command], wrappedReplyTo: ActorRef[Account.AcctResp]): Behavior[Message] =
    Behaviors.receiveMessagePartial[Message] {
      case Initialization =>
        p2p ! P2pJobcoin.GetAllAddresses(context.self)
        ready(context, buffer)

      case Initialized(addresses) =>
        addresses.foreach { address =>
          val account = context.spawn(Account(address), s"${Account.prefix}$address")
          accountActors = accountActors.updated(address, account)
        }
        ready(context, buffer)

      case Create(address, amount, replyTo) =>
        accountActors.get(address) match {
          case Some(account) => account ! Account.Create(amount, replyTo)
          case None =>
            val account = context.spawn(Account(address), s"${Account.prefix}$address")
            accountActors = accountActors.updated(address, account)
            account ! Account.Create(amount, replyTo)
        }
        ready(context, buffer)

      case Transfer(fromAddr, toAddr, amount, replyTo) =>
        accountActors.get(fromAddr) match {
          case Some(account) => account ! Account.Transfer(toAddr, amount, replyTo)
          case None          => replyTo ! Account.TransactionError(s"$fromAddr not in system, please create one.")
        }
        ready(context, buffer)

      case AddressInfo(sender) =>
        accountActors.values.foreach {
          account => account ! Account.AddressInfo(wrappedReplyTo)
        }
        val initState = AddressInfoState(accountActors.size, sender)
        waitingAddressInfo(context, buffer, initState)

      case AddressHistory(sender) =>
        accountActors.values.foreach {
          account => account ! Account.AddressHistory(wrappedReplyTo)
        }
        val initState = AddressHistoryState(accountActors.size, sender)
        waitingHistories(context, buffer, initState)
    }

  def waitingHistories(context: ActorContext[Message], buffer: StashBuffer[Message], state: AddressHistoryState)
                      (implicit p2p: ActorRef[P2pJobcoin.Command], wrappedReplyTo: ActorRef[Account.AcctResp]): Behavior[Message] =
    Behaviors.receiveMessagePartial[Message] {
      case WrappedResp(resp: Account.P2pHistory)=>
        val newState = state.copy(accounts = state.accounts - 1, transactions = resp.transactions.toList ++ state.transactions)
        if (newState.accounts > 0) {
          waitingHistories(context, buffer, newState)
        } else {
          newState.replyTo ! newState.transactions
          // stashed messages are replayed
          buffer.unstashAll(ready(context, buffer))
        }

      case message => // in waiting of response from P2pJobcoin
        buffer.stash(message)
        Behaviors.same
    }

  def waitingAddressInfo(context: ActorContext[Message], buffer: StashBuffer[Message], state: AddressInfoState)
                      (implicit p2p: ActorRef[P2pJobcoin.Command], wrappedReplyTo: ActorRef[Account.AcctResp]): Behavior[Message] =
    Behaviors.receiveMessagePartial[Message] {
      case WrappedResp(resp: Account.Balance)=>
        val newState = state.copy(accounts = state.accounts - 1, balances = resp :: state.balances)
        if (newState.accounts > 0) {
          waitingAddressInfo(context, buffer, newState)
        } else {
          newState.replyTo ! newState.balances
          // stashed messages are replayed
          buffer.unstashAll(ready(context, buffer))
        }

      case message => // in waiting of response from P2pJobcoin
        buffer.stash(message)
        Behaviors.same
    }


  def apply()(implicit p2p: ActorRef[P2pJobcoin.Command]): Behavior[Message] =
    Behaviors.withStash(stashCapacity) { buffer =>
      Behaviors.setup[Message] { context =>
        implicit val respWrapper: ActorRef[Account.AcctResp] = context.messageAdapter(WrappedResp.apply)
        ready(context, buffer)
      }
    }

}
