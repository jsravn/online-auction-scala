lazy val root = (project in file("."))
  .settings(name := "online-auction-scala")
  .aggregate(itemApi, itemImpl,
    biddingApi, biddingImpl,
    userApi, userImpl,
    searchApi, searchImpl,
    webGateway)
  .settings(commonSettings: _*)

organization in ThisBuild := "com.example"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.12.4"

version in ThisBuild := "1.0.0-SNAPSHOT"

// enable cinnamon
//lazy val app = project in file(".") enablePlugins (Cinnamon)

val playJsonDerivedCodecs = "org.julienrf" %% "play-json-derived-codecs" % "4.0.0"
val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"

// Add to project to enable cinnamon and prometheus scraping.
lazy val cinnamonSettings = Seq(
  libraryDependencies ++= Seq(
    Cinnamon.library.cinnamonCHMetrics3,
    Cinnamon.library.cinnamonAkka,
    Cinnamon.library.cinnamonAkkaHttp,
    Cinnamon.library.cinnamonJvmMetricsProducer,
    Cinnamon.library.cinnamonPlay,
    Cinnamon.library.cinnamonLagom,
    Cinnamon.library.cinnamonPrometheus,
    Cinnamon.library.cinnamonPrometheusHttpServer
  ),
  endpoints += TcpEndpoint("metrics", 9091, None),
  annotations := Map(
    // enable scraping
    "prometheus.io/scrape" -> "true"
  )
)

lazy val security = (project in file("security"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslServer % Optional,
      playJsonDerivedCodecs,
      scalaTest
    )
  )

lazy val itemApi = (project in file("item-api"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      playJsonDerivedCodecs
    )
  )
  .dependsOn(security)

lazy val itemImpl = (project in file("item-impl"))
  .settings(commonSettings: _*)
  .enablePlugins(LagomScala, SbtReactiveAppPlugin, Cinnamon)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslTestKit,
      lagomScaladslKafkaBroker,
      "com.datastax.cassandra" % "cassandra-driver-extras" % "3.0.0",
      macwire,
      scalaTest
    ),
    cinnamonSettings
  )
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(itemApi, biddingApi)

lazy val biddingApi = (project in file("bidding-api"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      playJsonDerivedCodecs
    )
  )
  .dependsOn(security)

lazy val biddingImpl = (project in file("bidding-impl"))
  .settings(commonSettings: _*)
  .enablePlugins(LagomScala, SbtReactiveAppPlugin, Cinnamon)
  .dependsOn(biddingApi, itemApi)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslTestKit,
      lagomScaladslKafkaBroker,
      macwire,
      scalaTest
    ),
    maxErrors := 10000,
    cinnamonSettings

  )

lazy val searchApi = (project in file("search-api"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      playJsonDerivedCodecs
    )
  )
  .dependsOn(security)

lazy val searchImpl = (project in file("search-impl"))
  .settings(commonSettings: _*)
  .enablePlugins(LagomScala, SbtReactiveAppPlugin, Cinnamon)
  .dependsOn(searchApi, itemApi, biddingApi)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaClient,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    ),
    cinnamonSettings
  )

lazy val transactionApi = (project in file("transaction-api"))
  .settings(commonSettings: _*)
  .dependsOn(itemApi)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      playJsonDerivedCodecs
    ),
    EclipseKeys.skipProject := true
  )
  .dependsOn(security)

lazy val transactionImpl = (project in file("transaction-impl"))
  .settings(commonSettings: _*)
  // .enablePlugins(LagomScala)
  .dependsOn(transactionApi, biddingApi)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    ),
    EclipseKeys.skipProject := true
  )

lazy val userApi = (project in file("user-api"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      playJsonDerivedCodecs
    )
  )
  .dependsOn(security)

lazy val userImpl = (project in file("user-impl"))
  .settings(commonSettings: _*)
  .enablePlugins(LagomScala, SbtReactiveAppPlugin, Cinnamon)
  .dependsOn(userApi)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      macwire,
      scalaTest
    ),
    cinnamonSettings
  )
  .dependsOn(itemApi)

lazy val webGateway = (project in file("web-gateway"))
  .settings(commonSettings: _*)
  .enablePlugins(PlayScala, LagomPlay, SbtReactiveAppPlugin)
  .dependsOn(biddingApi, itemApi, userApi)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslServer,
      macwire,
      scalaTest,

      "org.ocpsoft.prettytime" % "prettytime" % "3.2.7.Final",

      "org.webjars" % "foundation" % "6.2.3",
      "org.webjars" % "foundation-icon-fonts" % "d596a3cfb3"
    ),
    EclipseKeys.preTasks := Seq(compile in Compile),
    httpIngressPaths := Seq("/")
  )

def commonSettings: Seq[Setting[_]] = Seq(
)

lagomCassandraCleanOnStart in ThisBuild := false

// ------------------------------------------------------------------------------------------------

// register 'elastic-search' as an unmanaged service on the service locator so that at 'runAll' our code
// will resolve 'elastic-search' and use it. See also com.example.com.ElasticSearch
lagomUnmanagedServices in ThisBuild += ("elastic-search" -> "http://127.0.0.1:9200")
