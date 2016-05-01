
#
# .profile for ISFS users.  This script should be '.'d from the user's
# own .profile file.  ISFS, PROJECT may have already been set.
# Set other environment variables here.
#
#
umask 002

[ $ISFF ] || export ISFF=$ISFS
[ $ISFS ] || export ISFS=$ISFF

if [ -z "$PROJECT" ]; then
    if [ -f $HOME/default_project ]; then
        dp=($(<$HOME/default_project))
    else
        dp=($(<$ISFS/default_project))
    fi
    PROJECT=${dp[0]}
    [ ${#dp[*]} -gt 1 ] && DATASET=${dp[1]}
fi

# source isfs_functions.sh if can't find isfs_env
declare -F isfs_env > /dev/null || source $ISFS/scripts/isfs_functions.sh

isfs_env $PROJECT $DATASET

