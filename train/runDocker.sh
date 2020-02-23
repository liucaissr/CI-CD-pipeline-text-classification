#!/usr/bin/env bash
echo "hallo shell"

repopath=`git rev-parse --show-toplevel`

init(){
    docker rmi -f qc-image:$1
    docker rm qc-container-$1
}

Dep() {
  echo "hallo igor";
  docker build -f train/src/main/docker/Dockerfile -t qc-image:$1 .
  # run nbvonvert
  docker create --name qc-container-$1 -v ${repopath}:/app -i -t -p 8080:8080 qc-image:$1  \
  jupyter nbconvert --to notebook --execute --ExecutePreprocessor.timeout=-1 qc-contact-request-deletionreason.ipynb \
  jupyter nbconvert qc-contact-request-deletionreason.ipynb --output-dir='output' --output='commit1.nbconvert.html'
  docker rmi -f qc-image:$1
  docker rm qc-container-$1
}

Dev() {
  echo "hallo developer";
  hasimage=`docker images --format "{{.Repository}}:{{.Tag}}" | grep "qc-image:$1"`
  if [[ -z "$hasimage" || "$2" == 'initImage' ]] ;then
    docker rmi -f qc-image:$1
    docker rm qc-container-$1
    docker build -f ${repopath}/train/src/main/docker/Dockerfile -t qc-image:$1 .
    docker create --name qc-container-$1 -v ~/.gitconfig:/app/.gitconfig -v ${repopath}:/app/repo -i -t -p 8080:8080 qc-image:$1
  fi
  hasimage=`docker ps -a --format "{{.Image}} {{.Names}}:" | grep "qc-image:$1 qc-container-$1:" `
  if [[ -z "$hascontainer" ||  "$2" == 'initContainer' ]] ;then
    docker rm qc-container-$1
    docker create --name qc-container-$1 -v ~/.gitconfig:/app/.gitconfig -v ${repopath}:/app/repo -i -t -p 8080:8080 qc-image:$1
  fi
  docker start -a -i qc-container-$1
}

"$@"