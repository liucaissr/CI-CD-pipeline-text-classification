#!/usr/bin/env bash
echo "hallo shell"

repopath=`git rev-parse --show-toplevel`

init(){
    docker rmi -f qc-contactrequest-image:$1
    docker rm qc-contactrequest-container-$1
}

Dep() {
  echo "hallo igor";
  docker build -f train/src/main/docker/Dockerfile -t qc-contactrequest-image:$1 .
  # run nbvonvert
  docker create --name qc-contactrequest-container-$1 -v ${repopath}:/app -i -t -p 8080:8080 qc-contactrequest-image:$1  \
  jupyter nbconvert --to notebook --execute --ExecutePreprocessor.timeout=-1 qc-contact-request-deletionreason.ipynb \
  jupyter nbconvert qc-contact-request-deletionreason.ipynb --output-dir='output' --output='commit1.nbconvert.html'
  docker rmi -f qc-contactrequest-image:$1
  docker rm qc-contactrequest-container-$1
}

Dev() {
  echo "hallo developer";
  imageexisted=`docker images --format "{{.Repository}}:{{.Tag}}" | grep qc-contactrequest-image:$1`
  if [[ -z "$imageexisted" || "$2" == 'initImage' ]] ;then
    docker rmi -f qc-contactrequest-image:$1
    docker rm qc-contactrequest-container-$1
    docker build -f ${repopath}/train/src/main/docker/Dockerfile -t qc-contactrequest-image:$1 .
    docker create --name qc-contactrequest-container-$1 -v ~/.gitconfig:/etc/gitconfig -v ${repopath}:/app -i -t -p 8080:8080 qc-contactrequest-image:$1 bash
  fi
  if [[ "$2" == 'initContainer' ]] ;then
    docker rm qc-contactrequest-container-$1
    docker create --name qc-contactrequest-container-$1 -v ~/.gitconfig:/etc/gitconfig -v ${repopath}:/app -i -t -p 8080:8080 qc-contactrequest-image:$1 bash
  fi
  docker start -a -i qc-contactrequest-container-$1
}

"$@"


