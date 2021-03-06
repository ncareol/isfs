#!/bin/bash -e

install_repo=false
projects=()
while [ $# -gt 0 ]; do
    case $1 in
    -i)
        install_repo=true
        ;;
    *)
        if [ $dest ]; then
            projects+=($1)
        else
            dest=$1
        fi
        ;;
    esac
    shift
done

usage() {
    echo "${0##*/} [-i] dest project ..."
    echo "-i: install in debian repo specified by dest"
    echo "dest: destination of package"
}

if ! [ $dest ]; then
    usage
    exit 1
fi
if [ -z "$projects" ]; then
    usage
    exit 1
fi

dest=$(readlink -f $dest)

sdir=${0%/*}
sdir=$(readlink -f $sdir)

key='<eol-prog@eol.ucar.edu>'

tmpdir=$(mktemp -d /tmp/${0##*/}_XXXXXX)
trap "{ rm -rf $tmpdir; }" EXIT

cd $ISFS/projects

hashfiles=()

# look for dsm/DEBIAN directories
for projdir in ${projects[*]} ; do
    projdir="$projdir/ISFS"
    dsmdir="./$projdir/dsm"
    debdir="$dsmdir/DEBIAN"
    echo $debdir

    if [ ! -d "$debdir" ]; then
	echo "Project $projdir does not have a DEBIAN directory."
	continue
    fi

    hashfile=$dsmdir/.last_hash
    [ -f $hashfile ] && last_hash=$(cat $hashfile)

    cd $projdir
    # build if git hash has changed
    this_hash=$(git log -1 --format=%H .)
    cd -
    if [ "$this_hash" == "$last_hash" ]; then
	echo "No updates in $projdir since last build"
	continue
    fi

    $sdir/build_dsm_pkg.sh $projdir $tmpdir
    echo $this_hash > $hashfile
    hashfiles+=($hashfile)

done

if $install_repo; then

    if [ -e $HOME/.gpg-agent-info ]; then
        export GPG_AGENT_INFO
        . $HOME/.gpg-agent-info
    else
        echo "Warning: $HOME/.gpg-agent-info not found"
    fi

    shopt -s nullglob

    for deb in $tmpdir/*.deb; do

        dpkg-sig -k "$key" --gpg-options "--batch --no-tty" --sign builder $deb

        # remove _debver_all.deb from names of packages passed to reprepro
        pkg=${deb##*/}
        pkg=${pkg%_*}
        pkg=${pkg%_*}
        # deletes all hash files if the reprepro fails :-(
        flock $dest sh -c "
            reprepro -V -b $dest remove jessie $pkg;
            reprepro -V -b $dest deleteunreferenced;
            reprepro -V -b $dest includedeb jessie $deb" || rm -f ${hashfiles[*]}
    done
else
    [ -d $dest ] || mkdir -p $dest
    rsync $tmpdir/*.deb $dest
fi
