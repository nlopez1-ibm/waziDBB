# New version of packing script (2023) for Azure CD with plum 
. ~/.profile
package=$DBB_HOME/dbb-zappbuild/scripts/utilities/PackageBuildOutputs.groovy

echo "**************************************************************"
echo "**     Started: Package-Create.sh on HOST/USER: $(uname -Ia) $USER"
echo "**                             WorkDir:" $1
echo "**                      Package Script:" $package
 
groovyz $package --workDir $1 -ae -t package.tar
