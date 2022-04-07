package com.jobcoin.route

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.{ArraySchema, Content, MediaType, Schema}
import io.swagger.v3.oas.models.parameters.{Parameter, RequestBody}
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityScheme

import javax.ws.rs.core.{MediaType => JaxMediaType}
import scala.collection.immutable.ListSet
import scala.io.Source
import scala.jdk.CollectionConverters._

class SwaggerDocRoutes(implicit system: ActorSystem[_]) extends SwaggerHttpService {
  import SwaggerDocRoutes._

  def getRouteLocation(system: ActorSystem[_]): String = system.settings.config.getString("jobcoin.swagger.routeLocation")

  override val apiClasses: Set[Class[_]] = ListSet(classOf[JobcoinRoutes])
  override val schemes                   = List.empty
  override val host                      = s"${getRouteLocation(system)}" // the url of your api, not swagger's json endpoint
  override val info: Info                = Info(title = "Jobcoin DLT", version = "0.1", description = "Jobcoin REST Endpoints")
  override val unwantedDefinitions       = Seq("Function1", "Function1RequestContextFutureRouteResult")
  override def routes: Route =
    concat(
      super.routes,
      // redirect root page to /doc
      pathEndOrSingleSlash { redirect("/doc", StatusCodes.PermanentRedirect) },
      path("doc") {
        val html = Source
          .fromResource("swagger/index.html", getClass.getClassLoader)
          .getLines()
          .mkString("\n")
          .replace("https://petstore.swagger.io/v2/swagger.json", s"${getRouteLocation(system)}/api-docs/swagger.json")
        complete(HttpResponse(status = StatusCodes.OK, entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, html)))
      },
      pathPrefix("swagger") {
        getFromResourceDirectory("swagger")
      })

  override def components: Option[Components] =
    Some(
      /** Load common components for reuse in swagger annotations */
      new Components()
        .addRequestBodies("ArrayString", RequestBodyArrayString)
        .addRequestBodies("ArrayDate", RequestBodyArrayDate)
        .addResponses("JsonDone", ResponseJsonDone)
        .addResponses("TextDone", ResponseTextDone)
        .addResponses("JsonDate", ResponseJsonDate)
        .addResponses("Boolean", ResponseJsonBoolean)
        .addResponses("ArrayJsonString", ResponseJsonArrayString)
        .addResponses("ArrayJsonDate", ResponseJsonArrayDate)
        .addResponses("TextError", ResponseTextError)
        .addResponses("TextString", ResponseTextString)
        .addResponses("CsvString", ResponseCsvString)
        .addParameters("Date", QueryParameterCalendarDate)
        .addParameters("CreationTimestamp", QueryParameterCreationTimestamp)
        .addParameters("Ticker", QueryParameterTicker)
        .addParameters("CalibrationSetId", CalibrationSetId)
        .addParameters("RequiredCalibrationSetId", RequiredCalibrationSetId)
        .addParameters("RequiredTicker", QueryParameterRequiredTicker)
        .addParameters("StartDate", QueryParameterStartDate)
        .addParameters("EndDate", QueryParameterEndDate)
        .addParameters("Fingerprint", QueryParameterFingerprint)
        .addSecuritySchemes("basicAuth", BasicAuthSecurityScheme))
}

object SwaggerDocRoutes {
  // Define reusable swagger annotation components

  // native type schemas
  val SchemaString: Schema[_]    = new Schema().`type`("string")
  val SchemaCsvString: Schema[_] = new Schema().`type`("csv").example("col1, col2, col3")
  val SchemaBoolean: Schema[_]   = new Schema().`type`("boolean")
  val SchemaNumber: Schema[_]    = new Schema().`type`("number")
  val SchemaInteger: Schema[_]   = new Schema().`type`("integer")

  // custom type schemas
  val SchemaArrayString: Schema[_] =
    new ArraySchema().`type`("array").items(new Schema().`type`("string")).description("A list of string items")
  val SchemaArrayDate: Schema[_] =
    new ArraySchema().`type`("array").items(new Schema().`type`("string")).description("A list of dates (yyyyMMdd or yyyy-MM-dd)")

  val SchemaDone: Schema[_]         = new Schema().`type`("string").example("Done")
  val SchemaCalendarName: Schema[_] = new Schema().`type`("string").description("Calendar name")
  val SchemaCalendarDate: Schema[_] = new Schema().`type`("string").description("Calendar date")

