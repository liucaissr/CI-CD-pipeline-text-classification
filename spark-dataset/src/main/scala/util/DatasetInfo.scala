package util

case class DatasetInfo(content: String, datasetName: String) {
  override def toString: String = content + "." + datasetName
}
