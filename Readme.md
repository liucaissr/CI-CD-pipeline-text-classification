# Question Classifier of deletion reason contact request
TDSP Piepeline instruction:
https://docs.microsoft.com/en-us/azure/machine-learning/team-data-science-process/overview
- Business Understanding
- Data Acquisition and Understanding
- Modeling
- Deployment
- Customer Acceptance
    
## Data Acquisition and Understanding

### Project spark-postive
- It exports dataset related to contactrequest questions to hdfs directory:
http://hue.endor.gutefrage.net/hue/filebrowser/view=/user/hue#/data-projects/dataset/ivy-repo/releases/net.gutefrage.data/qc-deletionreason-contactrequest
- start: ```spark2-submit --conf spark.ui.port=4051  --driver-class-path /etc/hadoop/conf --class Dwh2Positive spark-positive-assembly-1-SNAPSHOT.jar```

### Project spark-negative
- It exports dataset alexa-like questions to hdfs directory:
http://hue.endor.gutefrage.net/hue/filebrowser/view=/user/hue#/data-projects/dataset/ivy-repo/releases/net.gutefrage.data/qc-alexa
- start: ```spark2-submit --conf spark.ui.port=4051  --driver-class-path /etc/hadoop/conf --class Dwh2Negative spark-negative-assembly-1-SNAPSHOT.jar```

