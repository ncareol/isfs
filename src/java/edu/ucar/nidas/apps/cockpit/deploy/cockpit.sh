#!/bin/sh

source ./classpath.sh

CLASSPATH=$CLASSPATH:classes

java edu.ucar.nidas.apps.cockpit.ui.Cockpit $@
