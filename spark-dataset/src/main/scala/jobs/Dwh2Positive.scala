package jobs
import com.typesafe.config.ConfigFactory
import buildInfo.BuildInfo
import net.gutefrage.data.commons.embeddings.CleanEmbeddings
import net.gutefrage.etl.commons.conf.SparkConfOps.LoadFromConfig
import net.gutefrage.etl.commons.conf.IgnoreSparkMasterSysProp
import net.gutefrage.etl.commons.util.Logging
import net.gutefrage.service.commons.mysql.jdbc.WeirdString
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.SparkConf
import org.apache.spark.sql.functions.{broadcast, _}
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.collection.mutable.ListBuffer
import util.{DatasetInfo, ExportHelper}

object Dwh2Positive extends IgnoreSparkMasterSysProp with Logging {
  val conf = new SparkConf()
    .withConfig(ConfigFactory.load(), "job.dwh2positive")

  val spark = SparkSession.builder
    .appName("dwh2positive")
    .config(conf)
    .getOrCreate()

  import spark.implicits._
  val config = ConfigFactory.load()

  val exportHelper = new ExportHelper(spark, config)

  val hdfsHost    = config.getString("hdfs.host")
  val exportFrom  = config.getString("job.dwh.mysql")
  val exportTo    = config.getString("job.dwh2positive.target")
  val buildNumber = BuildInfo.version

  val bytesPerPartition: Long  = 1024L * 1024 * 250 // MB (mind compression ration ~4:1)
  val bytesPerFetchBlock: Long = 1024L * 1024 * 2 // = initial task size
  val minPartitions            = 3
  val hdfs                     = FileSystem.get(spark.sparkContext.hadoopConfiguration)
  val weirdStringFromDb: String => String = (str: String) => {
    try {
      WeirdString.fromDbString(str).toString
    } catch {
      case e: Throwable => ""
    }
  }
  val weirdStringFromDbUdf = udf(weirdStringFromDb)

  val cleanTextForEmbeddings: String => String = { body =>
    Option(body) match {
      case None => ""
      case Some(b) =>
        CleanEmbeddings.cleanAll(b)
    }
  }
  val cleanTextForEmbeddingsUdf = udf(cleanTextForEmbeddings)

  /**
    * index running classifier to collect extra negative dataset
    */
  val datasetToRule = Map(
    "question.contact-request" -> "ContactRequestQuestion"
  )

  def getWhitelist(in: String): Set[String] = {
    in.split(",")
      .map(_.trim)
      .map(_.toLowerCase)
      .toSet
  }

  def getDatasetInfo(content: String): List[DatasetInfo] = {
    val contentReasons = spark.read
      .parquet(exportFrom + "/svc" + content + "_deletion_reason")
      .select("reason")
      .distinct()
      .map(row => row.mkString(""))
      .collect()

    val datasets = ListBuffer[DatasetInfo]()
    for (dataset <- contentReasons) {
      datasets += DatasetInfo(
        content,
        dataset
      )
    }
    datasets.toList
  }

  def exportDatasetContent(di: DatasetInfo): Unit = {

    if (di.content == "question") {
      val contentIdDwh = spark.read
        .parquet(exportFrom + "/svcquestion_deletion_reason")
        .filter(s"reason == '${di.datasetName}'")
        .select("question_id")

      val datasetSize = contentIdDwh.count()

      println(s"""
                 |copying positive dataset:    ${di.datasetName}
                 | dataset size:      ${datasetSize}
                 | """.stripMargin)

      spark.read.parquet(exportFrom + "/ask_question").createOrReplaceTempView("questionTable")
      val contentDwh = spark.sql("select id as question_id, title, body from questionTable")

      val timeA = System.currentTimeMillis()
      val df = contentDwh
        .join(broadcast(contentIdDwh), Seq("question_id"), "inner")
        .withColumn("decoded_title", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf($"title")))
        .withColumn("decoded_body", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf($"body")))
        .withColumn("label", lit("__label__" + di.datasetName))
        .select("question_id", "label", "decoded_title", "decoded_body")

      val basePath = (exportTo + "qc-deletionreason-"
        + di.datasetName.replaceAll("[\\s\\-()]", "") + "-ds"
        + "/" + buildNumber)

      exportHelper.datasetWriter(di, df, basePath, datasetSize)

      val timeB = System.currentTimeMillis()
      println("\n" + di + " duration: " + ((timeB - timeA) / 1000) + "s")
    }
  }

  def main(args: Array[String]) {
    // avoid NPE when writing parquet metadata
    spark.sparkContext.hadoopConfiguration.setBoolean("parquet.enable.summary-metadata", false)
    config.getString("job.dwh2positive.export.only") match {
      // or just some tables
      case reasonString: String => {
        val datasets = getWhitelist(reasonString) // all reasons as positive that should be imported
        // todo: change reasons to question.deletionreason.contact-request but not question.contact-request only
        val contents = datasets.map(_.split('.').head)
        contents.foreach(content => {
          getDatasetInfo(content)
            .filter(di => datasets.contains(di.content + "." + di.datasetName.toLowerCase))
            .foreach(di => exportDatasetContent(di))
        })
      }
      //todo: import all datasets
    }
    spark.stop()
  }
}
