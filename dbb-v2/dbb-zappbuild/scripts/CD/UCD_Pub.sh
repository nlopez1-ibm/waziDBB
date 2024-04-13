#!/bin/sh
# mod njl 4/21/24 - dual mode Artifactory or CodeStation for Demos 
. ~/.profile

ucd_version=$1
ucd_Component_Name=$2
MyWorkDir=$3
ArtifactoryMode=$4

buzTool=~/ibm-ucd/agent/bin/buztool.sh
pub=$DBB_HOME/dbb-zappbuild/scripts/UCD/dbb-ucd-packaging.groovy  

artProp=""
if [ -z "$ArtifactoryMode" ]; then
    artStore="UCD CodeStation"     
else
    artStore="jFroj" 
    artProp=" -prop $MyWorkDir/artifactoryProps"
fi

echo "**************************************************************"
echo "**  Started:  UCD_Pub.sh (V2) Pack&Pub on HOST/USER: $(uname -Ia) $USER"
echo "**                           Version/Build_ID:" $ucd_version
echo "**                                 Component:" $ucd_Component_Name 
echo "**                                   workDir:" $MyWorkDir   
echo "**                              BuzTool Path:" $buzTool 
echo "**                          Packaging Script:" $pub 
echo "**                            Artifact Store:" $artStore                   

groovyz $pub  --buztool $buzTool --workDir $MyWorkDir  --component $ucd_Component_Name --versionName $ucd_version $artProp
exit $?
