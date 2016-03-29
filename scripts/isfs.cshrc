#
# .cshrc script for ISFS users.  This script should be sourced from the user's
# own .cshrc file.
#

if (! $?ISFS) then
    if ($?ISFF) then     # old env var
        setenv ISFS $ISFF
    else if (-d /net/isf/isff) then
        setenv ISFS /net/isf/isff
        setenv ISFF $ISFS
    else if (-d /usr/local/isfs) then
        setenv ISFS /usr/local/isfs
        setenv ISFF $ISFS
    else if (-d /home/isfs) then
        setenv ISFS /home/isfs
        setenv ISFF $ISFS
    endif
endif

# Define useful ISFS aliases

alias set_project 'source $ISFS/scripts/set_project.csh \!*'
alias sp set_project
alias set_env 'eval `$ISFS/scripts/echo_csh_env.sh \!*`'
# alias set_ingest source $ISFS/scripts/set_ingest.csh
alias set_cdpath source $ISFS/scripts/set_cdpath.csh

#
# csh variables
#

if (-f $ISFS/scripts/set_cdpath.csh) then
    source $ISFS/scripts/set_cdpath.csh
endif

