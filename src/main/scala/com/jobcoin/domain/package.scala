package com.jobcoin

import java.time.{ZoneId, ZonedDateTime}

package object domain {

  val ZoneIdUTC = ZoneId.of("UTC")

  final case class Transaction(fromAddress: String, toAddress: String, amount: Double)

  final case class TransactionHistory(fromAddress: String, toAddress: String, amount: Double, timestamp: ZonedDateTime)

  final case class AddressInfo(address: String, balance: Double, transactions: List[TransactionHistory])

  final case class NewTransaction(toAddress: String, amount: Double)

}
