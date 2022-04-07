package com.jobcoin

import com.typesafe.config.Config

case class AppConfig(config: Config) {
  val host: String = config.getString("jobcoin.server.host")
  val port: Int    = config.getInt("jobcoin.server.port")

  val persistenceTagP2P: String = config.getString("jobcoin.persist-tag.p2p-jobcoin")
}
