#!/bin/true
#
# set_project.csh script.  This script should be source'd, and
# not exec'd, since it defines environment variables.
#

eval `/bin/bash -c "source $ISFS/scripts/isfs_functions.sh; set_project -c $*"`

source $ISFS/scripts/set_cdpath.csh

