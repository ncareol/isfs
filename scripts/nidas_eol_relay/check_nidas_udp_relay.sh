#!/bin/sh

export PATH=/bin:/usr/bin:/opt/nidas/bin
# set -x

script=$0
script=${script##*/}
logdir=$HOME/nidas_eol_relay

export NTOP=/home/granger/opt/nidas
export NTOP=/opt/nidas
export LD_LIBRARY_PATH="$NTOP/lib64"
export PATH="$NTOP/bin:${PATH}"
proc="nidas_udp_relay -h artse.txt -u 30010"

debug=0
if [ "$1" == debug ]; then
    debug=1
    shift
fi

if [ $debug -ne 1 ]; then
    exec >> $logdir/nidas_eol_relay.log 2>&1
fi

start()
{
    if ! pgrep -f "$proc" > /dev/null; then
	(cd "$logdir"
	    echo "`date`: ***** $proc not running, starting it..."
	    exec $proc) &
    fi
}

stop()
{
    pkill -f "$proc"
}


case "$1" in

    start)
	start
	;;

    stop)
	stop
	;;

    status)
	pid=`pgrep -f "$proc"`
	if [ -n "$pid" ]; then
	    (set -x ; ps ufp "$pid")
	else
	    echo "Not running."
	fi
	;;

    *)
	echo "Usage: $0 [debug] {start|stop|status}"
	exit 1
	;;

esac
