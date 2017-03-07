#!/bin/sh

# set the CLASSPATH to the jar files found in lib/linux

cp=(lib/*.jar lib/linux/*/qtjambi-4.8.7.jar lib/linux/*/qtjambi-native-linux64-gcc-4.8.7.jar)
# cp=(lib/*.jar lib/linux/*.jar)
OLDIFS=$IFS
IFS=:
# This syntax for expanding an array expands to a single word
# with each member separated by the first character in IFS
CLASSPATH="${cp[*]}"
IFS=$OLDIFS

export CLASSPATH

