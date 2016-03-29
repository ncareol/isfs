#!/bin/true
#
# set_project.csh script.  This script should be source'd, and
# not exec'd, since it defines environment variables.
#

eval `/bin/bash -c "source $ISFF/scripts/isff_functions.sh; set_project -c $*"`

source $ISFF/scripts/set_cdpath.csh

