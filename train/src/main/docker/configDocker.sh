#!/bin/bash
cp .gitconfig /root/.gitconfig
cd repo
nbstripout --install --global #to clean up notebook output when git add
git config --global core.hooksPath hooks
git config --global credential.helper store
cd train/src/main/notebook
bash #start docker with bash