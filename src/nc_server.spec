Summary: Server for NetCDF file writing.
Name: nc_server
Version: 1.0
Release: 2%{?dist}
License: GPL
Group: Applications/Engineering
Url: http://www.eol.ucar.edu/
Packager: Gordon Maclean <maclean@ucar.edu>

# becomes RPM_BUILD_ROOT, except on newer versions of rpmbuild
BuildRoot:  %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)
Vendor: UCAR
BuildArch: i386 x86_64
Source: %{name}-%{version}.tar.gz
BuildRequires: nidas-x86-build netcdf-devel
Requires: nidas nc_server-devel netcdf
%description
Server for NetCDF file writing.

%package devel
Summary: nc_server library and header file
Group: Applications/Engineering

%description devel
libnc_server_rpc.so library and header file

%package auxprogs
Summary: nc_server auxillary programs
Group: Applications/Engineering
Requires: nc_server-devel

%description auxprogs
nc_server auxillary programs

%prep
%setup -n nc_server

%build
pwd
scons PREFIX=${RPM_BUILD_ROOT}/usr
 
%install
rm -rf $RPM_BUILD_ROOT
scons PREFIX=${RPM_BUILD_ROOT}/usr install
cp scripts/* ${RPM_BUILD_ROOT}/usr/bin
install -d $RPM_BUILD_ROOT%{_sysconfdir}/init.d
cp etc/init.d/* $RPM_BUILD_ROOT%{_sysconfdir}/init.d
cp scripts/* ${RPM_BUILD_ROOT}/usr/bin

%pre

adduser=false
addgroup=false
grep -q ^nidas /etc/passwd || adduser=true
grep -q ^eol /etc/group || addgroup=true

# check if NIS is running. If so, check if nidas.eol is known to NIS
if which ypwhich > /dev/null 2>&1 && ypwhich > /dev/null 2>&1; then
    ypmatch nidas passwd > /dev/null 2>&1 && adduser=false
    ypmatch eol group > /dev/null 2>&1 && addgroup=false
fi

$addgroup && /usr/sbin/groupadd -g 1342 -o eol
$adduser && /usr/sbin/useradd  -u 10035 -o -N -M -g eol -s /sbin/nologin -d /tmp -c NIDAS -K PASS_MAX_DAYS=-1 nidas || :

%post
ldconfig

# To enable the boot script, uncomment this:
# if ! chkconfig --level 3 nc_server; then
#     chkconfig --add nc_server 
# fi

if ! chkconfig --list nc_server > /dev/null 2>&1; then
    echo "nc_server is not setup to run at boot time"
    chkconfig --list nc_server
fi

exit 0

%triggerin -- sudo

tmpsudo=/tmp/sudoers_$$
cp /etc/sudoers $tmpsudo

# Remove requiretty requirement for nidas account so that we can
# do sudo from non-login (crontab) scripts.
if grep -E -q "^Defaults[[:space:]]+requiretty" $tmpsudo; then
    if ! grep -E -q '^Defaults[[:space:]]*:[[:space:]]*[^[:space:]]+[[:space:]]+!requiretty' $tmpsudo; then
        sed -i '
/^Defaults[[:space:]]*requiretty/a\
# The /usr/bin/nc_server.check script starts nc_server via sudo, which may be\
# handy if it needs to be started from a crontab or at other than boot time.\
# The following statements add permission for the "nidas" user to start\
# nc_server via sudo. If nidas is not a login account, change "nidas"\
# to a login account that will want to run nc_server.check or otherwise\
# start nc_server via sudo. Change this !requiretty\ line and the\
# /usr/bin/nc_server line below.\
Defaults:nidas !requiretty\
' $tmpsudo
    fi
fi

if ! { grep NOPASSWD $tmpsudo | grep -q nc_server; }; then
cat << \EOD >> $tmpsudo
nidas ALL=NOPASSWD: SETENV: /usr/bin/nc_server
EOD
fi

visudo -c -f $tmpsudo && cp $tmpsudo /etc/sudoers
rm -f $tmpsudo

%clean
rm -rf $RPM_BUILD_ROOT

%files
/usr/bin/nc_server
/etc/init.d/nc_server

%files auxprogs
/usr/bin/nc_sync
/usr/bin/nc_shutdown
/usr/bin/nc_close
/usr/bin/nc_check
/usr/bin/nc_ping
/usr/bin/nc_server.check

%files devel
/usr/include/nc_server_rpc.h
%_libdir/libnc_server_rpc.so.*
%_libdir/libnc_server_rpc.so

%changelog
* Fri Apr 15 2011 Gordon Maclean <maclean@ucar.edu> 1.0-2
- added /etc/init.d/nc_server boot script. %pre does useradd/groupadd of nidas.eol.
- nc_server has -g runstring option to set the group. sudo is not needed to start.
* Mon Jun  7 2010 Gordon Maclean <maclean@ucar.edu> 1.0-1
- original
