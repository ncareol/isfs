#!/bin/sh

# set -x

script=$0
script=${script##*/}

logdir=$HOME

proc="nidas_udp_relay -h vertex.txt -u 30010"

if ! pgrep -f "$proc" > /dev/null; then
    echo "`date`: $proc not running" >> $logdir/$script.log
    $proc >> $logdir/nidas_udp_relay.log 2>&1
fi

