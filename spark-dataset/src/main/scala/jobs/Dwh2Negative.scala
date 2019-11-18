package jobs
import com.typesafe.config.ConfigFactory
import net.gutefrage.etl.commons.conf.{DbConfig, IgnoreSparkMasterSysProp}
import net.gutefrage.etl.commons.util.Logging
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.broadcast
import net.gutefrage.etl.commons.conf.SparkConfOps.LoadFromConfig
import net.gutefrage.service.commons.mysql.jdbc.WeirdString
import net.gutefrage.data.commons.embeddings.CleanEmbeddings
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.sql.functions._

import scala.collection.mutable.ListBuffer
import scala.util.Properties
import org.apache.spark.sql.expressions.Window
import util.ExportHelper
import util.DatasetInfo

object Dwh2Negative extends IgnoreSparkMasterSysProp with Logging {
  val conf = new SparkConf()
    .withConfig(ConfigFactory.load(), "job.dwh2negative")

  val spark = SparkSession.builder
    .appName("dwh2negative")
    .config(conf)
    .getOrCreate()

  import spark.implicits._
  val config = ConfigFactory.load()

  val exportHelper = new ExportHelper(spark, config)

  val hdfsHost    = config.getString("hdfs.host")
  val exportFrom  = config.getString("job.dwh.mysql")
  val exportTo    = config.getString("job.dwh2negative.target")
  val buildNumber = Properties.envOrNone("BUILD_NUMBER").getOrElse("1-SNAPSHOT")

  val bytesPerPartition: Long  = 1024L * 1024 * 250 // MB (mind compression ration ~4:1)
  val bytesPerFetchBlock: Long = 1024L * 1024 * 2 // = initial task size
  val minPartitions            = 3

  lazy val statDbConfig = DbConfig("mysql.write")
  val jdbcDatabase      = config.getString("mysql.stat.database")
  val jdbcTable         = config.getString("mysql.stat.table")

  val hdfs = FileSystem.get(spark.sparkContext.hadoopConfiguration)
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

  def getWhitelist(in: String): Set[String] = {
    in.split(",")
      .map(_.trim)
      .map(_.toLowerCase)
      .toSet
  }

