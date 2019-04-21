package org.globalforestwatch.treecoverloss

import com.monovore.decline.{CommandApp, Opts}
import org.apache.log4j.Logger
import org.apache.spark._
import org.apache.spark.rdd._
import org.apache.spark.sql._
import cats.implicits._
import geotrellis.vector.io.wkb.WKB
import geotrellis.vector.{Feature, Geometry}
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

object TreeLossSummaryMain
  extends CommandApp(
    name = "geotrellis-tree-cover-loss",
    header = "Compute statistics on tree cover loss",
    main = {

      val featuresOpt =
        Opts.options[String]("features", "URI of features in TSV format")

      val outputOpt =
        Opts.option[String]("output", "URI of output dir for CSV files")

      // Can be used to increase the level of job parallelism
      val intputPartitionsOpt = Opts
        .option[Int]("input-partitions", "Partition multiplier for input")
        .withDefault(16)

      // Can be used to consolidate output into fewer files
      val outputPartitionsOpt = Opts
        .option[Int](
        "output-partitions",
        "Number of output partitions / files to be written"
      )
        .orNone

      val limitOpt = Opts
        .option[Int]("limit", help = "Limit number of records processed")
        .orNone

      val isoOpt =
        Opts.option[String]("iso", help = "Filter by country ISO code").orNone

      val admin1Opt = Opts
        .option[String]("admin1", help = "Filter by country Admin1 code")
        .orNone

      val admin2Opt = Opts
        .option[String]("admin2", help = "Filter by country Admin2 code")
        .orNone

      val logger = Logger.getLogger("TreeLossSummaryMain")

      (
        featuresOpt,
        outputOpt,
        intputPartitionsOpt,
        outputPartitionsOpt,
        limitOpt,
        isoOpt,
        admin1Opt,
        admin2Opt
      ).mapN {
        (featureUris,
         outputUrl,
         inputPartitionMultiplier,
         maybeOutputPartitions,
         limit,
         iso,
         admin1,
         admin2) =>
          val spark: SparkSession = TreeLossSparkSession.spark
          import spark.implicits._

          // ref: https://github.com/databricks/spark-csv
          var featuresDF: DataFrame = spark.read
            .options(Map("header" -> "true", "delimiter" -> "\t"))
            .csv(featureUris.toList: _*)

          iso.foreach { isoCode =>
            featuresDF = featuresDF.filter($"gid_0" === isoCode)
          }

          admin1.foreach { admin1Code =>
            featuresDF = featuresDF.filter($"gid_1" === admin1Code)
          }

          admin2.foreach { admin2Code =>
            featuresDF = featuresDF.filter($"gid_2" === admin2Code)
          }

          limit.foreach { n =>
            featuresDF = featuresDF.limit(n)
          }

          /* Transition from DataFrame to RDD in order to work with GeoTrellis features */
          val featureRDD: RDD[Feature[Geometry, FeatureId]] =
            featuresDF.rdd.map { row: Row =>
              val countryCode: String = row.getString(2)
              val admin1: String = row.getString(3)
              val admin2: String = row.getString(4)
              val geom: Geometry = WKB.read(row.getString(5))
              Feature(geom, FeatureId(countryCode, admin1, admin2))
            }

          val part = new HashPartitioner(
            partitions = featureRDD.getNumPartitions * inputPartitionMultiplier
          )

          val summaryRDD: RDD[(FeatureId, TreeLossSummary)] =
            TreeLossRDD(featureRDD, TenByTenGrid.blockTileGrid, part)

          val summaryDF =
            summaryRDD
              .flatMap {
                case (id, treeLossSummary) =>
                  treeLossSummary.stats.map {
                    case (lossDataGroup, lossData) => {

                      val admin1 =
                        if (id.admin1.length > 0)
                          id.admin1.split("[.]")(1).split("[_]")(0)
                        else ""
                      val admin2 =
                        if (id.admin2.length > 0)
                          id.admin2.split("[.]")(2).split("[_]")(0)
                        else ""

                      LossRow(
                        LossRowFeatureId(id.country, admin1, admin2),
                        lossDataGroup.tcd2000,
                        lossDataGroup.tcd2010,
                        LossRowLayers(
                          lossDataGroup.drivers,
                          lossDataGroup.globalLandCover,
                          lossDataGroup.primaryForest,
                          lossDataGroup.idnPrimaryForest,
                          lossDataGroup.erosion,
                          lossDataGroup.biodiversitySignificance,
                          lossDataGroup.biodiversityIntactness,
                          lossDataGroup.wdpa,
                          lossDataGroup.aze,
                          lossDataGroup.plantations,
                          lossDataGroup.riverBasins,
                          lossDataGroup.ecozones,
                          lossDataGroup.urbanWatersheds,
                          lossDataGroup.mangroves1996,
                          lossDataGroup.mangroves2016,
                          lossDataGroup.waterStress,
                          lossDataGroup.intactForestLandscapes,
                          lossDataGroup.endemicBirdAreas,
                          lossDataGroup.tigerLandscapes,
                          lossDataGroup.landmark,
                          lossDataGroup.landRights,
                          lossDataGroup.keyBiodiversityAreas,
                          lossDataGroup.mining,
                          lossDataGroup.rspo,
                          lossDataGroup.peatlands,
                          lossDataGroup.oilPalm,
                          lossDataGroup.idnForestMoratorium,
                          lossDataGroup.idnLandCover,
                          lossDataGroup.mexProtectedAreas,
                          lossDataGroup.mexPaymentForEcosystemServices,
                          lossDataGroup.mexForestZoning,
                          lossDataGroup.perProductionForest,
                          lossDataGroup.perProtectedAreas,
                          lossDataGroup.perForestConcessions,
                          lossDataGroup.braBiomes,
                          lossDataGroup.woodFiber,
                          lossDataGroup.resourceRights,
                          lossDataGroup.logging,
                          lossDataGroup.oilGas
                        ),
                        lossData.totalArea,
                        lossData.totalGainArea,
                        lossData.totalBiomass,
                        lossData.totalCo2,
                        lossData.biomassHistogram.mean(),
                        lossData.totalMangroveBiomass,
                        lossData.totalMangroveCo2,
                        lossData.mangroveBiomassHistogram.mean(),
                        LossYearDataMap.toList(lossData.lossYear)
                      )
                    }
                  }
              }
              .toDF(
                "feature_id",
                "threshold_2000",
                "threshold_2010",
                "layers",
                "area",
                "gain",
                "biomass",
                "co2",
                "biomass_per_ha",
                "mangrove_biomass",
                "mangrove_co2",
                "mangrove_biomass_per_ha",
                "year_data"
              )

          val runOutputUrl = outputUrl + "/treecoverloss_" + DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmm")
            .format(LocalDateTime.now)

          //          val outputPartitionCount =
          //            maybeOutputPartitions.getOrElse(featureRDD.getNumPartitions)

          summaryDF.repartition($"feature_id", $"threshold_2000")
          summaryDF.cache()

          val masterDF = summaryDF.transform(MasterDF.expandByThreshold)

          masterDF.cache()

          val extent2010DF = summaryDF
            .transform(Extent2010DF.sumArea)
            .transform(Extent2010DF.joinMaster(masterDF))
            .transform(Extent2010DF.aggregateByThreshold)

          val annualLossDF = summaryDF
            .transform(AnnualLossDF.unpackYearData)
            .transform(AnnualLossDF.sumArea)
            .transform(AnnualLossDF.joinMaster(masterDF))
            .transform(AnnualLossDF.aggregateByThreshold)

          val adm2DF = annualLossDF
            .transform(Adm2DF.joinExtent2010(extent2010DF))
            .transform(Adm2DF.unpackFeautureIDLayers)

          adm2DF.repartition($"iso")
          adm2DF.cache()

          val csvOptions = Map(
            "header" -> "true",
            "delimiter" -> "\t",
            "quote" -> "\u0000",
            "quoteMode" -> "NONE",
            "nullValue" -> "\u0000"
          )

          val adm2SummaryDF = adm2DF
            .filter($"threshold" > 0)
            .transform(Adm2SummaryDF.sumArea)

          adm2SummaryDF
            .repartition($"iso")
            .orderBy($"iso", $"adm1", $"adm2", $"threshold")
            .write
            .options(csvOptions)
            .csv(path = runOutputUrl + "/summary/adm2")

          val adm1SummaryDF = adm2SummaryDF.transform(Adm1SummaryDF.sumArea)

          adm1SummaryDF
            .repartition($"iso")
            .orderBy($"iso", $"adm1", $"threshold")
            .write
            .options(csvOptions)
            .csv(path = runOutputUrl + "/summary/adm1")

          val isoSummaryDF = adm1SummaryDF.transform(IsoSummaryDF.sumArea)

          isoSummaryDF
            .repartition($"iso")
            .orderBy($"iso", $"threshold")
            .write
            .options(csvOptions)
            .csv(path = runOutputUrl + "/summary/iso")

          adm1SummaryDF
            .coalesce(1)
            .orderBy($"iso", $"adm1", $"threshold")
            .write
            .options(csvOptions)
            .csv(path = runOutputUrl + "/summary/global/adm1")

          isoSummaryDF
            .coalesce(1)
            .orderBy($"iso", $"threshold")
            .write
            .options(csvOptions)
            .csv(path = runOutputUrl + "/summary/global/iso")

          val adm2ApiDF = adm2DF
            .transform(Adm2ApiDF.nestYearData)

          adm2ApiDF
            .repartition($"iso", $"adm1", $"adm2", $"threshold")
            .orderBy($"iso", $"adm1", $"adm2", $"threshold")
            .toJSON
            .mapPartitions(vals => Iterator("[" + vals.mkString(",") + "]"))
            .write
            .text(runOutputUrl + "/api/adm2")

          val adm1ApiDF = adm2DF
            .transform(Adm1ApiDF.sumArea)
            .transform(Adm1ApiDF.nestYearData)

          adm1ApiDF
            .repartition($"iso", $"adm1", $"threshold")
            .orderBy($"iso", $"adm1", $"threshold")
            .toJSON
            .mapPartitions(vals => Iterator("[" + vals.mkString(",") + "]"))
            .write
            .text(runOutputUrl + "/api/adm1")

          val isoApiDF = adm2DF
            .transform(IsoApiDF.sumArea)
            .transform(IsoApiDF.nestYearData)

          isoApiDF
            .repartition($"iso", $"threshold")
            .orderBy($"iso", $"threshold")
            .toJSON
            .mapPartitions(vals => Iterator("[" + vals.mkString(",") + "]"))
            .write
            .text(runOutputUrl + "/api/iso")

          spark.stop
      }
    }
  )
