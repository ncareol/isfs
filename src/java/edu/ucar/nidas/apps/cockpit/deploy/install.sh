#! /bin/sh
#
# Install the cockpit script and required jar files into the target
# directory.

if [ $# -ne 1 ]; then
    cat <<EOF
Usage: $0 <prefix>

Install cockpit run script and required jar files into the <prefix>
directory.

EOF
    exit 1
fi

prefix="$1"
source=`dirname "$0"`

files="cockpit cockpit_linux64.jar lib/linux64/qtjambi-4.8.7.jar"
files="$files lib/linux64/qtjambi-native-linux64-gcc-4.8.7.jar"
for f in $files; do
    (set -x ; cp -fp "$source/$f" "$prefix")
done
