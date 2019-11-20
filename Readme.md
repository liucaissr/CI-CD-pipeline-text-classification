# Question Classifier of deletion reason contact request
TDSP Piepeline instruction:
https://docs.microsoft.com/en-us/azure/machine-learning/team-data-science-process/overview
- Business Understanding
- Data Acquisition and Understanding
- Modeling
- Deployment
- Customer Acceptance
    
## Data Acquisition and Understanding

### Project spark-dataset
- It exports dataset related to contactrequest questions to hdfs directory:
http://hue.endor.gutefrage.net/hue/filebrowser/view=/user/hue#/data-projects/dataset/ivy-repo/releases/net.gutefrage.data/qc-deletionreason-contactrequest
- start positive job: ```spark2-submit --conf spark.ui.port=4051  --driver-class-path /etc/hadoop/conf --class jobs.Dwh2Positive spark-dataset-assembly-1-SNAPSHOT.jar```
- start negative job: ```spark2-submit --conf spark.ui.port=4051  --driver-class-path /etc/hadoop/conf --class jobs.Dwh2Negative spark-dataset-assembly-1-SNAPSHOT.jar```
- Statistic is written in to lugger experimental/ai_dataset_exp temporarily

## Run notebook locally

### Setup

```
pyenv virtualenv 3.6.9 qc-contactrequest
pyenv activate qc-contactrequest
pip install --upgrade pip && pip install --no-cache-dir -r requirements.txt
```
