# Sample script to push a zar to artifactory                                                      
export JAVA_HOME=/usr/lpp/java/J8.0_64;export DBB_HOME=/var/dbb100FIX/usr/lpp/IBM/dbb;export GROOVY_HOME=$DBB_HOME/groovy-2.4.12   
export MYROCKET=$HOME/rocket/bin
export PATH=$JAVA_HOME/bin:$MYROCKET:$GROOVY_HOME/bin:$DBB_HOME/bin:$PATH 

groovyz $HOME/Azure-zAppBuild/publish.groovy $1
  
 