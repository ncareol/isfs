#!/bin/sh

export PATH=/bin:/usr/bin:/opt/nidas/bin
# set -x

script=$0
script=${script##*/}
logdir=$HOME/nidas_eol_relay

proc="nidas_udp_relay -h perdigao.txt -u 30010"

if ! pgrep -f "$proc" > /dev/null; then
    (cd "$logdir"
	echo "`date`: ***** $proc not running, starting it..."
	exec $proc) >> $logdir/nidas_eol_relay.log 2>&1
fi

