#!/bin/sh

# Update the contributed packages in R

slib=$(R --vanilla --slave -e 'cat(.Library.site[1])' )

echo "R site library on this system is $slib"


if ! [ -d $slib -a -w $slib ]; then
    echo "You don't have write access to $slib"
    exit 1
fi

eolrepo="https://www.eol.ucar.edu/software/R"
cranrepo="http://cran.us.r-project.org"

echo "Updating packages in $slib from $eolrep and $cranrepo"

echo "If it fails with permisson problems, do:
sudo chgrp eol -R $slib
sudo chmod g+w -R $slib
"

echo 

echo "To update R and its base packages, do 
sudo dnf upgrade R
"

echo 

pkgs="
akima
RNetCDF
maps
mapdata
mapproj
splusTimeDate
splusTimeSeries
quantreg
SparseM
digest
memoise
MethComp
gWidgets2
gWidgets2RGtk2
RGtk2
gWidgets2tcltk
gWidgets
gWidgetstcltk
rgl
Rcpp
RUnit
TideHarmonics
"

# convert to R form:  c("a","b","c")
pkgs='c("'$(echo $pkgs | sed 's/ /","/g')'")'

R --vanilla --slave << EOD
update.packages(lib.loc=.Library.site[1], ask=FALSE,repos="$eolrepo")
pkgs = $pkgs
repos=c("http://cran.us.r-project.org")
update.packages(lib.loc=.Library.site[1],oldPkgs=pkgs,repos="$cranrepo",ask=FALSE)
EOD

