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
# Allow this package to be relocatable to other places than /opt/local/nidas/x86
# rpm --relocate /opt/local/nidas/x86=/usr --relocate /opt/local/nidas/share=/usr/share
%description devel
libnc_server_rpc.so library and header file

%package auxprogs
Summary: nc_server auxillary programs
Group: Applications/Engineering
Requires: nc_server-devel
# Allow this package to be relocatable to other places than /opt/local/nidas/x86
# rpm --relocate /opt/local/nidas/x86=/usr --relocate /opt/local/nidas/share=/usr/share
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
if [ "$1" -eq 1 ]; then
    addnidas=true
    addeol=true
    # check if NIS is running
    if which ypwhich > /dev/null 2>&1 && ypwhich > /dev/null 2>&1; then
        ypmatch nidas passwd > /dev/null 2>&1 /dev/null && addnidas=false
        ypmatch eol group > /dev/null 2>&1 /dev/null && addeol=false
    fi

    $addeol && /usr/sbin/groupadd -g 1342 -f -r eol >/dev/null 2>&1 || :;
    $addnidas && /usr/sbin/useradd  -u 11009 -N -M -g eol -s /sbin/nologin -d /tmp -c NIDAS -K PASS_MAX_DAYS=-1 nidas >/dev/null 2>&1 || :;
fi;

%post
ldconfig

# To enable the boot script, uncomment this:
# if ! chkconfig --level 3 nc_server; then
#     chkconfig --add nc_server 
# fi

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
- added /etc/init.d/nc_server boot script, and useradd of nidas.eol user
* Mon Jun  7 2010 Gordon Maclean <maclean@ucar.edu> 1.0-1
- original
