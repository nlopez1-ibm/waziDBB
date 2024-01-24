echo off
REM Desc: This is a sample windows cli ssh script to use with the  poc-workspace repo  
REM Purpose: Demo CI/CD with or without an orchastrator
REM Author:  Nlopez  2022-v2
REM Repo:    git clone git@github.com:nlopez1-ibm/dbb-zappbuild.git
REM CLI:     C:/Users/NelsonLopez/git/dbb-zappbuild/scripts/Win_launcher/ssh_RunBook.bat
REM Chglog:  + Tested Clone vs Pull (passed)
REm          + Tested CI build and pub to arty (non-ucd) with scripted cd (passed)
REM          + Tested non-ucd cd on  t6031 cics and DB2  (Passed)
REM            NOTE: T6031 openssl is at 6.4 and GH expect 7.2. Cant clone for now!!!
REM          + Tested VMware UCD/codeStation build/pub/deploy (passed) 
REM          + Tested DB2 bind and CICS newcopy for tran DATDEMO on zDT (passed)
REM          + Tested with DB2 parms in the UCD shiplist for a CD bind (passed)
REM          + Tested DB2 parms at the pmg level (my ver) - (passed)
REM          + WIP  add the CI/CD scripts to Azure ... 
REM          + WIP testing cpp builds with .h  

cls
echo Windows SSH Runbook - CI/CD with DBB on Z (NLopez - v2) -  %DATE% %TIME%



@REM Init +++++++++++++++++++++++++++
	set /a buildID=%random% %%10000 +1
	
	REM ECHO HARDCODE FOR UNIT TESTING WITH PRIOR RUN
	REM set buildID=1600
	REM goto UCD_Pub


REM Section for various env vars
	set appWorkSpace=poc-workspace
	set appRepoUrl=git@github.com:nlopez1-ibm/%appWorkSpace%.git
	set appName=poc-app
	set appRef=develop

	set commonWorkSpace=common
	set commonRepoUrl=git@github.com:nlopez1-ibm/%commonWorkSpace%.git
	set commonRef=main

	set scriptsHome=tmp/dbb-zappbuild/scripts

	set zBuildHost=nlopez@zos.dev -p 2022
	set CI_workDir=/u/nlopez/tmp/ssh_RunBook/CI


	@rem deploy host is TVT6031 - CHECK VPN/FIREWALL BEFORE RUNNING
	@rem note tvt6031 has some weried clone bug for zappbuild   - todo 

	set zDeployHost=nlopez@tvt6031.svl.ibm.com
	set CD_workDir=/u/nlopez/tmp/ssh_RunBook/CD

REM ??? Where are the latest CD Scripts??/


REM ++++++++++++++++++++++++++++++


@REM Section to Init build scripts  
@REM ++++++++++++++++++++++++++++++
	@REM set initHost=n
	@REM set /p initHost="Enter 'y' to re-pull changes to the build scripts on %zBuildHost%  --> "	
	@REM if  %initHost%==n goto clone 

	:init 
		@rem @echo Refreshing scipts  on %zBuildHost% and %zDeployHost% ...		
		@rem @echo Refreshing scipts  on %zBuildHost%  
		ssh %zBuildHost%  rm -rf tmp/dbb-zappbuild
		ssh %zBuildHost%  . ./.profile;   git -C tmp clone git@github.com:nlopez1-ibm/dbb-zappbuild.git 
		ssh %zBuildHost%  chmod -R +xxx tmp/dbb-zappbuild

		@rem WIP - had issues cloning there... defer
		@rem ssh %zDeployHost%  rm -rf tmp/dbb-zappbuild
		@rem ssh %zDeployHost%  . ./.profile;   git -C tmp clone git@github.com:nlopez1-ibm/dbb-zappbuild.git 
		@rem ssh %zDeployHost%  chmod -R +xxx tmp/dbb-zappbuild




REM Section for unit testing this script:
@REM ++++++++++++++++++++++++++++++
	@REM echo Unit Test enabled
	@REM goto UCD_Pub







