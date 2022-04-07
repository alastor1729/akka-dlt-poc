package com.jobcoin.serialization

/**
  * Marker trait to tell Akka to serialize messages into CBOR using Jackson for persistence.
  *
  * See application.conf where it is bound to a serializer
  */
trait CborSerializable
