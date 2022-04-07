name := "jobcoin-scala"

version := "0.1"

scalaVersion := "2.13.5"

trapExit := false

libraryDependencies ++= Seq(
  "com.typesafe.akka"            %% "akka-actor-typed"           % "2.6.14",
  "com.typesafe.akka"            %% "akka-stream-typed"          % "2.6.14",
  "com.typesafe.akka"            %% "akka-persistence-typed"     % "2.6.14",
  "com.typesafe.akka"            %% "akka-serialization-jackson" % "2.6.14",
  "com.typesafe.akka"            %% "akka-slf4j"                 % "2.6.14",
  "com.typesafe.akka"            %% "akka-http"                  % "10.2.4",
  "com.typesafe.akka"            %% "akka-parsing"               % "10.2.4",   // TODO - fix akka-parsing logic?
  "com.typesafe.akka"            %% "akka-http2-support"         % "10.2.4",
  "com.typesafe.akka"            %% "akka-http-core"             % "10.2.4",

  "org.fusesource.leveldbjni"     % "leveldbjni-all"             % "1.8",

  "io.circe"                     %% "circe-core"                 % "0.13.0",
  "io.circe"                     %% "circe-generic"              % "0.13.0",
  "io.circe"                     %% "circe-parser"               % "0.13.0",
  "de.heikoseeberger"            %% "akka-http-circe"            % "1.35.3",

  // Swagger
  "com.github.swagger-akka-http" %% "swagger-akka-http"          % "2.4.0",
  ("javax.ws.rs"                  % "javax.ws.rs-api"   % "2.1.1").artifacts(Artifact("javax.ws.rs-api", "jar", "jar")),
  "com.sun.jersey.contribs"       % "jersey-multipart"           % "1.19.4",

  // Test
  "com.typesafe.akka"            %% "akka-testkit"               % "2.6.14" % Test,
  "com.typesafe.akka"            %% "akka-http-testkit"          % "10.2.4" % Test
)
