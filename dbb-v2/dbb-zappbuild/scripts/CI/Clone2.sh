#!/bin/sh
## Demo script git clone v4 (nlopez)
## chgs: - Added support for pull vs full clone for performance
## Testing new dhh hash extract scrip

. ~/.profile
#set -x
workDir=$1 
workSpace=$2
repoUrl=$3
repoRef=$4
depth=$5  


if test ! "$depth" # if depth not passed, do full clone 
    then 
        unset depthStr
        depthMsg="(Not used. Running a full clone)"
        depthHashMsg="(Not needed for full clones)"

    else 
        depthStr=" --depth  $5"
        depthMsg="(for faster cloning)"
        depthHashMsg="" 
        ## get the last successful build hash of this app's collection for shallow cloning 
        ## hard coded values for demo purposes only  
        dbb build-result find-last --build-group poc-app-develop --state 2 --status 0  -t f > dbbHash
        dbbHash=$(grep ':githash:' dbbHash | awk '{print $2}' |awk '{ print substr($0, 0, 7) }' )
        rm dbbHash
        ###
fi 


# Strip any 'refs/heads/...'    
if [[ $repoRef = *"refs/heads/"* ]]; then 
    repoRef=${repoRef##*"refs/heads/"}    
fi   

# for common repos switch to the main app repo's workDir clone before the common
if [ -d $workDir/$workSpace ]; then
   cd $workDir/$workSpace
   gitCmd="git pull  "
  
else 
    mkdir -p $workDir
    cd $workDir
    gitCmd="git clone -b $repoRef $depthStr  $repoUrl "    
fi 
 
echo "**************************************************************"
echo "**     Started:  Clone2.sh v4   HOST/USER: $(uname -Ia)/$USER"
echo "**                               Repo Url:" $repoUrl  
echo "**                               Repo Ref:" $repoRef
echo "**                              workSpace:" $workSpace 
echo "**                          WorkDir (pwd):" $PWD
echo "**                                Git Cmd:" $gitCmd 
echo "**                            Git Version: $(git --version)"
echo "**                            Clone depth:" $depth   $depthMsg
echo "**           DBB last Successful Git Hash:" $dbbHash $depthHashMsg   
echo "**"
 
git config --global  advice.detachedHead false > /dev/null 
$gitCmd   > clone.log  2>&1  
cat clone.log 

if [ -d $workSpace ]; then    cd $workSpace ; fi

echo "\n**"
if test ! "$depth" # if depth is passed check for dbb hash else list top 5 
then    
    echo "Showing the last 5  commits: " 
    git log --graph --oneline --decorate -n 5 > commit.log  2>&1
    cat commit.log  
else
    echo "Showing the last $depth commits: " 
    git log --graph --oneline --decorate -n $depth > commit.log  2>&1
    cat commit.log 

    echo "\n**" 
    git rev-parse -q --verify $dbbHash^{commit} > /dev/null 
    if [ $? -eq 1 ] 
        then
            echo "** ERROR: DBB's last build Git HASH is not found."
            echo "** You may need to adjust the hardcoded clone depth and rerun."
            exit 12   
    fi   
fi     


exit 0