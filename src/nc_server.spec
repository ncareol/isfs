Summary: Server for NetCDF file writing.
Name: nc_server
Version: 1.0
Release: 1%{?dist}
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
Requires: nidas
%description
Server for NetCDF file writing.

%package devel
Summary: nc_server headers
Requires: nc_server
Group: Applications/Engineering
# Allow this package to be relocatable to other places than /opt/local/nidas/x86
# rpm --relocate /opt/local/nidas/x86=/usr --relocate /opt/local/nidas/share=/usr/share
%description devel
nc_server headers

%prep
%setup -n nc_server

%build
pwd
scons PREFIX=${RPM_BUILD_ROOT}/usr
 
%install
rm -rf $RPM_BUILD_ROOT
scons PREFIX=${RPM_BUILD_ROOT}/usr install

%post
ldconfig

%clean
rm -rf $RPM_BUILD_ROOT

%files
/usr/bin/nc_server
/usr/bin/nc_sync
/usr/bin/nc_shutdown
/usr/bin/nc_close
/usr/bin/nc_check
/usr/lib/libnc_server_rpc.so.*

%files devel
/usr/include/nc_server_rpc.h
/usr/lib/libnc_server_rpc.so

%changelog
* Mon Jun  7 2010 Gordon Maclean <maclean@ucar.edu> 1.0-1
- original
