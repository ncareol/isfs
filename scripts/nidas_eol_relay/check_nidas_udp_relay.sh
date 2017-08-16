#!/bin/sh

export PATH=/bin:/usr/bin:/opt/nidas/bin
# set -x

script=$0
script=${script##*/}
logdir=$HOME/nidas_eol_relay

# museum UDP stream bound for barolo
proc="nidas_udp_relay -h artse.txt -u 30010"

if ! pgrep -f "$proc" > /dev/null; then
    (cd "$logdir"
	echo "`date`: ***** $proc not running, starting it..."
	exec $proc) >> $logdir/nidas_eol_relay.log 2>&1
fi

# airport UDP stream bound for barolo and flux2
proc="nidas_udp_relay -h artse.txt -u 30011"

if ! pgrep -f "$proc" > /dev/null; then
    (cd "$logdir"
	echo "`date`: ***** $proc not running, starting it..."
	exec $proc) >> $logdir/nidas_eol_relay_2.log 2>&1
fi

