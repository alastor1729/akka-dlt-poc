package com.jobcoin

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.RouteConcatenation.concat
import com.jobcoin.route.{JobcoinRoutes, SwaggerDocRoutes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.Http
import com.jobcoin.mixer.Mixer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class JobcoinServer(implicit val mixerSystem: ActorSystem[Mixer.Message], appConfig: AppConfig) extends JobcoinRoutes {

  implicit val ec: ExecutionContext = mixerSystem.executionContext

  private[jobcoin] def createSwaggerEndpoints(): Route = new SwaggerDocRoutes().routes

  def startHttp(implicit mixerSystem: ActorSystem[Mixer.Message]): Future[Http.ServerBinding] = {

    val restRoutes = concat(createRestRoutes, createSwaggerEndpoints())

    Http().newServerAt(appConfig.host, appConfig.port).bindFlow(Route.toFlow(restRoutes))
  }

  def run(implicit mixerSystem: ActorSystem[Mixer.Message]): Map[String, Future[Http.ServerBinding]] = {

    val restBinding: Future[Http.ServerBinding] = startHttp

    restBinding.foreach { b =>
      mixerSystem.log.info(s"Jobcoin REST server (all endpoints and swagger) bound to: ${b.localAddress}")
      b.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds)
    }

    val bindings: Map[String, Future[Http.ServerBinding]] = Map("http" -> restBinding)

    bindings
  }

}
