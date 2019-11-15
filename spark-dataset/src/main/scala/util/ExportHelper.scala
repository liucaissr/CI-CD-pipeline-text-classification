package util

import com.typesafe.config.Config
import jobs.Dwh2Positive.hdfs
import org.apache.hadoop.fs.Path
import org.apache.spark.sql.{DataFrame, SparkSession}
import net.gutefrage.etl.commons.conf.DbConfig

import scala.util.Properties

class ExportHelper(spark: SparkSession, config: Config) {

  lazy val statDbConfig = DbConfig("mysql.write")

  val jdbcDatabase = config.getString("mysql.stat.database")
  val jdbcTable    = config.getString("mysql.stat.table")

  val buildNumber = Properties.envOrNone("BUILD_NUMBER").getOrElse("1-SNAPSHOT")

  def datasetWriter(di: DatasetInfo, df: DataFrame, basePath: String, classifier: String, datasetSize: Long): Unit = {
    // ivy-repo
    val base    = new Path(basePath)
    val destDir = classifier + "/parquet"
    val tmpDir  = classifier + "/parquet_tmp_dir"
    val delDir  = classifier + "/to_delete"

    val tmpOutputDir  = new Path(base, tmpDir)
    val toDeleteDir   = new Path(base, delDir)
    val destOutputDir = new Path(base, destDir)

    df.coalesce(1)
      .write
      .mode("overwrite")
      .parquet(tmpOutputDir.toString)

    val tempParquetCount = spark.read.parquet(tmpOutputDir.toString).count()

    // varify dataset
    if ((tempParquetCount <= datasetSize) && (statsReader(di, tempParquetCount, classifier))) {
      println(s"""
                 | The dataset size is verified, exporting ...
                 """.stripMargin)
      if (hdfs.exists(destOutputDir)) {
        hdfs.rename(destOutputDir, toDeleteDir)
      }
      hdfs.rename(tmpOutputDir, destOutputDir)
      statsWriter(di, tempParquetCount, classifier)
    } else {
      println(s"""
                 | The dataset size is abnomaly large, stoping ...
                 """.stripMargin)
      hdfs.rename(tmpOutputDir, toDeleteDir)

    }
    hdfs.delete(toDeleteDir, true)
  }

  def statsWriter(di: DatasetInfo, count: Long, ivyClassifier: String): Unit = {
    val stmt = statDbConfig.getConnection().createStatement()
    println(s"""
         |INSERT INTO ${jdbcDatabase}.${jdbcTable}
         |(dataset,content,count,application, label, is_validated, build_number)
         |VALUES ('${di.datasetName}', '${di.content}', ${count}, 'deletion-reason', '${ivyClassifier}', ${true}, '${buildNumber}')
         |""".stripMargin)
    stmt.executeUpdate(s"""
         |INSERT INTO ${jdbcDatabase}.${jdbcTable}
         |(dataset, content, count, application, label, is_validated, build_number)
         |VALUES ('${di.datasetName}', '${di.content}', ${count}, 'deletion-reason', '${ivyClassifier}', ${true}, '${buildNumber}')
         |""".stripMargin)
    stmt.close()
  }

  def statsReader(di: DatasetInfo, count: Long, ivyClassifier: String): Boolean = {
    val stmt = statDbConfig.getConnection().createStatement()
    //todo: change col application to svc
    println(s"""
         |Select count, created_at from ${jdbcDatabase}.${jdbcTable}
         |where (dataset = '${di.datasetName}' and content = '${di.content}' and application = 'deletion-reason' and label = '${ivyClassifier}')
         |order by created_at desc
         |""".stripMargin)
    val result    = stmt.executeQuery(s"""
         |Select count, created_at from ${jdbcDatabase}.${jdbcTable}
         | where (dataset = '${di.datasetName}' and content = '${di.content}' and application = 'deletion-reason' and label = '${ivyClassifier}')
         | order by created_at desc
         |""".stripMargin)
    var last_size = 0
    if (result.next()) {
      last_size = result.getInt("count")
    }
    stmt.close()
    if (last_size <= count) {
      true
    } else {
      false
    }
  }
}
