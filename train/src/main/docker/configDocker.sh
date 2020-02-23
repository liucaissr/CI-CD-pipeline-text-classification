#!/bin/bash
cd caggle
nbstripout --install #to clean up notebook output when git add
git config core.hooksPath hooks
git config credential.helper store
bash