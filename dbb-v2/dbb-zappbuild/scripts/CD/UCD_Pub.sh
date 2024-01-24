#!/bin/sh
. ~/.profile

ucd_version=$1
ucd_Component_Name=$2
MyWorkDir=$3
artifacts=buildReport.json

buzTool=~/ibm-ucd/agent/bin/buztool.sh
pub=$DBB_HOME/dbb-zappbuild/scripts/UCD/dbb-ucd-packaging.groovy  


echo "**************************************************************"
echo "**  Started:  UCD_Pub.sh (V2) Pack&Pub on HOST/USER: $(uname -Ia) $USER"
echo "**                           Version/Build_ID:" $ucd_version
echo "**                                 Component:" $ucd_Component_Name 
echo "**                                   workDir:" $MyWorkDir   
echo "**                         DBB Artifact Path:" $artifacts
echo "**                              BuzTool Path:" $buzTool 
echo "**                          Packaging Script:" $pub 

groovyz $pub  --buztool $buzTool --workDir $MyWorkDir  --component $ucd_Component_Name --versionName $ucd_version
