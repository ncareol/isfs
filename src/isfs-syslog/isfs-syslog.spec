Summary: Additions to syslog config for ISFS logging
Name: isfs-syslog
Version: %{gitversion}
Release: %{releasenum}%{?dist}
License: GPL
Group: System Environment/Daemons
Url: http://www.eol.ucar.edu/
Packager: Gordon Maclean <maclean@ucar.edu>
Vendor: UCAR
BuildArch: noarch
# Obsoletes: isff-syslog <= 1.0-5
Obsoletes: isff-syslog

# rsyslog provides syslog, so this works with rsyslog
Requires: rsyslog

Source: %{name}-%{version}.tar.gz

%description
Additions to rsyslog config and logrotate for ISFS logging

%prep
%setup -n %{name}

%build

%install
rm -rf $RPM_BUILD_ROOT
install -d $RPM_BUILD_ROOT%{_sysconfdir}
cp -r etc/* $RPM_BUILD_ROOT%{_sysconfdir}

%clean
rm -rf $RPM_BUILD_ROOT

%triggerin -- rsyslog

[ -d /var/log/isfs ] || mkdir /var/log/isfs
touch /var/log/isfs/isfs.log /var/log/isfs/isfs_debug.log
chmod -R 755 /var/log/isfs /var/log/isfs/isfs_debug.log

cf=/etc/rsyslog.conf
rssyslog=false
# suppress local5 messages in /var/log/message, by adding
# local5.none to rsyslog.conf entry
if ! grep -v "^#" $cf | grep -F /var/log/messages | grep -qF local5.none; then
    rssyslog=true
    sed -r -i -e '
/^[^#].*\/var\/log\/messages/s,^([^[:space:]]+)([[:space:]]+/var/log/messages),\1;local5.none\2,
' $cf
fi
if ! grep -v "^#" $cf | grep -F @lurker | grep -qF local5.none; then
    rssyslog=true
    sed -r -i -e '
/^[^#].*@lurker/s,^([^[:space:]]+)([[:space:]]+@lurker),\1;local5.none\2,
' $cf
fi

# old isff-syslog added a line to /etc/sysconfig/rsyslog
# with a -c option which is no longer supported.
cf=/etc/sysconfig/rsyslog
if grep -Eq "^[[:space:]]*SYSLOGD_OPTIONS.*-c" $cf; then
    rssyslog=true
    sed -r -i -e 's/^[[:space:]]*(SYSLOGD_OPTIONS.*-c)/# \1/' $cf
fi

$rssyslog && systemctl restart rsyslog.service

%files
%defattr(-,root,root)
%config /etc/rsyslog.d/isfs.conf
%config /etc/logrotate.d/isfs

%changelog
