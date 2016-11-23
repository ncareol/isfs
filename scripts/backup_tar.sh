#!/bin/sh -e

# An example configuration file for this script, which uses bash syntax:
#       # where to put the tarballs and incremental files
#	dest=/backup/data
#       # what to backup. Values should be absolute paths
#	backup[boot]=/boot
#	backup[root]=/
#	backup[home]=/home
#
# For a full backup, no -i incremental option, this script loops
# over the backup array, by the keys (boot, root, home) and does:
#
#   cd /
#   tar --create --one-file-system --sparse --selinux \
#       --file=$dest/${key}_YYYYMMDD.tar.gz \
#	--listed-incremental=$dest/${key}_YYYYMMDD.snar-0
#	bkdir
#   where bkdir is ${backup[$key]} with the leading slash removed.
#
# With the -i option, a level 1 incremental backup will be created.
#   1. determine most recent full backup, by lexical sort of 
#       $dest/${key}_YYYYMMDD.tar.gz, where YYYYMMDD matches any date.
#       If a full backup is not found, a full backup is done instead.
#   2. get YYYYMMDD of that full backup from name
#   3. get NN of last incremental from lexical sort of tar files
#       with that date: ${key}_YYYYMMDD_NN.tar.gz
#   4. increment NN, or set to 00 if no incrementals found
#   5. Copy ${key}_YYYYMMDD.snar-0 file to ${key}_YYYYMMDD_NN.snar-1,
#      so that an incremental backup is done of the changes since the
#      last full backup.
#
#   cd /
#   tar --create --one-file-system --sparse --selinux \
#       --file=$dest/${key}_YYYYMMDD_NN.tar.gz \
#	--listed-incremental=$dest/${key}_YYYYMMDD_NN.snar-1
#	bkdir

# If a logical volume is specified in the configuration for
# one or more backups:
#	lvdev[root]=/dev/mapper/vg_myhost-lv-root
#	lvdev[home]=/dev/mapper/vg_myhost-lv-home
# Then an lvm snapshot is created of that logical volume, using the
# rest of the free space on the volume group. The snapshot is mounted
# to a temporary mount point and the tar is done on the mounted
# snapshot:
#    lvcreate --snapshot -l100%FREE -n lv-root-snapshot \
#		/dev/mapper/vg_myhost-lv-root
#    tmpdir=/tmp/root_ABCDEF  # for example
#    mount /dev/mapper/vg_myhost-lv-root-snapshot -o ro $tmpdir/bkdir
#    cd $tmpdir
#    tar ... bkdir
#
# Sufficient free space should exist in the volume group containing
# that logical volume to hold any file system changes that happen while
# the snapshot is mounted.
#
# By default a .gz compressed archive is created, but other compression,
# or none, may specified via a runstring argument.
#
# TODO:
#   automate lvm handing: determine logical volume and use lvm tools
#	to determine if volume group has a minimum of free space
#   age off old backups?
#	Keep option: -k 3
#	save last 3 full backups and all their incrementals
# Other things that should be done for a full backup:
#   save sfdisk config: sfdisk --dump /dev/sda > /backup/disk/sda.dump
#   save lvm config: vgcfgbackup -f /backup/disk/vg_myhost.conf vg_myhost

usage () {
    echo "Usage ${0##*/} [-d] [-i] [ -j | -J | -n | -z ] config"
    echo "-d: print debug messages, don't create backup"
    echo "-i: incremental backup of last full backup"
    echo "-j: create bzip2 compressed archive with .bz2 suffix"
    echo "-J: create xz compressed archive with .xz suffix"
    echo "-n: no compression of archive"
    echo "-z: default, gzip archive with .gz suffix"
    echo "config: name of configuration file"
    exit 1
}

[ $# -eq 0 ] && usage 

curdate=$(date +%Y%m%d)

debug=false
incremental=false
suffix=.gz
config=
while [ $# -gt 0 ]; do
    case $1 in
    -i)
        incremental=true
        ;;
    -d)
        debug=true
        ;;
    -j)
        suffix=.bz2
        ;;
    -J)
        suffix=.xz
        ;;
    -n)
        suffix=
        ;;
    -z)
        suffix=.gz
        ;;
    *)
        config=$1
        ;;
    esac
    shift
done

[ -z "$config" ] && usage
if ! [ -f $config ]; then
    echo "$config not found"
    usage
fi

declare -A backup
declare -A lvdev
source $config

if [ -z "$dest" ]; then
    echo "dest not specified in $config"
    usage
fi

# On Nov 20, without --sparse, root tar file was 139G
#       but root was only 41G out of 50G
#       Then, with --sparse, tar file was 40G
#	man page says --sparse may be twice as slow
# With --sparse and no compression, time of root tar:
#	real    68m6.765s
#	user    2m4.663s
#	sys	4m21.131s
#	size of tar:  40G
# With --sparse, and .gz:
#	real	70m40.905s
#	user	31m44.015s
#	sys	4m5.377s
#	size of tar:  24G

# --auto-compress suffixes:
# gzip: .gz, .tgz, .taz
# compress: .Z, .taZ
# bzip2: .bz2, tz2, tbz2, tbz
# lz: .lzip
# lzma: .lzma, .tlz
# .lzo: .lzop
# .xz: .xz

