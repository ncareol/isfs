#!/bin/sh -e

# See usage function for options and format of configuration file.
#
# Assume a configuration file containing the following:
#       # where to put the tarballs and incremental files
#	dest=/backup/data
#       # what to backup. Keys in this bash associative array should be
#       # simple strings, without slashes. Values should be absolute paths.
#	backup[boot]=/boot
#	backup[root]=/
#	backup[home]=/home
#
# For a full backup, no -i incremental option, and gzip compression enabled,
# this script loops over the keys of the backup array (boot, root, home
# in the above example) and does:
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
#       $dest/${key}_YYYYMMDD.tar*, where YYYYMMDD matches any date.
#       If a full backup is not found, a full backup is done instead.
#   2. get YYYYMMDD of that full backup from name
#   3. get NN of last incremental from lexical sort of tar files
#       with that date: ${key}_YYYYMMDD_NN.tar*
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

# If the mount device for a desired backup is an lvm device,
# then the amount of free space on the volume group is checked.
# If it is greater or equal to minfreeM (the value of the -s option)
# then a read-only lvm snapshot is created of the logical volume, using the
# rest of the free space on the volume group.  The snapshot is mounted
# to a temporary mount point and the tar is done on the mounted
# snapshot.
# For example, if root (/) is mounted from /dev/mapper/vg_myhost-lv_root,
# these steps are performed:
#
#    lvcreate --snapshot --permission r -l100%FREE -n lv_root_snapshot \
#		/dev/mapper/vg_myhost-lv_root
#    tmpdir=/tmp/root_ABCDEF  # for example
#    mount /dev/mapper/vg_myhost-lv_root_snapshot -o ro $tmpdir/bkdir
#    cd $tmpdir
#    tar ... bkdir
#
# The value of -s minfreeM should be a guess of the necessary
# free space that should exist in the volume group to save the original
# copy of any file system changes that happen during the backup.
#
# By default a .gz compressed archive is created, but other compression,
# or none, may specified via a runstring argument.
#
# After doing a full backup, this script also saves a sfdisk
# dump of the partitions on all physical disk devices containing
# file systems that were backed up. These configurations are written
# with "sfdisk --dump /dev/$dev > $dest/${dev}_YYYYMMDD.sfdisk",
# where $dev is the PKNAME reported by lsblk, such as "sda".
# This configuration could be restored to an empty disk, with:
#   sfdisk /dev/sda < $dest/sda_YYYYMMDD.sfdisk
#
# If any backed up file systems reside on a LVM partition, the
# volume group information is also saved as part of a full backup:
#   vgcfgbackup -f $dest/${vg}_YYYYMMDD.conf $vg
# This LVM configuation could be restored with:
#   vgcfgrestore -f $dest/${vg}_YYYYMMDD.conf $vg
#   The vgcfg file seems to contain enough information to know
#   where to place the volume group. If not, then these commands
#   may be necessary before the vgcfgrestore:
#   pvcreate /dev/sda1
#   vgcreate $vg /dev/sda1
#   
# TODO:
#   age off old backups?
#	Keep option: -k 3
#	save last 3 full backups and all their incrementals

minfreeMdefault=1000
usage () {
    echo "Usage ${0##*/} [-d] [-i] [ -j | -J | -n | -z ] [-s freeM] config
    -d: print debug messages, don't create backup
    -i: incremental backup of last full backup
    -j: create bzip2 compressed archive with .bz2 suffix
    -J: create xz compressed archive with .xz suffix
    -n: no compression of archive
    -s freeM: minimum Mb of free space on lvm volume group to do a snapshot, default=$minfreeMdefault
    -z: default, gzip archive with .gz suffix
    config: name of configuration file

configuration file should look like the following (bash syntax, avoid spaces!):
    # Where to place backup archive files
    dest=/backup/data
    # What to backup. Keys in this bash associative array should be
    # simple strings, without slashes. Values should be absolute paths.
    # Tar backups of single file systems at these paths will be done.
    backup[boot]=/boot
    backup[root]=/
    backup[home]=/home"
    exit 1
}

