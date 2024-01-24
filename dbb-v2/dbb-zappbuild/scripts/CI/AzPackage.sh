# Sample script to package a, Azure DBB Build  for staging into artifactory
#export _BPXK_AUTOCVT=ON;export _CEE_RUNOPTS="FILETAG(AUTOCVT,AUTOTAG) POSIX(ON)";export _TAG_REDIR_ERR=txt;export _TAG_REDIR_IN=txt;export _TAG_REDIR_OUT=txt                                                       
export JAVA_HOME=/usr/lpp/java/J8.0_64;export DBB_HOME=/var/dbb100FIX/usr/lpp/IBM/dbb;export GROOVY_HOME=$DBB_HOME/groovy-2.4.12   
export PATH=$JAVA_HOME/bin:$GROOVY_HOME/bin:$DBB_HOME/bin:$PATH 

  
groovyz $HOME/Azure-zAppBuild/package.groovy\
  -w $HOME/Azure/logs/build.azv_$1\
  -a AzDBB\
  -v $1\
  -s gitSourceUrl-TBD\
  -g git@github.ibm.com:Nelson-Lopez1/a-dummy-repo.git\
  -x gitSourceBranch-TBD\
  -y gitBuildBranch-TBD\
  -b 12345678\
  -n P092259\
  -u ARTIFACTORY.COM
   
  
  
