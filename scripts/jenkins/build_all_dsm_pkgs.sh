#!/bin/bash -e

if [ $# -lt 1 ]; then
    echo "${0##*/} debian_repository"
    exit 1
fi
debrepo=$1

sdir=${0%/*}
sdir=$(readlink -f $sdir)

key='<eol-prog@eol.ucar.edu>'

tmpdir=$(mktemp -d /tmp/${0##*/}_XXXXXX)
trap "{ rm -rf $tmpdir; }" EXIT

cd $ISFS/projects

hashfiles=()

# look for ISFS directories
for debdir in $(find . -name .git -prune -o -type d -name DEBIAN -print); do
    echo $debdir
    # Remove /DEBIAN from path, pass to script
    projdir=${debdir%/*}

    hashfile=$projdir/.last_hash
    [ -f $hashfile ] && last_hash=$(cat $hashfile)

    cd $projdir
    # build if git hash has changed
    this_hash=$(git log -1 --format=%H .)
    cd -
    if [ "$this_hash" == "$last_hash" ]; then
	echo "No updates in $projdir since last build"
	continue
    fi

    $sdir/build_dsm_pkg.sh $projdir $tmpdir && echo $this_hash > $hashfile
    hashfiles+=($hashfile)

done

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
    flock $debrepo sh -c "
	reprepro -V -b $debrepo remove jessie $pkg;
	reprepro -V -b $debrepo deleteunreferenced;
	reprepro -V -b $debrepo includedeb jessie $deb" || rm -f ${hashfiles[*]}
done