[ $# -eq 0 ] && usage 

curdate=$(date +%Y%m%d)

debug=false
incremental=false
suffix=.gz
config=
minfreeM=$minfreeMdefault
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
        -s)
            shift
            [ $# -eq 0 ] && usage
            minfreeM=$1
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
source $config

if [ -z "$dest" ]; then
    echo "dest not specified in $config"
    usage
fi

if [ $(id -u) -ne 0 ]; then
    echo "ERROR: you must be root or use sudo."
    exit 1
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

# array of whole-disk device names that contain file systems
# being backed up, such as "sda"
declare -A diskdevs
# volume groups of backed up file systems 
declare -A volgroups

for key in ${!backup[*]}; do

    echo "tar backup starting: $key, $(date)"

    targ=
    inc=$incremental
    if $inc; then
        lasttar=$(get_last_level0 $key $dest)
        if [ -z "$lasttar" ]; then
            echo "A full backup of $key does not exist. Doing full backup instead of incremental"
            inc=false
        fi
    fi

    if $inc; then
        # echo "lasttar=$lasttar"
        l0date=$(get_date_of_backup $lasttar)
        # echo "l0date=$l0date"
        ninc=$(get_last_incremental ${key}_${l0date} $dest)
        # echo "ninc=$ninc"
        [ -z "$ninc" ] && ninc=-1
        printf -v ninc "%02d" $((ninc+1))
        # echo "ninc=$ninc"
        tarball=${dest}/${key}_${l0date}_$ninc.tar$suffix
        tarinc=${dest}/${key}_${l0date}_$ninc.snar-1
        $debug || cp ${dest}/${key}_${l0date}.snar-0 $tarinc
    else
        tarball=${dest}/${key}_$curdate.tar$suffix
        tarinc=${dest}/${key}_$curdate.snar-0
        $debug || rm -f $tarinc
    fi

    snapshot=false
    mntdev=$(findmnt --noheadings --first-only --output SOURCE ${backup[$key]})
    if [ -n "$mntdev" ] && lvs --no-headings $mntdev > /dev/null 2>&1; then
        lvs=($(lvs --no-headings $mntdev))
        lv=${lvs[0]}
        vg=${lvs[1]}
        snaplv=${lv}_snapshot
        snapdev=/dev/mapper/${vg}-$snaplv
        # echo "lv=$lv"
        # echo "vg=$vg"
        # echo "snaplv=$snaplv"
        # echo "snapdev=$snapdev"
        # unmount and remove snapshot if it exists
        findmnt $snapdev > /dev/null && umount -v $snapdev
        lvs $snapdev > /dev/null 2>&1 && lvremove --force $snapdev
        vgfree=$(vgs -o vg_free --no-headings --units=M --nosuffix $vg)
        echo "Free space on volume group $vg: ${vgfree}M"
        if [ ${vgfree%.*} -lt $minfreeM ]; then
            echo "Free space on $vg is less than $minfreeM."
            echo "lvm snapshot will not be used for backup of $key=${backup[$key]}"
        else
            snapshot=true
        fi
        partdev=$(pvs -S vg_name=$vg -o pv_name --noheadings)
        diskdev=$(lsblk --noheadings --output pkname $partdev | head -n 1)
        diskdevs[$diskdev]=$diskdev
        volgroups[$vg]=$vg
    else
        diskdev=$(lsblk --noheadings --output pkname $mntdev | head -n 1)
        diskdevs[$diskdev]=$diskdev
    fi

    if $snapshot; then
        lvs $snapdev > /dev/null 2>&1 ||
            lvcreate --snapshot --permission r -l100%FREE -n $snaplv $mntdev
        trap "{ lvremove --force $snapdev; }" EXIT
        tmpdir=$(mktemp -d /tmp/${key}_XXXXXX)
        trap "{ rm -rf $tmpdir; lvremove --force $snapdev; }" EXIT
        mntpath=${tmpdir}${backup[$key]}
        # echo "mntpath=$mntpath"
        [ -d $mntpath ] || mkdir -p $mntpath
        findmnt $snapdev > /dev/null && umount -v $snapdev
        mount -v $snapdev -o ro $mntpath
        trap "{ sleep 1; umount -v $mntpath; rm -rf $tmpdir; lvremove --force $snapdev; }" EXIT
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
    trap "{ cd -; sleep 1; umount -v $mntpath; rm -rf $tmpdir; lvremove --force $snapdev; }" EXIT

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
    if $snapshot; then
        sleep 1
        trap "{ rm -rf $tmpdir; lvremove --force $snapdev; }" EXIT
        umount -v $mntpath
        trap "{ lvremove --force $snapdev; }" EXIT
        rm -rf $tmpdir
        trap - EXIT
        lvremove --force $snapdev;
    fi
    ls -l $tarball
    echo "tar backup finished: $key, $(date)"
    echo ""
done

if ! $incremental; then
    # Save partition information for backed up disks
    for dev in ${!diskdevs[*]}; do
        echo "sfdisk --dump /dev/$dev > $dest/${dev}_$curdate.sfdisk"
        sfdisk --dump /dev/$dev > $dest/${dev}_$curdate.sfdisk
    done

    # Save LVM information for backed up logical volumes
    for vg in ${!volgroups[*]}; do
        echo "vgcfgbackup -f $dest/${vg}_$curdate.conf $vg"
        vgcfgbackup -f $dest/${vg}_$curdate.conf $vg
    done
fi
