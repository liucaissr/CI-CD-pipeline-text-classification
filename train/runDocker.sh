#!/usr/bin/env bash
echo "hallo shell"

init(){
    docker rmi -f qc-contactrequest-train-image:$1
    docker rm qc-contactrequest-container-$1
}

Dep() {
  echo "hallo igor";
  docker build -f train/src/main/docker/Dockerfile -t qc-contactrequest-image:$1 .
  # run nbvonvert
  docker run --name qc-contactrequest-container-$1 -v $PWD/train/target:/usr/target -v $PWD/train/src/main/notebook:/usr/src/main/notebook qc-contactrequest-image:$1  \
  jupyter nbconvert --to notebook --execute --ExecutePreprocessor.timeout=-1 qc-contact-request-deletionreason.ipynb \
  jupyter nbconvert qc-contact-request-deletionreason.ipynb --output-dir='output' --output='commit1.nbconvert.html'
  docker rmi -f qc-contactrequest-image:$1
  docker rm qc-contactrequest-container-$1
}

Dev() {
  echo "hallo developer";
  docker build -f train/src/main/docker/Dockerfile -t qc-contactrequest-image:$1 .
  # run jupyter notebook
  docker run --name qc-contactrequest-container -v $PWD/train/target:/usr/target -v $PWD/train/src/main/notebook:/usr/src/main/notebook -p 8080:8080 qc-contactrequest-image:$1 jupyter notebook --ip=0.0.0.0 --port=8080 --no-browser --allow-root
  docker rmi -f qc-contactrequest-image:$1
  docker rm qc-contactrequest-container-$1
}

"$@"


