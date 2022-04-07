package com.jobcoin

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, ActorSystem => TypedActorSystem}
import com.jobcoin.mixer.Mixer
import com.jobcoin.p2p.P2pJobcoin
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object JobcoinSystem {

  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>

      //setup application configurations
      implicit val appConf: AppConfig = AppConfig(context.system.settings.config)

      implicit val system: TypedActorSystem[Nothing] = context.system
      implicit val ec: ExecutionContextExecutor      = system.executionContext

      // P2P System.  To scale out, use akka cluster sharding
      implicit val p2pJobcoin: ActorRef[P2pJobcoin.Command] = context.spawn(P2pJobcoin(), P2pJobcoin.name)

      // Mixer system
      implicit val mixerSystem: TypedActorSystem[Mixer.Message] = TypedActorSystem(Mixer(), Mixer.name)
      (mixerSystem: ActorRef[Mixer.Message]) ! Mixer.Initialization

      // Start HTTP Server
      val _ = new JobcoinServer().run

      Behaviors.empty
    }

}

object JobcoinApp extends App {

  val conf = ConfigFactory.load()

  // Start Jobcoin REST Server
  val jobcoinSystem = TypedActorSystem[Nothing](JobcoinSystem(), "jobcoin-system", conf)
  jobcoinSystem.log.info("Jobcoin System Started [" + jobcoinSystem + "]")

  jobcoinSystem.log.info(s"Server online at http://0.0.0.0:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return

}
