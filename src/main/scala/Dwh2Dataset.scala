import com.typesafe.config.ConfigFactory
import net.gutefrage.etl.commons.conf.IgnoreSparkMasterSysProp
import net.gutefrage.etl.commons.util.Logging
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import net.gutefrage.etl.commons.conf.SparkConfOps.LoadFromConfig
import net.gutefrage.service.commons.mysql.jdbc.WeirdString
import net.gutefrage.data.commons.embeddings.CleanEmbeddings
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.sql.functions._
import scala.collection.mutable.ListBuffer
import scala.util.Properties
import net.gutefrage.etl.commons.hdfs.ParquetWriter.ParquetWriterConfig

object Dwh2Dataset extends IgnoreSparkMasterSysProp with Logging {
  val conf = new SparkConf()
    .withConfig(ConfigFactory.load(), "job.dwh2dataset")

  val spark = SparkSession.builder
    .appName("dwh2dataset")
    .config(conf)
    .getOrCreate()

  import spark.implicits._
  val config = ConfigFactory.load()

  val hdfsHost                 = config.getString("hdfs.host")
  val exportFrom                   = config.getString("job.dwh.mysql")
  val exportTo                   = config.getString("job.dwh2dataset.target")
  val buildNumber               = Properties.envOrNone("BUILD_NUMBER").getOrElse("1-SNAPSHOT")

  val bytesPerPartition: Long  = 1024L * 1024 * 250 // MB (mind compression ration ~4:1)
  val bytesPerFetchBlock: Long = 1024L * 1024 * 2 // = initial task size
  val minPartitions            = 3

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
  val datasetToclassifer = Map(
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
        .filter("created_at > '2019-11-01'")
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

  }

  def exportDatasetContent(di: DatasetInfo): Unit ={

    val contentIdDwh = getContentDwh(di.content)
      .filter(s"reason == '${di.datasetName}'")
      .select("question_id")

    val datasetSize              = contentIdDwh.count()
    val contentDwh = spark.read.parquet(exportFrom + "/ask_" + di.content).withColumnRenamed("id", di.content+"_id")

    println(s"""
               |copying dataset:    ${di.datasetName}
               | dataset size:      ${datasetSize}
               | """.stripMargin)

    val timeA = System.currentTimeMillis()
    val df = contentIdDwh
      .join(contentDwh, Seq(di.content+"_id"), "inner")
      .withColumn("decoded_title", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf($"title")))
      .withColumn("decoded_body", cleanTextForEmbeddingsUdf(weirdStringFromDbUdf($"body")))
      .withColumn("label", lit("__label__"+di.datasetName))
      .select(di.content+"_id","label", "decoded_title", "decoded_body")

    // ivy-repo
    val basePath = new Path(exportTo + di.content.substring(0,1)
      + "c-deletionreason-"
      + di.datasetName.replaceAll("[\\s\\-()]", "")
      + "/"+ buildNumber )

    val destDir = "positive/parquet"
    val tmpDir = "positive/parquet_tmp_dir"

    val tmpOutputDir = new Path(basePath, tmpDir)
    val toDeleteDir = new Path(basePath, "positive/to_delete")
    val destOutputDir = new Path(basePath, destDir)

    val hdfs = FileSystem.get(spark.sparkContext.hadoopConfiguration)

    df.coalesce(1)
      .write
      .mode("overwrite")
      .parquet(tmpOutputDir.toString)

    // varify dataset
    if( spark.read.parquet(tmpOutputDir.toString).count() <= datasetSize) {
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

    val timeB = System.currentTimeMillis()
    println("\n" + di + " duration: " + ((timeB - timeA) / 1000) + "s")
  }

  def main(args: Array[String]) {
    // avoid NPE when writing parquet metadata
    spark.sparkContext.hadoopConfiguration.setBoolean("parquet.enable.summary-metadata", false)
    config.getString("job.dwh2dataset.export.only") match{
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
