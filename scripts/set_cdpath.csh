#!/usr/bin/echo You_must_source
#
# script which sets cdpath for ISFF users.
#

if ( $?PROJECT ) then
    if (-d $ISFS/projects/$PROJECT/ISFS) then
        set newpath = $ISFS/projects/$PROJECT/ISFS
    else if (-d $ISFS/projects/$PROJECT/ISFF) then
        set newpath = $ISFS/projects/$PROJECT/ISFF
    else
        set newpath = $ISFF/projects/$PROJECT
    endif

    if ( ! $?cdpath) set cdpath
    foreach dir ($cdpath)
        # Substitute current project
        if ( "$dir" !~ $ISFF/projects/* ) then
            set newpath=($newpath $dir)
        endif
    end
    set cdpath = ($newpath)
    unset dir newpath
endif
