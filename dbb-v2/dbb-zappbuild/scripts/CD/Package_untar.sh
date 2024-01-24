#!/bin/sh
##  untar a package  
. ~/.profile

workDir=$1
tar=$2
echo "*** Package_untar.sh   unPackage Tar file ***"
echo "                     Package:$tar" 
echo "                Into workDir:$workDir "

cd $workDir
tar -xvf $tar
rc=$?
 
if [ "$rc" -ne "0" ]; then
 echo "unTar Error. Check the log for details. RC=$rc"
 exit $rc
fi
