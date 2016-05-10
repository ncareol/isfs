#!/bin/bash -e

# Build a debian package of the field project meta-data and scripts
# for DSMs. This may be built by jenkins which may
# not have the full ISFS environment setup.

# Build system must be able to execute the nidas proj_configs and ck_xml commands
source $ISFS/scripts/isfs_functions.sh

# directories to put in the package which will end up
# at $ISFS/projects/$PROJECT/ISFS
pkgcontents=(config cal_files dsm/scripts)

# files to put in the $DAQ_USER's home

usage() {
    echo "Usage: ${1##*/} projdir dest"
    echo "projdir: ISFS project directory, typically $ISFS/projects/$PROJECT/ISFS"
    echo "dest: destination, default is ."
    exit 1
}

while [ $# -gt 0 ]; do
    case $1 in
    -h)
        usage
        ;;
    *)
        if [ $projdir ]; then
            dest=$1
        else
            projdir=$1
        fi
        ;;
    esac
    shift
done

if ! [ $dest ]; then
    usage $0
fi

# avoid old dpkg commands in /opt/arcom/bin
PATH=/usr/bin:$PATH

# projdir should be $ISFS/projects/$PROJECT/ISFS
# directories in $projdir are placed at
# /home/daq/isfs/projects/$PROJECT/ISFS
# in the debian package

cd $projdir

# lop off ISFS
pdir=${projdir%/*}
# get project name
export PROJECT=${pdir##*/}

isfs_env $PROJECT

if ! [ -f dsm/DEBIAN/control ]; then
    echo "$PWD/DEBIAN/control not found"
    exit 1
fi

# Get package name
dpkg=$(grep "^Package:" dsm/DEBIAN/control | awk '{print $2}')

if gitdesc=$(git describe --match "v[0-9]*"); then
    # example output of git describe: v2.0-14-gabcdef123
    gitdesc=${gitdesc/#v}       # remove leading v
    version=${gitdesc%-g*}       # 2.0-14
else
    version="1.0-1"
    echo "git describe failed, looking for a tag of the form v[0-9]*. Using $version"
fi

tmpdir=$(mktemp -d /tmp/${0##*/}_XXXXXX)
tmptar=$(mktemp /tmp/${0##*/}_XXXXXX.tar)
trap "{ rm -rf $tmpdir $tmptar; }" EXIT

pkgdir=$tmpdir/$dpkg
tmp_isfs=$pkgdir/home/daq/isfs
tmp_proj=$tmp_isfs/projects/$PROJECT/ISFS
mkdir -p $tmp_proj

# DEBIAN
rsync -aC --exclude=.gitignore dsm/DEBIAN $pkgdir
rsync -aC --exclude=.gitignore --ignore-missing-args dsm/hosts-field $pkgdir/tmp/hosts-field
rsync -aC --exclude=.gitignore --ignore-missing-args dsm/home $pkgdir
rsync -aC --exclude=.gitignore --ignore-missing-args ${pkgcontents[*]} $tmp_proj

echo $PROJECT > $tmp_isfs/current_project

# Get the NIDAS xmls of this project
cf=$tmp_proj/config/configs.xml 

# Write the list to config/nidas_xmls.list
# which is used by the DEBIAN/postinst script

xmls=()
# set -x
if [ -f $cf ]; then
    # command on /opt/nidas/bin: proj_configs
    proj_configs -l $cf | awk '{print $2}' | sort -u > $tmp_proj/config/nidas_xmls.list
    xmls=($(<$tmp_proj/config/nidas_xmls.list))
    if [ ${#xmls[*]} -eq 0 ]; then
        echo "Warning: \"proj_configs -l $cf\" found no XML files"
    fi
    # check xml files. Could exclude ones that don't parse
    for xml in ${xmls[*]}; do
        xml=$(eval echo $xml)
        ck_xml $xml > /dev/null
    done
else
    echo "Warning: $cf not found"
fi

cf=$pkgdir/usr/share/doc/$dpkg/changelog.Debian.gz
cld=${cf%/*}
[ -d $cld ] || mkdir -p $cld

cat << EOD | gzip -c -9 > $cf
$dpkg Debian maintainer and upstream author are identical.
Therefore see also normal changelog file for Debian changes.
EOD

# output gzipped git log to usr/share/doc/ol-daq

sed -i -e "s/^Package:.*/Package: $dpkg/" $pkgdir/DEBIAN/control
sed -i -e "s/^Version:.*/Version: $version/" $pkgdir/DEBIAN/control

chmod -R g-ws $pkgdir/DEBIAN

tar cf $tmptar --mtime="2010-01-01 00:00" -C $pkgdir .

fakeroot dpkg-deb -b $pkgdir

# dpkg-name: info: moved 'eol-daq.deb' to '/tmp/build_dpkg.sh_4RI6L9/eol-daq_1.0-1_all.deb'
newname=$(dpkg-name ${pkgdir%/*}/${dpkg}.deb | sed -r -e "s/.* to '([^']+)'.*/\1/")

echo "moving $newname to $dest"
mv $newname $dest

