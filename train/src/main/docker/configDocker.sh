#!/bin/bash
git config credential.helper store
git config --global core.hooksPath hooks
cd caggle
nbstripout --install #to clean up notebook output when git add
bash