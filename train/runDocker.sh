echo "hallo docker"

docker build -f train/src/main/docker/Dockerfile -t qc-contactrequest-train-image:$1 .
docker run --name qc-contactrequest-train-container-$1 -v /var/lib/jenkins/workspace/Data/qc-contactrequest:/usr/src/app qc-contactrequest-train-image:$1
docker rmi -f qc-contactrequest-train-image:$1
docker rm qc-contactrequest-train-container-$1