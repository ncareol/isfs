Summary: Package containing system configuration of an ISFS base system.
Name: isfs-base-system
Version: %{gitversion}
Release: %{releasenum}
License: GPL
Group: System Administration
BuildArch: noarch
Source: %{name}-%{version}.tar.gz
Obsoletes: isff-base-system

Requires: chrony
Requires: git subversion
Requires: sudo minicom emacs
Requires: eol-repo-fedora
Requires: netcdf nco
Requires: nidas nidas-buildeol nidas-daq nidas-devel
Requires: nc_server nc_server-devel
Requires: isfs-R
# GNOME stuff
Requires: gnome-tweak-tool dconf-editor

%description
Package containing system configuration of an ISFS base system.

%prep
%setup -n %{name}

%build

%install
rm -rf $RPM_BUILD_ROOT
install -d $RPM_BUILD_ROOT%{_sysconfdir}
cp -r etc/* $RPM_BUILD_ROOT%{_sysconfdir}

%clean
rm -rf $RPM_BUILD_ROOT

%triggerin -- setup

# if ! systemctl is-enabled nfs-server.service; then
#     systemctl enable nfs-server.service
# fi

# Add /usr/local to /etc/exports
# if ! grep -q /usr/local /etc/exports; then
# cat << \EOD >> /etc/exports
# # /usr/local entry added by isff-base-system RPM
# /usr/local 192.168.0.0/16(rw)
# EOD

# systemctl restart nfs-server.service

%triggerin -- initscripts

# update /etc/sysctl.conf to enable sysrq
sed -i '/^kernel.sysrq/s/^[^=]*=[[:space:]]*0.*$/kernel.sysrq = 1/' /etc/sysctl.conf

%triggerin -- chrony

# enable chrony
if ! systemctl is-enabled chronyd.service > /dev/null; then
    echo "enabling chronyd.service"
    systemctl enable chronyd.service
fi

%triggerin -- nc_server

# enable nc_server
if ! systemctl is-enabled nc_server.service > /dev/null; then
    echo "enabling and starting nc_server.service"
    systemctl enable nc_server.service
    systemctl start nc_server.service
fi

%triggerin -- nidas-daq

cf=%{_sysconfdir}/default/nidas-daq
source $cf

# setup systemd for $DAQ_USER so that their services are run at boot
if [ $DAQ_USER ]; then
    loginctl enable-linger $DAQ_USER
fi

%post

%files
/etc/minirc.ttyUSB0

%changelog