  // contents
  val ContentJsonArrayString: Content =
    new Content().addMediaType(JaxMediaType.APPLICATION_JSON, new MediaType().schema(SchemaArrayString))
  val ContentTextArrayString: Content =
    new Content().addMediaType(JaxMediaType.TEXT_PLAIN, new MediaType().schema(SchemaArrayString))
  val ContentJsonArrayDate: Content =
    new Content().addMediaType(JaxMediaType.APPLICATION_JSON, new MediaType().schema(SchemaArrayDate))
  val ContentTextString: Content = new Content().addMediaType(JaxMediaType.TEXT_PLAIN, new MediaType().schema(SchemaString))
  val ContentCsvString: Content  = new Content().addMediaType("text/plain; charset=UTF-8", new MediaType().schema(SchemaCsvString))
  val ContentJsonDone: Content   = new Content().addMediaType(JaxMediaType.APPLICATION_JSON, new MediaType().schema(SchemaDone))
  val ContentTextDone: Content   = new Content().addMediaType(JaxMediaType.TEXT_PLAIN, new MediaType().schema(SchemaDone))
  val ContentJsonBoolean: Content =
    new Content().addMediaType(JaxMediaType.APPLICATION_JSON, new MediaType().schema(SchemaBoolean))
  val ContentJsonDate: Content =
    new Content().addMediaType(JaxMediaType.APPLICATION_JSON, new MediaType().schema(SchemaCalendarDate))

  // parameters
  val QueryParameterCalendarDate: Parameter =
    new Parameter().name("date").in("query").schema(SchemaArrayDate).required(true).description("Calendar date in MM-dd-yyyy")
  val QueryParameterCreationTimestamp: Parameter =
    new Parameter()
      .name("creationTimestamp")
      .in("query")
      .schema(SchemaArrayDate)
      .required(false)
      .description("Time stamp in MM-dd-yyyy")
  val CalibrationSetId: Parameter =
    new Parameter().name("calibrationSetId").in("query").schema(SchemaString).required(false).description("calibration set id")
  val RequiredCalibrationSetId: Parameter =
    new Parameter().name("calibrationSetId").in("query").schema(SchemaString).required(true).description("calibration set id")
  // requestBodies
  val RequestBodyArrayString: RequestBody =
    new RequestBody().description("a list of string payload items").required(true).content(ContentJsonArrayString)
  val RequestBodyArrayDate: RequestBody =
    new RequestBody().description("a list of yyyyMMdd or yyyy-MM-dd dates").required(true).content(ContentJsonArrayDate)
  val QueryParameterTicker: Parameter =
    new Parameter().name("ticker").in("query").schema(SchemaArrayString).required(false).description("ticker symbol")
  val QueryParameterStartDate: Parameter =
    new Parameter().name("startDate").in("query").schema(SchemaString).required(true).description("Calendar date in MM-dd-yyyy")
  val QueryParameterEndDate: Parameter =
    new Parameter().name("endDate").in("query").schema(SchemaString).required(true).description("Calendar date in MM-dd-yyyy")
  val QueryParameterRequiredTicker: Parameter =
    new Parameter().name("ticker").in("query").schema(SchemaArrayString).required(true).description("ticker symbol")
  val QueryParameterFingerprint: Parameter =
    new Parameter().name("fingerprint").in("query").schema(SchemaArrayString).required(true).description("fingerprint UUID")

  // responses
  val ResponseJsonDone: ApiResponse = new ApiResponse().content(ContentJsonDone).description("Returns Done")
  val ResponseTextDone: ApiResponse = new ApiResponse().content(ContentTextDone).description("Returns Done")
  val ResponseJsonArrayString: ApiResponse =
    new ApiResponse().content(ContentJsonArrayString).description("Returns a list of strings")
  val ResponseTextArrayString: ApiResponse =
    new ApiResponse().content(ContentTextArrayString).description("Returns a list of strings")
  val ResponseJsonArrayDate: ApiResponse = new ApiResponse().content(ContentJsonArrayDate).description("Returns a list of dates")
  val ResponseTextString: ApiResponse    = new ApiResponse().content(ContentTextString).description("Returns string result")
  val ResponseCsvString: ApiResponse     = new ApiResponse().content(ContentCsvString).description("Returns string result")
  val ResponseTextError: ApiResponse     = new ApiResponse().content(ContentTextString).description("Returns error messages")
  val ResponseJsonBoolean: ApiResponse   = new ApiResponse().content(ContentJsonBoolean).description("Returns true or false")
  val ResponseJsonDate: ApiResponse      = new ApiResponse().content(ContentJsonDate).description("Returns date")

  val BasicAuthSecurityScheme = new SecurityScheme().`type`(SecurityScheme.Type.HTTP).scheme("basic")
  val UnauthorizedResponse = new ApiResponse()
    .description("Authentication information is missing or invalid")
    .headers(Map("WWW_Authenticate" -> new Header().schema(new Schema().`type`("string"))).asJava)
}