#
# .login script for ISFS users.  This script should be sourced from the user's
# own .login file.  ISFS, PROJECT have already been set in .cshrc
# Set other environment variables here.
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

if ( ! $?PROJECT ) then
    if ( -f $HOME/default_project ) then
        set dp = `cat $HOME/default_project`
    else if ( -f $ISFS/default_project ) then
        set dp = `cat $ISFS/default_project`
    else
        set dp = (unknown)
    endif
    setenv PROJECT $dp[1]
    if ( $#dp > 1 ) then
        setenv DATASET $dp[2]
    endif
endif

if ($?DATASET) then
    set dataset = $DATASET
else
    set dataset
endif

eval `$ISFS/scripts/echo_csh_env.sh $PROJECT $dataset`

# For BASH scripts
if (! $?BASH_ENV) then
    setenv BASH_ENV $ISFS/scripts/isfs.bashrc
    # echo "BASH_ENV=$BASH_ENV"
endif

unset dataset

# source $ISFS/scripts/set_cdpath.csh

# if ( $?MANPATH ) then
#     if ( "$MANPATH" !~ *$ISFS/doc/man* ) setenv MANPATH ${MANPATH}:$ISFS/doc/man
# else
#     setenv MANPATH /usr/share/man:$ISFS/doc/man
# endif

umask 002