# -V TEXT: volume label

get_last_level0 () {
    local name=$1
    local dest=$2
    local res
    local tarball=$(ls -1 $dest/${name}_20[12][0-9][01][0-9][0-3][0-9].tar* 2>/dev/null | sort | tail -n 1)
    if [ -n "$tarball" ]; then
    	local tarinc=${tarball/.tar*/.snar-0}
	if [ -f $tarinc ]; then
	    res=$tarball
	fi
    fi
    echo $res
}

get_date_of_backup () {
    local tarball=$1
    tarball=${tarball##*/}
    tarball=${tarball%.tar*}
    local l0date=${tarball#*_}
    echo $l0date
}

get_last_incremental () {
    local namedate=$1
    local dest=$2
    local res=
    local tarball=$(ls -1 $dest/${namedate}_[0-9][0-9].tar* 2>/dev/null | sort | tail -n 1)
    if [ -n "$tarball" ]; then
    	local tarinc=${tarball/.tar*/.snar-1}
	if [ -f $tarinc ]; then
	    tarball=${tarball##*/}
	    tarball=${tarball#$namedate}
	    tarball=${tarball%.tar*}
	    tarball=${tarball#_}
	    res=$tarball
	fi
    fi
    echo $res
}

renice +20 -p $$

for key in ${!backup[*]}; do

    echo "tar backup starting: $key, $(date)"

    targ=
    inc=$incremental
    if $inc; then
        lasttar=$(get_last_level0 $key $dest)
	if [ -z "$lasttar" ]; then
	    echo "A level 0 backup of $key does not exist. Doing level 0 instead of incremental"
            inc=false
        fi
    fi

    if $inc; then
        # echo "lasttar=$lasttar"
	l0date=$(get_date_of_backup $lasttar)
        # echo "l0date=$l0date"
	ninc=$(get_last_incremental ${key}_${l0date} $dst)
        # echo "ninc=$ninc"
        if [ -n "$ninc" ]; then
	    ninc=$(printf "%02d" $((ninc+1)))
	else
	    ninc=00
	fi
        # echo "ninc=$ninc"
	tarball=${dest}/${key}_${l0date}_$ninc.tar$suffix
	tarinc=${dest}/${key}_${l0date}_$ninc.snar-1
	$debug || cp ${dest}/${key}_${l0date}.snar-0 $tarinc
    else
	tarball=${dest}/${key}_$curdate.tar$suffix
	tarinc=${dest}/${key}_$curdate.snar-0
	$debug || rm -f $tarinc
    fi

    if [ -n "${lvdev[$key]}" ]; then
        newlv=lv_${key}_snapshot
        # echo "newlv=$newlv"
        vgpath=${lvdev[$key]%-lv*}
        # echo "vgpath=$vgpath"
        vg=${vgpath##*/}
        # echo "vg=$vg"
        lvpath=${vgpath}-$newlv
        # echo "lvpath=$lvpath"
        lvs $lvpath > /dev/null 2>&1 ||
            lvcreate --snapshot -l100%FREE -n $newlv ${lvdev[$key]}
        trap "{ lvremove --force $lvpath; }" EXIT
        tmpdir=$(mktemp -d /tmp/${key}_XXXXXX)
        trap "{ rm -rf $tmpdir; lvremove --force $lvpath; }" EXIT
        mntpath=${tmpdir}${backup[$key]}
        # echo "mntpath=$mntpath"
        [ -d $mntpath ] || mkdir -p $mntpath
        mount | grep -Fq $lvpath && umount -v $lvpath
        mount -v $lvpath -o ro $mntpath
        trap "{ sleep 1; umount -v $mntpath; rm -rf $tmpdir; lvremove --force $lvpath; }" EXIT
        # remove leading slash
        bkdir=${backup[$key]#/}
        if [ -z "$bkdir" ]; then
            bkdir=.
            # remove ./ in front of path names
            targ="--transform=s,^\./,,"
        fi
	cddir=$tmpdir
    else
        # remove leading slash
        bkdir=${backup[$key]#/}
        [ -z "$bkdir" ] && bkdir=/
	cddir=/
    fi

    cd $cddir
    trap "{ cd -; sleep 1; umount -v $mntpath; rm -rf $tmpdir; lvremove --force $lvpath; }" EXIT

    echo "PWD=$PWD"
    echo "backup=$bkdir"
    echo "tarball=$tarball"
    echo "tarinc=$tarinc"
    if ! $debug; then
	time tar --create --one-file-system --auto-compress \
	    --selinux --no-check-device --sparse $targ \
	    --listed-incremental=$tarinc \
	    --file=$tarball \
            $bkdir
    fi
    cd - > /dev/null
    if [ -n "${lvdev[$key]}" ]; then
        sleep 1
        trap "{ rm -rf $tmpdir; lvremove --force $lvpath; }" EXIT
	umount -v $mntpath
        trap "{ lvremove --force $lvpath; }" EXIT
	rm -rf $tmpdir
	trap - EXIT
	lvremove --force $lvpath;
    fi
    ls -l $tarball
    echo "tar backup finished: $key, $(date)"
    echo ""
done

