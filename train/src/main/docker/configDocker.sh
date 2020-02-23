#!/bin/bash
cp .gitconfig /root/.gitconfig
cd repo
nbstripout --install #to clean up notebook output when git add
git config core.hooksPath hooks
git config credential.helper store
cd train/src/main/notebook
bash #start docker with bash