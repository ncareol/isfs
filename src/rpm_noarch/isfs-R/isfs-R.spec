Summary: Setup for ISFS R users
Name: isfs-R
Version: %{gitversion}
Release: %{releasenum}
License: GPL
Group: System Environment/Daemons
Url: http://www.eol.ucar.edu/
Packager: Gordon Maclean <maclean@ucar.edu>
Vendor: UCAR
BuildArch: noarch
Requires: R fftw-devel netcdf-devel GeographicLib-devel
# For BUILD_GROUP in /etc/default/nidas-build
Requires: nidas-build

Source: %{name}-%{version}.tar.gz

%description
Setup for ISFS R users

%prep
%setup -n %{name}

%build

%install

# We could RHOME=$(R RHOME), but that would require R on the build system
# if [ "$RHOME" != %{_libdir}/R ]; then
#     echo "Expected RHOME to be %{_libdir}/R"
# fi

%triggerin -- nidas-build R

# Create R library directory on /usr/local. It will be writeable
# by non-root users, so they can install packages.
rslib=/usr/local/lib/R 
[ -d $rslib/site-library ] || mkdir -p $rslib/site-library

rlog=/tmp/isfs-R-install.log
echo "Doing a build/install of R packages. This will take a while."
echo "Log file is $rlog"
# install some R packages into site library
R --vanilla --slave << EOD >& $rlog
# CRAN and EOL repos
repos=c("http://cran.us.r-project.org","https://www.eol.ucar.edu/software/R")
pkgs=c("eolts", "isfs", "eolsonde")
cat("Installing:",paste(pkgs,sep=","),"\n")
install.packages(pkgs,lib=.Library.site[1],repos=repos)
EOD

# Add a section to /usr/lib64/R/etc/Rprofile.site for ISFS users
cf=%{_libdir}/R/etc/Rprofile.site
if ! grep -qF ISFS $cf 2> /dev/null; then
    cat << \EOD >> $cf
local({
    # Added by %{name} RPM
    # Initialize things for ISFS (aka ISFF) users
    isfs <- Sys.getenv("ISFS",unset=NA)
    if (!is.na(isfs)) {
        # If $ISFS/scripts/Rprofile.isfs exists, source it.
        isfsprofile <- file.path(isfs,"scripts","Rprofile.isfs")

        if (file.exists(isfsprofile)) {
            cat(paste("doing: source(\"",isfsprofile,"\")\n",sep=""))
            source(isfsprofile)
        }
    }
})
EOD
fi

# Make R site library writeable by BUILD_GROUP from nidas-build
cf=/etc/default/nidas-build
if [ -f $cf ]; then
    source $cf
    if [ $BUILD_GROUP ]; then
        chgrp -R $BUILD_GROUP $rslib
        chmod -R g+ws $rslib
    fi
fi

%clean
rm -rf $RPM_BUILD_ROOT

%files

%changelog
