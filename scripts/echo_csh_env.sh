#!/bin/bash

declare -F isfs_env > /dev/null || source $ISFF/scripts/isfs_functions.sh

isfs_env -c "$@"

