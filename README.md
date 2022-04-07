# Jobcoin-Scala
Simple project for a "Jobcoin Digital Ledger / DLT-based" POC using Scala 2.13.5, SBT (1.5.3), Typed Akka Actors (2.6.14), and Akka HTTP (10.2.4).

I used "swagger-akka-http" (version 2.4.0) to generate the Swagger AKKA HTTP endpoints (as seen in the JobcoinRoutes Scala class).

### Run
1) `sbt run`

2) In any Web Browser, type in:
**http://0.0.0.0:8080/doc**

### Swagger
After `sbt run`, going to **http://0.0.0.0:8080/doc** will direct you to a Swagger-UI Page.

As of now, this Swagger Page can only be used for POST Requestsn (the GET Requests on Swagger is not working). But from Swagger, you can retrieve the cUrl command in order to use the GET Requests on your Local Command Line application.

### TODOs:
1) Fix some of the GET Swagger calls.
2) Add both Scala and Akka Unit Tests.
3) Switch from LevelDB to Apache Cassandra? Mainly for better Akka Persistence performance.