echo on
@REM +++++++++++++++++++++++++++++++
@REM Section for CI Build Steps: 
@REM ++++++++++++++++++++++++++++++
	
	:clone
		@echo Cloning source ...		
		@REM CI Job Steps Pull or Clone the main app repo and the common repo:
	    ssh %zBuildHost% %scriptsHome%/CI/Clone2.sh    %CI_workDir%                 %appWorkSpace%    %appRepoUrl%    %appRef%
	    @IF %ERRORLEVEL% NEQ 0 GOTO trapme

	    ssh %zBuildHost% %scriptsHome%/CI/Clone2.sh    %CI_workDir%/%appWorkSpace%  %commonWorkSpace% %commonRepoUrl% %commonRef%
	    @IF %ERRORLEVEL% NEQ 0 GOTO trapme
	
	:build
		@REM Run DBB in impact mode passing a buildID to the buildReport. Build output is at the workspace root level.
		@echo Running DBB Build ... hardcoded pgm name ... for testing 
	    
		@REM ssh %zBuildHost% %scriptsHome%/CI/Build.sh     %CI_workDir% %appWorkSpace%   %appName%   --impactBuild -buildID %buildID%

	    @REM ssh %zBuildHost% %scriptsHome%/CI/Build.sh    %CI_workDir% %appWorkSpace%  %appName% poc-app/bms/datmapm.bms

	    ssh %zBuildHost% %scriptsHome%/CI/Build.sh    %CI_workDir% %appWorkSpace%  %appName%  -buildID %buildID% poc-app/cpp/cppdemo.cpp
		@rem ssh %zBuildHost% %scriptsHome%/CI/Build.sh    %CI_workDir% %appWorkSpace%  %appName%  -buildID %buildID% poc-app/cobol/datdemo.cbl

		@IF %ERRORLEVEL% NEQ 0 GOTO trapme



set useUCD=y
set /p useUCD=" Press Enter to use UCD or 'N' for nonUCD Pub/Deploy process or cntl-c to kii now --> " 
if  %useUCD%==y goto UCD_Pub 

@REM +++++++++++++++++++++++++++++++
@REM Section for Scripted CI Pub: 
@REM ++++++++++++++++++++++++++++++		
	@echo Running scripted Pub and Deploy scripts - non-UCD using Artifactory
	:package
		@REM Package the DBB output artifacts (loads, dbrm ...) into a local tar file
	    ssh %zBuildHost% %scriptsHome%/CI/Package_Create.sh  %CI_workDir% %appWorkSpace%  %appName%
	
	:publish
		@REM Publish the new tar into artifactory (Versions are not created in this sample)
	    ssh %zBuildHost% %scriptsHome%/CI/Package_Publish.sh  %CI_workDir%/%appName%.tar
	    @IF %ERRORLEVEL% NEQ 0 GOTO trapme


@REM ++++++++++++++++++++++++++
@REM Section for Scripted CD:  
@REM ++++++++++++++++++++++++++
	:downLoadPackage
		@REM find the published package 
    	ssh %zDeployHost% %scriptsHome%/CD/Package_Download.sh   %CI_workDir%/%appworkSpace%/%appName%.tar %CD_workDir%
    	@IF %ERRORLEVEL% NEQ 0 GOTO trapme

	:unTarPackage
		ssh %zDeployHost% %scriptsHome%/CD/Package_untar.sh     %CD_workDir% mypackage.tar


	:deployPackage 		
		ssh %zDeployHost% %scriptsHome%/CD/Deploy.sh     %CD_workDir%
		

	:testDeployment
		@REM WIP ---
		@goto EXITOK
	    @REM ssh %zDeployHost% %scriptsHome%/?
		
@REM ++++++++++++++++++++++++++
@goto EXITOK



@REM +++++++++++++++++++++++++++++++
@REM Section for UCD Pub and Deploy: 
@REM ++++++++++++++++++++++++++++++	
:UCD_Pub
	@echo Running UCD Package script (BuzTool)...
    REM ssh %zBuildHost% . ./.profile;  groovyz /u/nlopez/%scriptsHome%/UCD/PackageBuildOutputs.groovy  --verbose -t mypackage.tar -w %CI_workDir%/%appWorkSpace% 
	ssh %zBuildHost% . ./.profile;   groovyz /u/nlopez/%scriptsHome%/UCD/dbb-ucd-packaging.groovy --buztool /u/nlopez/ucd-agent/bin/buztool.sh --workDir %CI_workDir%/%appWorkSpace%  --component poc-app --versionName %buildID%
	REM groovyz $HOME/dbb-ucd-packaging.groovy -b $buzTool -w $artifacts -c $ucd_Component_Name -v $ucd_version 
	
	REM @echo Running UCD's buztool  ... wip

	echo UCD WIP
@REM ++++++++++++++++++++++++++
@goto EXITOK



@REM ++++++++++++++++++++++++++
:trapme
@echo Error trapped RC=%ERRORLEVEL%
:EXITOK
@echo Done!
@pause