import com.typesafe.config.ConfigFactory
import net.gutefrage.etl.commons.conf.IgnoreSparkMasterSysProp
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

object Dwh2Positive extends IgnoreSparkMasterSysProp with Logging {
  val conf = new SparkConf()
    .withConfig(ConfigFactory.load(), "job.dwh2positive")

  val spark = SparkSession.builder
    .appName("dwh2positive")
    .config(conf)
    .getOrCreate()

  import spark.implicits._
  val config = ConfigFactory.load()

  val hdfsHost                 = config.getString("hdfs.host")
  val exportFrom                   = config.getString("job.dwh.mysql")
  val exportTo                   = config.getString("job.dwh2positive.target")
  val buildNumber               = Properties.envOrNone("BUILD_NUMBER").getOrElse("1-SNAPSHOT")

  val bytesPerPartition: Long  = 1024L * 1024 * 250 // MB (mind compression ration ~4:1)
  val bytesPerFetchBlock: Long = 1024L * 1024 * 2 // = initial task size
  val minPartitions            = 3
  val hdfs = FileSystem.get(spark.sparkContext.hadoopConfiguration)
  val weirdStringFromDb: String=>String=(str:String)=>{
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

  //todo: refactor to object? or ...
  def getContentDwh(content: String): DataFrame ={
    val contentDwh =  spark.read
      .parquet(exportFrom + "/svc" + content + "_deletion_reason")
    contentDwh
  }

  def getDatasetInfo(content: String): List[DatasetInfo] ={
    val contentDwh = getContentDwh(content)
    val contentReasons = contentDwh
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

  def getExtraNegative(di: DatasetInfo): Unit = {
    val datasetName = di.content+'.'+di.datasetName
    val content_id = di.content+"_id"
    if (datasetToRule.contains(datasetName)) {
      val rule = datasetToRule.get(datasetName)
      val premodIdDwh = spark.read
        .parquet(exportFrom + "/svcpremoderation_" + di.content + "_reasons")
        .filter(s"rule_type == '${rule.get}'")
        .select(content_id)

      val acceptIdDwh = spark.read
        .parquet(exportFrom + "/svcpremoderation_" + di.content)
        .filter("resolve_status == 'Approved'")
        .select(content_id)

      val contentIdDwh = premodIdDwh.join(acceptIdDwh, Seq(content_id), "inner").select(content_id)

      val datasetSize = contentIdDwh.count()
      val contentDwh = spark.read.parquet(exportFrom + "/ask_" + di.content).withColumnRenamed("id", content_id)

      println(
        s"""
           |copying negative dataset:    ${di.datasetName}

           | dataset size:      ${datasetSize}

           | """.stripMargin)

      val timeA = System.currentTimeMillis()
      val df = contentDwh
        .join(broadcast(contentIdDwh), Seq(content_id), "inner")
        .withColumn("decoded_title", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf($"title")))
        .withColumn("decoded_body", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf($"body")))
        .withColumn("label", lit("__label__legit"))
        .select(content_id,"label", "decoded_title", "decoded_body")

      println(s"""size of negative: ${df.count()} """)


      val basePath = (exportTo + di.content.substring(0,1)
        + "c-deletionreason-"
        + di.datasetName.replaceAll("[\\s\\-()]", "")
        + "/"+ buildNumber )

      val ivyClassifier = "negative"

      parquetWriter(df, basePath, ivyClassifier, datasetSize)

      val timeB = System.currentTimeMillis()
      println("\n" + di + " duration: " + ((timeB - timeA) / 1000) + "s")
    }
  }

  def exportDatasetContent(di: DatasetInfo): Unit ={

    getExtraNegative(di)
    val content_id = di.content + "_id"
    val contentIdDwh = getContentDwh(di.content)
      .filter(s"reason == '${di.datasetName}'")
      .select(content_id)

    val datasetSize              = contentIdDwh.count()
    val contentDwh = spark.read.parquet(exportFrom + "/ask_" + di.content).withColumnRenamed("id", content_id)

    println(s"""
               |copying positive dataset:    ${di.datasetName}
               | dataset size:      ${datasetSize}
               | """.stripMargin)

    val timeA = System.currentTimeMillis()
    val df = contentDwh
      .join(broadcast(contentIdDwh), Seq(di.content+"_id"), "inner")
      .withColumn("decoded_title", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf($"title")))
      .withColumn("decoded_body", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf($"body")))
      .withColumn("label", lit("__label__"+di.datasetName))
      .select(di.content+"_id","label", "decoded_title", "decoded_body")

    val basePath = (exportTo + di.content.substring(0,1)
      + "c-deletionreason-"
      + di.datasetName.replaceAll("[\\s\\-()]", "")
      + "/"+ buildNumber )

    val ivyClassifier = "positive"

    parquetWriter(df, basePath, ivyClassifier, datasetSize)

    val timeB = System.currentTimeMillis()
    println("\n" + di + " duration: " + ((timeB - timeA) / 1000) + "s")
  }

  def parquetWriter(df: DataFrame, basePath: String , classifier: String, datasetSize: Long): Unit ={
    // ivy-repo
    val base = new Path(basePath)
    val destDir = classifier + "/parquet"
    val tmpDir = classifier + "/parquet_tmp_dir"
    val delDir = classifier + "/to_delete"

    val tmpOutputDir = new Path(base, tmpDir)
    val toDeleteDir = new Path(base, delDir)
    val destOutputDir = new Path(base, destDir)

    df.coalesce(1)
      .write
      .mode("overwrite")
      .parquet(tmpOutputDir.toString)

    val tempParquet = spark.read.parquet(tmpOutputDir.toString)

    // varify dataset
    if( tempParquet.count() <= datasetSize) {
      println(s"""
                 | The dataset size is verified, exporting ...
                 |  Copy from file '${tmpOutputDir.toString}' to '${destOutputDir.toString}'
                 """.stripMargin)
      if (hdfs.exists(destOutputDir)) {
        hdfs.rename(destOutputDir, toDeleteDir)
      }
      hdfs.rename(tmpOutputDir, destOutputDir)
    } else {
      println(s"""
                 | The dataset size is abnomaly large, stoping ...
                 """.stripMargin)
      hdfs.rename(tmpOutputDir, toDeleteDir)
    }
    hdfs.delete(toDeleteDir, true)

  }

  def main(args: Array[String]) {
    // avoid NPE when writing parquet metadata
    spark.sparkContext.hadoopConfiguration.setBoolean("parquet.enable.summary-metadata", false)
    config.getString("job.dwh2positive.export.only") match{
      // or just some tables
      case reasonString: String => {
        val datasets  = getWhitelist(reasonString)     // all reasons as positive that should be imported
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

case class DatasetInfo(content: String, datasetName: String) {
  override def toString: String = content + "." + datasetName
}
