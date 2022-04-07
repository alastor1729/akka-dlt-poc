package com.jobcoin.route

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.jobcoin.domain._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.generic.auto._
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import javax.ws.rs.core.MediaType
import javax.ws.rs.{Consumes, GET, POST, Path, Produces}

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.jobcoin.mixer.{Account, Mixer}

import scala.concurrent.{ExecutionContext, Future}

trait JobcoinRoutes {
  import scala.concurrent.duration._

  implicit val timeout: Timeout = 5.seconds

  implicit val mixerSystem: ActorSystem[Mixer.Message]

  val mixer: ActorRef[Mixer.Message] = mixerSystem

  @POST
  @Path("/api/transactions")
  @Tag(name = "Transactions", description = "Send Jobcoins from one address to another.")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.TEXT_PLAIN))
  @Operation(
    summary = "Send Jobcoins from one address to another",
    description = "Returns 200 Status code when execution was done successfully",
    requestBody = new RequestBody(
      content = Array(new Content(schema = new Schema(implementation = classOf[Transaction])))
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Ok"),
      new ApiResponse(responseCode = "400", description = "Bad request"),
      new ApiResponse(responseCode = "500", ref = "#/components/responses/TextError")))
  def jobcoinTransactionsPost(implicit ec: ExecutionContext): Route =
    ignoreTrailingSlash {
      (path("api" / "transactions") & post) {
        entity(as[Transaction]) { t =>
          val request = Mixer.Transfer(t.fromAddress, t.toAddress, t.amount, _)
          val resp: Future[Account.AcctResp] = (mixer ? request).mapTo[Account.AcctResp]

          complete(resp)
        }
      }
    }

  @GET
  @Path("/api/transactions")
  @Tag(name = "Transactions", description = "Get the list of all Jobcoin transactions.")
  @Produces(Array("application/json"))
  @Operation(
    summary = "Get the list of all Jobcoin transactions",
    description = "Returns 200 Status code when execution was done successfully",
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        content = Array(new Content(schema = new Schema(implementation = classOf[TransactionHistory])))
      ),
      new ApiResponse(responseCode = "404", description = "Not Found"),
      new ApiResponse(responseCode = "500", ref = "#/components/responses/TextError")))
  def jobcoinTransactionHistoryGet(implicit ec: ExecutionContext): Route =
    ignoreTrailingSlash {
      (path("api" / "transactions") & get) {
        val resp: Future[List[TransactionHistory]] = (mixer ? Mixer.AddressHistory).mapTo[List[TransactionHistory]]
        complete(resp)
      }
    }

  @GET
  @Path("/api/addresses")
  @Tag(name = "Addresses", description = "Get the balance and list of transactions for all addresses.")
  @Produces(Array("application/json"))
  @Operation(
    summary = "Get the balance and list of transactions for all addresses",
    description = "Returns 200 Status code when execution was done successfully",
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        content = Array(new Content(schema = new Schema(implementation = classOf[AddressInfo])))
      ),
      new ApiResponse(responseCode = "404", description = "Not Found"),
      new ApiResponse(responseCode = "500", ref = "#/components/responses/TextError")))
  def addressInfoGet(implicit ec: ExecutionContext): Route =
    ignoreTrailingSlash {
      path("api" / "addresses") {
        get {
          val resp: Future[List[Account.Balance]] = (mixer ? Mixer.AddressInfo).mapTo[List[Account.Balance]]

          complete(
            resp.map(_.map(b => AddressInfo(b.address, b.balance, b.transactions.toList)))
          )
        }
      }
    }

  @POST
  @Path("/api/create/jobcoins")
  @Tag(name = "Addresses", description = "Create New Jobcoins given a valid Address.")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.TEXT_PLAIN))
  @Operation(
    summary = "Create 50 New Jobcoins given a valid Address",
    description = "Returns 200 Status code when execution was done successfully",
    requestBody = new RequestBody(
      content = Array(new Content(schema = new Schema(implementation = classOf[NewTransaction])))
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Ok"),
      new ApiResponse(responseCode = "400", description = "Bad request"),
      new ApiResponse(responseCode = "500", ref = "#/components/responses/TextError")))
  def createNewJobcoinsPost(implicit ec: ExecutionContext): Route =
    ignoreTrailingSlash {
      path("api" / "create" / "jobcoins") {
        post {
          entity(as[NewTransaction]) { t =>
            val request = Mixer.Create(t.toAddress, t.amount, _)
            val resp: Future[Account.AcctResp] = (mixer ? request).mapTo[Account.AcctResp]

            complete(resp)
          }
        }
      }
    }

  def createRestRoutes(implicit ec: ExecutionContext): Route =
    concat(
      jobcoinTransactionsPost,
      jobcoinTransactionHistoryGet,
      addressInfoGet,
      createNewJobcoinsPost
    )

}
