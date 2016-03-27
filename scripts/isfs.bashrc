#
# .bashrc script for ISFS users.  This script should be sourced from the user's
# own .bashrc file.
#
# set CDPATH for ISFS users.
#

if [ "${ISFS+set}" = set ]; then
	export CDPATH=.:$ISFS/projects/$PROJECT/ISFS
	source $ISFS/scripts/isfs_functions.sh
fi

if [ "$PS1" ]; then     # are we interactive 
     # If cdspell is set, minor errors in the spelling of a directory 
     # component in a cd command will be corrected.
     # shopt -s cdspell 
     alias h=history 
     alias cp="cp -i" 
     alias mv="mv -i" 
     alias rm="rm -i" 
     vienv() { 
         export EDITOR=vi 
         bind -m vi 
         set -o vi 
     } 
     emacsenv() { 
         export EDITOR=emacs 
         bind -m emacs 
         set -o emacs 
     } 
     geditenv() { 
         export EDITOR=gedit 
         bind -m emacs 
         set -o emacs 
     } 

     vienv 
     # emacsenv 
     alias scons="nice scons -j 4" 
     alias lm="ls -lt | more" 
     alias dum="du -sk | more" 
     alias psm="ps -adltf | more" 
     alias ed="emacs -nw" 

     # DSM aliases 
     alias ds="data_stats" 
     alias md="mote_dump $*" 
fi 
