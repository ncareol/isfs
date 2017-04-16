#!/bin/sh

# For running cockpit directly against the class files built by the
# makejar script.

source ./classpath.sh

CLASSPATH=$CLASSPATH:classes_linux64

java edu.ucar.nidas.apps.cockpit.ui.Cockpit "$@"
