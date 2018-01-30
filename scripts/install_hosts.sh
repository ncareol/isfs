#! /bin/sh

# Update the local hosts file with field host entries.  Useful for field
# network server, travel laptops, and DSMs.  Follows the original separator
# line convention used by the isfs-field-<project> DEBIAN packages for
# installing the hosts-field file.
#
# If $PROJECT is set, then look for the hosts.xml from that project by
# default.


genhosts="env PYTHOPATH=$ISFS/projects/python $ISFS/projects/python/isfs/Hosts.py"

backup_hosts() # <hosts-file>
{
    hosts="$1"
    test ! -f "${hosts}.bak" && cp -p "${hosts}" "${hosts}.bak"
}


install_hosts() # <dest-hosts-file> [Hosts.py arguments ...]
{
    hosts="$1"
    shift

    tmpfile=$(mktemp)
    cp "$hosts" "$tmpfile"

    # remove previous field project entries from /etc/hosts
    sed -r -i -e '/^# Start of field project entries/,/^# End of field project entries/d' $tmpfile
    # add new ones
    echo "# Start of field project entries" >> $tmpfile
    (set -x; $genhosts "$@" >> $tmpfile) || exit 1
    echo "# End of field project entries" >> $tmpfile
    # check for difference 
    diff -q $tmpfile "$hosts" || cp $tmpfile "$hosts"
    rm -f $tmpfile
}


hosts="$1"
shift

if [ -z "$hosts" ]; then
    hosts="/etc/hosts"
fi
if [ ! -f "$hosts" ]; then
    echo "Hosts file not found: $hosts!"
    exit 1
fi
if [ ! -w "$hosts" ]; then
    echo "Cannot write: $hosts"
    echo "Maybe you need to sudo?"
    exit 1
fi

echo "Installing host entries into ${hosts}..."

backup_hosts "$hosts"
install_hosts "$hosts" "$@"

echo "Done."