  def getExtraNegative(di: DatasetInfo): DataFrame = {

    val contentIdDwh = spark.read
      .parquet(exportFrom + "/svcpremoderation_question")
      .filter("resolve_status == 'Approved'")
      .select("question_id")

    val datasetSize = contentIdDwh.count()
    val contentDwh = spark.read
      .parquet(exportFrom + "/ask_question")
      .filter("is_deleted == 0")
      .select("id", "title", "body")
      .withColumnRenamed("id", "question_id")

    println(s"""
               |copying negative dataset:    ${di.datasetName}

               | dataset size:      ${datasetSize}

               | """.stripMargin)

    val df = contentDwh
      .join(broadcast(contentIdDwh), Seq("question_id"), "inner")
      .withColumn("decoded_title", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf($"title")))
      .withColumn("decoded_body", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf($"body")))
      .withColumn("label", lit("__label__legit"))
      .select("question_id", "label", "decoded_title", "decoded_body")
    df
  }

  //todo: refactor to object? or ...
  def exportDatasetContent(di: DatasetInfo): Unit = {
    //todo: add broadcast to speed up the query
    if (di.datasetName == "notdeleted") {
      val extraDf = getExtraNegative(di)
      spark.read.parquet(exportFrom + "/ask_question").createOrReplaceTempView("questionTable")
      val questionDF = spark.sql(
        "select id, stripped_title, title, body, created_at from questionTable where is_deleted = 0 and created_at > '2019-01-01'"
      )

      val resultQuestions = questionDF
        .withColumn("decoded_body", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf(col("body"))))
        .drop("body")
        .withColumn("decoded_title", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf(col("title"))))
        .drop("title")

      spark.read.parquet(exportFrom + "/ask_answer").createOrReplaceTempView("answerTable")
      val answerDF = spark.sql("select id, question_id, body from answerTable where is_deleted = 0")

      spark.read.parquet(exportFrom + "/ask_answer_rating").createOrReplaceTempView("answerRatingTable")
      val answerRatingDF = spark.sql("select question_id, answer_id, value from answerRatingTable")

      val answers = answerDF.drop("question_id").as("answers")
      val scores  = answerRatingDF.as("answer_scores")
      val answersWithScoresDF = answers.join(
        scores,
        col("answers.id") === col("answer_scores.answer_id"),
        "inner"
      )

      val answersWithScoreBetterThan07 =
        answersWithScoresDF.filter("value >= 0.7")

      val questionsWithAnswers =
        resultQuestions.join(answersWithScoreBetterThan07.drop("id"), col("id") === col("question_id"), "inner")

      spark.read.parquet(exportFrom + "/ask_tag").createOrReplaceTempView("tagTable")
      val tagDF = spark.sql("select id as tag_id, normalized_tag from tagTable")

      spark.read.parquet(exportFrom + "/ask_question_tag").createOrReplaceTempView("questionTagTable")
      val questionTagDF = spark.sql("select tag_id, question_id from questionTagTable")
      val tags =
        questionTagDF.as("qt").join(tagDF.as("t"), col("qt.tag_id") === col("t.tag_id"), "inner").drop("tag_id")

      import org.apache.spark.sql.functions._

      val stopTags = List(
        "sex",
        "aerger",
        "hass",
        "liebe",
        "beziehung",
        "freundschaft",
        "familie",
        "party",
        "drogen",
        "schwul",
        "gefuehle",
        "depression",
        "gesundheit",
        "medizin",
        "penis",
        "vagina",
        "scheide",
        "religion",
        "schwuchtel",
        "fotze",
        "muschi",
        "wixen",
        "schwanzvergleich",
        "schwanz",
        "politik",
        "fluechtlinge",
        "geschlechtsverkehr",
        "homosexualitaet",
        "liebe-und-beziehung",
        "erotik",
        "erotikfilm",
        "sexfilme",
        "porno",
        "bdsm",
        "sklaven",
        "gesundheit-und-medizin"
      )

      val tagsForQuestion =
        tags
          .filter(not($"normalized_tag".isin(stopTags: _*)))
          .groupBy("question_id")
          .agg(collect_list("normalized_tag"))

      val result = questionsWithAnswers
        .as("q")
        .join(tagsForQuestion.as("t"), col("q.question_id") === col("t.question_id"), "inner")
      val w = Window.partitionBy($"id").orderBy(desc("value"))

      // Filter
      val filteredResult = result.withColumn("rank", rank.over(w)).where($"rank" <= 3)
      val alexaDf = filteredResult
        .withColumn("label", lit("__label__legit"))
        .select("label", "id", "decoded_title", "decoded_body")
        .withColumnRenamed("id", "question_id")
        .distinct

      val df = alexaDf.union(extraDf).distinct

      val datasetSize = df.count()

      println(s"""
                 | dataset size:      ${datasetSize}
                 | """.stripMargin)

      val timeA = System.currentTimeMillis()

      val basePath = (exportTo + "qc-"
        + di.datasetName.replaceAll("[\\s\\-()]", "")
        + "/" + buildNumber)

      exportHelper.datasetWriter(di, df, basePath, datasetSize)

      val timeB = System.currentTimeMillis()
      println("\n" + di + " duration: " + ((timeB - timeA) / 1000) + "s")

    }

  }

  def getDatasetInfo(dataset: String): List[DatasetInfo] = {

    val datasets = ListBuffer[DatasetInfo]()
    datasets += DatasetInfo(
      dataset.split('.').head,
      dataset.split('.').last
    )
    datasets.toList
  }

  def main(args: Array[String]) {
    // avoid NPE when writing parquet metadata
    spark.sparkContext.hadoopConfiguration.setBoolean("parquet.enable.summary-metadata", false)
    config.getString("job.dwh2negative.export.only") match {
      // or just some tables
      case reasonString: String => {
        val datasets = getWhitelist(reasonString) // all reasons as positive that should be imported
        // todo: change reasons to question.deletionreason.contact-request but not question.contact-request only
        datasets.foreach(datasets => {
          getDatasetInfo(datasets)
            .filter(di => datasets.contains(di.content + "." + di.datasetName.toLowerCase))
            .foreach(di => exportDatasetContent(di))
        })
      }
      //todo: import all datasets
    }
    spark.stop()
  }
}
