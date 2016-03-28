#!/usr/bin/echo You_must_source
#
# Various bash functions for ISFS
# This script should be source'd, and not exec'd.
#

isfs_env() {
    # args:  [-c] [[project] [dataset]]
    # setup environment for ISFS users.  If passed -c option,
    # then echo "setenv ... ;" statements to set up environment
    # for csh/tcsh.

    # set -x

    local cshell=false

    envset() {
        export $1="$2"
    }
    setenv() {
        export $1="$2"
    }

    local iarg=0
    local dataset=

    while [ $# -gt 0 ]; do
        case $1 in
            -c)
                cshell=true
                envset() {
                    echo "setenv $1 \"$2\";"
                }
                setenv() {
                    echo "setenv $1 \"$2\";"
                }
                ;;
            *)
                if [ $iarg -eq 0 ]; then
                    PROJECT=$1
                else
                    dataset=$1
                fi
                iarg=$(( $iarg + 1 ))
                ;;
        esac
        shift
    done

    export PROJECT

    if [ -z "$PROJECT" ]; then
        echo "environment variable PROJECT not known and not passed to $FUNCNAME bash function"
        return 1
    fi

    envset PROJECT $PROJECT
    [ $dataset ] && envset DATASET $dataset

    local dxml=$ISFS/projects/$PROJECT/ISFS/config/datasets.xml
    if [ -n "$dataset" -a -f $dxml ]; then
        if type -p datasets > /dev/null; then
            if $cshell; then
                datasets -c $dataset $dxml
            else
                eval $(datasets -b $dataset $dxml)
            fi
        fi
    fi

    # Build $path
    echo $PATH | grep -q /opt/nc_server/bin || PATH=$PATH:/opt/nc_server/bin

    # Look for ISFS bin and scripts directories in path.  If they are found
    # leave them in the same position, otherwise add them at the end.
    # Remove $ISFS/projects/$PROJECT/scripts entries of other PROJECTS from path,
    # and add current $ISFS/projects/$PROJECT/scripts.
    local bin=/opt/nidas/bin
    local binpath=$bin
    local scripts=$ISFS/scripts
    local scriptpath=$scripts
    local projectpath=$ISFS/projects/$PROJECT/ISFS/scripts
    [ -d $projectpath ] || projectpath=$ISFS/projects/$PROJECT/ISFF/scripts
    [ -d $projectpath ] || projectpath=$ISFS/projects/$PROJECT/scripts

    local -a pathdirs
    local oldifs=$IFS
    IFS=:
    pathdirs=($PATH)
    IFS=$oldifs

    local -a newpath
    local dir

    for dir in ${pathdirs[*]}; do
        case $dir in
            $bin)
                newpath=(${newpath[*]} $binpath)
                unset binpath
                ;;
            $scripts)
                newpath=(${newpath[*]} $projectpath $scriptpath)
                unset scriptpath
                unset projectpath
                ;;
            $ISFS/projects/*/scripts)
                ;;
            $ISFS/projects/*/ISFS/scripts )
                ;;
            *)
                newpath=(${newpath[*]} $dir)
                ;;
        esac
    done

    newpath=(${newpath[*]} $projectpath $scriptpath $binpath)
    IFS=:
    newpath="${newpath[*]}"
    IFS=$oldifs

    envset PATH $newpath

    [ -f $ISFS/projects/$PROJECT/ISFS/scripts/isfs_env.sh ] &&
        source $ISFS/projects/$PROJECT/ISFS/scripts/isfs_env.sh
    :
}

set_project() {

    local iarg=0
    local arg
    local project
    local dataset
    while [ $# -gt 0 ]; do
        case $1 in
            -c)
                arg=$1
                ;;
            *)
                if [ $iarg -eq 0 ]; then
                    project="$1"
                else
                    dataset="$1"
                fi
                iarg=$(( $iarg + 1 ))
                ;;
        esac
        shift
    done

    if [ -z $project ]; then

        local -a projects=($(get_projects))

        echo "---- Projects ----" 1>&2
        local oldps3=$PS3
        PS3="Choose a project, by number: "
        select project in ${projects[*]}; do
            break
        done
        PS3=$oldps3
        echo "" 1>&2
    fi

    if [ -z $dataset ]; then
        local -a datasets=($(get_datasets $project))

        if [ ${#datasets[*]} -eq 1 ]; then
            dataset=${datasets[0]}
            echo "dataset=$dataset" 1>&2
        elif [ ${#datasets[*]} -gt 0 ]; then
            echo "---- Datasets ----" 1>&2
            local oldps3=$PS3
            PS3="Choose a dataset, by number: "
            select dataset in ${datasets[*]}; do
                break
            done
            PS3=$oldps3
            echo "" 1>&2
        fi
    fi

    isfs_env $arg $project $dataset
    set_cdpath
}

# short-hand
sp () { set_project "$@"; }

set_cdpath() {
    # set -x
    local newpath
    local projpath=$ISFS/projects/${PROJECT:?"PROJECT not set"}
    [ -d $projpath/ISFS ] && projpath=$projpath/ISFS

    local oldifs=$IFS
    local -a dirs
    IFS=:
    dirs=($CDPATH)
    IFS=$oldifs

    for dir in ${dirs[*]}; do
        # Substitute current project
        if [[ $dir == */projects/* ]]; then
            newpath+=${newpath:+:}$projpath
            projpath=
        else
            newpath+=${newpath:+:}$dir
        fi
    done
    [ -n "$projpath" ] && newpath+=${newpath:+:}$projpath
    export CDPATH=${newpath}
    # set +x
}

get_projects() {
    local dir=$ISFS/projects
    [ -d $dir ] || return
    local -a projects=( `find $dir -depth -mindepth 1 -maxdepth 1 -follow \
        \( -type d -o -type l \) -a ! -name CVS -a ! -name .svn -prune -print | sort` )
    # remove leading path names
    projects=(${projects[*]##*/})
    echo ${projects[*]}
}

get_datasets() {

    local project=$1
    if type -p datasets > /dev/null; then
        local dxml=$ISFS/projects/$project/ISFS/config/datasets.xml
        [ -f $dxml ] && datasets -n $dxml
    fi
}
   
