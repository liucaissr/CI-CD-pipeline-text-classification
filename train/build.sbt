// docker build -t qc-contactrequest-train-image:1-snapshot train/src/main/docker
// docker run -d --name qc-contactrequest-train-container-1-snapshot -v /Users/christian.dedie/Projects/datateam/qc-contactrequest/train/src/main/notebook:/usr/src/app qc-contactrequest-train-image:1-snapshot
// docker exec qc-contactrequest-train-container-1-snapshot pip install --upgrade pip

// docker run --name qc-contactrequest-train-container-1-snapshot --rm -i -t -v /Users/christian.dedie/Projects/datateam/qc-contactrequest/train/src/main/notebook:/usr/src/app python:3.6.9
