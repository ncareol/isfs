#!/bin/bash -e

cd $ISFS/projects

for script in find . -name .git -prune -o -name build_dsm_pkg.sh -print; do
    echo $script
done
