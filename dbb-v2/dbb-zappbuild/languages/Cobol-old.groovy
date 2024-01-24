// nelsons old cobol groovy v4 with cool stuff

@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import com.ibm.dbb.build.*
import groovy.transform.*
import com.ibm.jzos.ZFile
import com.ibm.dbb.build.report.*
import com.ibm.dbb.build.report.records.*
//NJL sample jZos package for WTO Support 
import com.ibm.jzos.MvsConsole
import com.ibm.jzos.WtoConstants


//NJL Special copy of Cobol.groovy for shops needding the CICS and DB2 pre-process steps 
// When the V4 option is not null in app-conf, build-groovy will call this scrip
// Side Note: I dont have access to the v4 lib's so simulating with v6 
//            This ver also includes - pgm lvl overrides, sysdebug "SEP" file and dual mode    
// All changes to the offical code are tagged NJL (NJLopez)

// Parms and other seeting modelled from Allen's CMAN sysout
// Steps (conditionaly executed based on scanner's detection of DB2 and/or CICS source stmts): 
//   1 DB2 PreComp(DSNHPC)   PARM=('HOST(IBMCOB),APOST,APOSTSQL,SOURCE,XREF,VERSION(AUTO)')  
//   2 CICS Trans (DFHECP1$) PARM=('CICS,SOURCE,NOSEQ,COBOL3,NODEBUG')
//   3 Cob        (IGYCRCTL) PARM=(XREF,APOST,OBJECT,ADV,NODYNAM,ARITH(EXTEND),TRUNC(BIN)
//   4 Binder/Link(IEWL)     PARM=('LIST,XREF,MAP,RENT')
// Test case pgm & Results: 
//   DATDEMO = non db2/cics (passed
//   DB2pgm  = db2 batch 	(passed with bind)
//   DATXCICS = cics only 	(passed no CICW Sregi0on yet)
//   DATxCIC2 = cics/db2  	(Todo)
//
// chglog - rename datxcics to datdemo to reuse t6031 cics setup in script deploy demo (njl 2-9-22)
//        - added deploytype cicsload to isCICS syslmod 
//=======================================================================================

// define script properties
@Field BuildProperties props = BuildProperties.getInstance()
@Field def buildUtils= loadScript(new File("${props.zAppBuildDir}/utilities/BuildUtilities.groovy"))
@Field def impactUtils= loadScript(new File("${props.zAppBuildDir}/utilities/ImpactUtilities.groovy"))
@Field def bindUtils= loadScript(new File("${props.zAppBuildDir}/utilities/BindUtilities.groovy"))
@Field RepositoryClient repositoryClient

println("** Building files mapped to ${this.class.getName()}.groovy script   COBv4 (in Simulation mode)")
MvsConsole.wto("dbb-zappbuild Cobol_V4 started ",  
		WtoConstants.ROUTCDE_PROGRAMMER_INFORMATION,
		WtoConstants.DESC_IMPORTANT_INFORMATION_MESSAGES)

// verify required build properties
buildUtils.assertBuildProperties(props.cobol_requiredBuildProperties)

// create language datasets
def langQualifier = "cobol"
buildUtils.createLanguageDatasets(langQualifier)

// sort the build list based on build file rank if provided
List<String> sortedList = buildUtils.sortBuildList(argMap.buildList, 'cobol_fileBuildRank')

if (buildListContainsTests(sortedList)) {
	langQualifier = "cobol_test"
	buildUtils.createLanguageDatasets(langQualifier)
}

// iterate through build list
sortedList.each { buildFile ->
	println "*** _Building file $buildFile  "
	 
		
	
	// Check if this a testcase
	isZUnitTestCase = (props.getFileProperty('cobol_testcase', buildFile).equals('true')) ? true : false

	// copy build file and dependency files to data sets
	String rules = props.getFileProperty('cobol_resolutionRules', buildFile)
	DependencyResolver dependencyResolver = buildUtils.createDependencyResolver(buildFile, rules)
	if(isZUnitTestCase){
		buildUtils.copySourceFiles(buildFile, props.cobol_testcase_srcPDS, null, null)
	}else{
		buildUtils.copySourceFiles(buildFile, props.cobol_srcPDS, props.cobol_cpyPDS, dependencyResolver)
	}
	// create mvs commands
	LogicalFile logicalFile = dependencyResolver.getLogicalFile()
	String member = CopyToPDS.createMemberName(buildFile)
	
		
	// NJL - PGM level cfg override     
    File refreshAppDefaults  = new File(buildUtils.getAbsolutePath(props.application) + "/application-conf/Cobol.properties")
    props.load(refreshAppDefaults)	
    File getPgmOptions   = new File(buildUtils.getAbsolutePath(props.application) + "/application-conf/pgmOptions/${member.toLowerCase()}.properties")
    if(getPgmOptions.exists()){
    	println "** pgmOptions in effect. Using build prop overrides in  ${member.toLowerCase()}.properties"		
    	props.load(getPgmOptions)		
    }
    // 
			
	// Init the main sysprint log file
	File logFile = new File( props.userBuild ? "${props.buildOutDir}/${member}.log" : "${props.buildOutDir}/${member}.cobol.log")
	if (logFile.exists()) logFile.delete()			
		
	
	// NJL - init DB2 preComp & CICS Translator 	
	MVSExec precompiler  = createPreCompileCommand(buildFile, logicalFile, member, logFile)
	MVSExec translator   = createCicsTranslatorCommand(buildFile, logicalFile, member, logFile)
	// 
	
	
	// Init compiler and linkedit
	MVSExec compile  = createCompileCommand(buildFile, logicalFile, member, logFile)
	MVSExec linkEdit = createLinkEditCommand(buildFile, logicalFile, member, logFile)

	
	// Setup the JOB and execute the steps	
	MVSJob job = new MVSJob()
	job.start()
	
	props.error 		= null 
	boolean bindFlag 	= true
	boolean buildError  = false 
	
	println "*Trace isCICS=" + buildUtils.isCICS(logicalFile)
	println "*Trace isSQL=" + buildUtils.isSQL(logicalFile)

	
	// njl - DB2 Precomp	
	if (buildUtils.isSQL(logicalFile))  {
	   	println "** Running DB2 Precomplier Step"	
						
		int pcRC = precompiler.execute()
		if  (pcRC > 4) {
			String errorMsg = "*! The DB2 Precompiler return code ($pcRC) for $buildFile exceeded the maximum return code of 4"
			println(errorMsg)
			props.error = "true"
			bindFlag 	= false
			buildError 	= true
			buildUtils.updateBuildResult(errorMsg:errorMsg,logs:["${member}.log":logFile],client:getRepositoryClient())
	    }
		else {
			//Store db2 bind information as a generic property record in the BuildReport
			String generateDb2BindInfoRecord = props.getFileProperty('generateDb2BindInfoRecord', buildFile)
			if (generateDb2BindInfoRecord.toBoolean() ){
				PropertiesRecord db2BindInfoRecord = buildUtils.generateDb2InfoRecord(buildFile)
				BuildReportFactory.getBuildReport().addRecord(db2BindInfoRecord)
			}
		}	   	
	   	println "** DB2 Precomplier RC=$pcRC\n"
	}		
	//  
		

	// njl - CICS Tranalator  	
	if (!buildError  && buildUtils.isCICS(logicalFile) )  {
	   	println "** Running CICS Translator Step"	
						
		int transRC = translator.execute()
		if  (transRC > 4) {
			String errorMsg = "*! The CICS Translator  return code ($transRC) for $buildFile exceeded the maximum return code of 4"
			println(errorMsg)
			props.error = "true"
			buildError 	= true
			buildUtils.updateBuildResult(errorMsg:errorMsg,logs:["${member}.log":logFile],client:getRepositoryClient())			
	       }
	   	println "** CICS Translator RC=$transRC\n"
	   }		
	//   
		
	
	// compile  the  program
	if (!buildError) {
		println "** Running Compiler Step"	
		int rc = compile.execute()
		int maxRC = props.getFileProperty('cobol_compileMaxRC', buildFile).toInteger()

		if (rc > maxRC) {
			String errorMsg = "*! The compile return code ($rc) for $buildFile exceeded the maximum return code allowed ($maxRC)"
			println(errorMsg)
			props.error = "true"
			buildError 	= true
			buildUtils.updateBuildResult(errorMsg:errorMsg,logs:["${member}.log":logFile],client:getRepositoryClient())
		}
		println "** Compiler RC=$rc\n"
	}
	
	// link  the cobol program
	String needsLinking = props.getFileProperty('cobol_linkEdit', buildFile)
	if (!buildError  && needsLinking.toBoolean() ) {
		println "** Running Linkedit Step"
		rc = linkEdit.execute()
		maxRC = props.getFileProperty('cobol_linkEditMaxRC', buildFile).toInteger()
		if (rc > maxRC) {
			String errorMsg = "*! The link edit return code ($rc) for $buildFile exceeded the maximum return code allowed ($maxRC)"
			println(errorMsg)
			props.error = "true"
			buildError 	= true
			buildUtils.updateBuildResult(errorMsg:errorMsg,logs:["${member}.log":logFile],client:getRepositoryClient())
		}
		println "** Linkedit  RC=$rc\n"
	}
	

	// scan the load module for static calls  
	String scanLoadModule = props.getFileProperty('cobol_scanLoadModule', buildFile)
	if (!buildError  && !props.userBuild && !isZUnitTestCase && scanLoadModule && scanLoadModule.toBoolean() && getRepositoryClient() ) 
		impactUtils.saveStaticLinkDependencies(buildFile, props.linkedit_loadPDS, logicalFile, repositoryClient)			
	

	//perform Db2 Bind only on User Build and perfromBindPackage property
	if (!buildError  && props.userBuild && bindFlag && logicalFile.isSQL() && props.bind_performBindPackage && props.bind_performBindPackage.toBoolean() ) {
		int bindMaxRC = props.getFileProperty('bind_maxRC', buildFile).toInteger()

		// if no  owner is set, use the user.name as package owner
		def owner = ( !props.bind_packageOwner ) ? System.getProperty("user.name") : props.bind_packageOwner

		def (bindRc, bindLogFile) = bindUtils.bindPackage(buildFile, props.cobol_dbrmPDS, props.buildOutDir, props.bind_runIspfConfDir,
				props.bind_db2Location, props.bind_collectionID, owner, props.bind_qualifier, props.verbose && props.verbose.toBoolean());
		
		if ( bindRc > bindMaxRC) {
			String errorMsg = "*! The bind package return code ($bindRc) for $buildFile exceeded the maximum return code allowed ($props.bind_maxRC)"
			println(errorMsg)
			props.error = "true"
			buildError 	= true
			buildUtils.updateBuildResult(errorMsg:errorMsg,logs:["${member}_bind.log":bindLogFile],client:getRepositoryClient())
		}

		//njl - append bind log to the main sysprint log
		
		 bindLog = new File("${props.buildOutDir}/${member}_bind.log")
		println "* DB2 Bind Ec  RC=$bindRc          Log=${props.buildOutDir}/${member}_bind.log" 
		
	}


	//njl Dual mode sample - copy the load module created above to the CICS load lib = (comp/link options for cics and batch assumed to be the same)
	def isDual = BuildProperties.getFileProperty("isDual", buildFile)		
	if (!buildError  &&  isDual ) {			
		println "*! Dual Mode (IEBCOPY) processing in effect for member=$member..." 
		
		//Assmues IEBCOPY is in the linklist. Otherwise add a tasklib dd (aka steplib for groovy)
		MVSExec copyMod = new MVSExec().pgm("IEBCOPY")			
		
		def cntl ="  COPYMOD INDD=BATCH,OUTDD=CICSLOAD \n  SELECT MEMBER=(($member,,R))"
		copyMod.dd(new DDStatement().name("SYSIN").instreamData(cntl))
			
		copyMod.dd(new DDStatement().name("CICSLOAD").dsn(props.cobol_CICSloadPDS).options('shr').output(true).deployType("CICSLOAD"))
		copyMod.dd(new DDStatement().name("BATCH").dsn(props.cobol_loadPDS).options('shr'))
							
		copyMod.dd(new DDStatement().name("SYSPRINT").options(props.cobol_printTempOptions))
		copyMod.copy(new CopyToHFS().ddName("SYSPRINT").file(logFile).hfsEncoding(props.logEncoding).append(true))			
							
		def cpyDual_rc  = copyMod.execute()
		println "*! Batch load was copied to CICS load $props.cobol_CICSloadPDS    rc=$cpyDual_rc"
	}


	// clean up passed DD statements	
	job.stop()
}
// end script

	
	

//********************************************************************
//* Method definitions
//********************************************************************

/*
 * createCobolParms - Builds up the COBOL compiler parameter list from build and file properties
 */
def createCobolParms(String buildFile, LogicalFile logicalFile) {
	def parms = props.getFileProperty('cobol_compileParms', buildFile) ?: ""
	
	//njl commet out  all cob v6 parms for cics or db2   
	//def cics = props.getFileProperty('cobol_compileCICSParms', buildFile) ?: ""
	//def sql = props.getFileProperty('cobol_compileSQLParms', buildFile) ?: ""
	
	def errPrefixOptions = props.getFileProperty('cobol_compileErrorPrefixParms', buildFile) ?: ""
	def compileDebugParms = props.getFileProperty('cobol_compileDebugParms', buildFile)

	//if (buildUtils.isCICS(logicalFile))
	//	parms = "$parms,$cics"

	//if (buildUtils.isSQL(logicalFile))
	//	parms = "$parms,$sql"

	if (props.errPrefix)
		parms = "$parms,$errPrefixOptions"

	// add debug options
	if (props.debug)  {
		parms = "$parms,$compileDebugParms"
	}

	if (parms.startsWith(','))
		parms = parms.drop(1)

	if (props.verbose) println "Cobol compiler parms for $buildFile = $parms"
	return parms
}

/*
 * createCompileCommand - creates a MVSExec command for compiling the COBOL program (buildFile)
 */
def createCompileCommand(String buildFile, LogicalFile logicalFile, String member, File logFile) {
	String parms = createCobolParms(buildFile, logicalFile)
	String compiler = props.getFileProperty('cobol_compiler', buildFile)

	// define the MVSExec command to compile the program
	MVSExec compile = new MVSExec().file(buildFile).pgm(compiler).parm(parms)
	String compilerVer = props.getFileProperty('cobol_compilerVersion', buildFile)
	compile.dd(new DDStatement().name("TASKLIB").dsn(props."SIGYCOMP_$compilerVer").options("shr"))
	if (props.SFELLOAD)
		compile.dd(new DDStatement().dsn(props.SFELLOAD).options("shr"))
	
		
	// njl override sysin based on pgm type.  
	sysinDSN =	"${props.cobol_srcPDS}($member)"
	if (buildUtils.isSQL(logicalFile) ) sysinDSN = "&&DSNHOUT"		
	if (buildUtils.isCICS(logicalFile)) sysinDSN = "&&CSORC" 
	if (isZUnitTestCase)				sysinDSN = "${props.cobol_testcase_srcPDS}($member)"				
	compile.dd(new DDStatement().name("SYSIN").dsn(sysinDSN).options('shr').report(true))
		
	// Write SYSLIN to temporary dataset if performing link edit or to physical dataset
	String doLinkEdit     = props.getFileProperty('cobol_linkEdit', buildFile)
	String linkEditStream = props.getFileProperty('cobol_linkEditStream', buildFile)
	String linkDebugExit  = props.getFileProperty('cobol_linkDebugExit', buildFile)

	if (props.debug && linkDebugExit && doLinkEdit.toBoolean()){
		compile.dd(new DDStatement().name("SYSLIN").dsn("${props.cobol_objPDS}($member)").options('shr').output(true))
	} else if (doLinkEdit && doLinkEdit.toBoolean() && ( !linkEditStream || linkEditStream.isEmpty())) {
		compile.dd(new DDStatement().name("SYSLIN").dsn("&&TEMPOBJ").options(props.cobol_tempOptions).pass(true))
	} else {
		compile.dd(new DDStatement().name("SYSLIN").dsn("${props.cobol_objPDS}($member)").options('shr').output(true))
	}

	// add a syslib to the compile command with optional bms copybook libs  
	compile.dd(new DDStatement().name("SYSLIB").dsn(props.cobol_cpyPDS).options("shr"))
	compile.dd(new DDStatement().dsn(props.SDFHCOB).options("shr"))
	compile.dd(new DDStatement().dsn(props.SCSQCOBC).options("shr"))
	
	if (props.bms_cpyPDS && ZFile.dsExists("'${props.bms_cpyPDS}'"))
		compile.dd(new DDStatement().dsn(props.bms_cpyPDS).options("shr"))
	if(props.team)
		compile.dd(new DDStatement().dsn(props.cobol_BMS_PDS).options("shr"))
		
	// add custom syslib concatenation
	def compileSyslibConcatenation = props.getFileProperty('cobol_compileSyslibConcatenation', buildFile) ?: ""
	if (compileSyslibConcatenation) {
		def String[] syslibDatasets = compileSyslibConcatenation.split(',');
		for (String syslibDataset : syslibDatasets )
		compile.dd(new DDStatement().dsn(syslibDataset).options("shr"))
	}
		
		
	// add additional zunit libraries
	if (isZUnitTestCase)
		compile.dd(new DDStatement().dsn(props.SBZUSAMP).options("shr"))

	// add IDz User Build Error Feedback DDs
	if (props.errPrefix) {
		compile.dd(new DDStatement().name("SYSADATA").options("DUMMY"))
		// SYSXMLSD.XML suffix is mandatory for IDZ/ZOD to populate remote error list
		compile.dd(new DDStatement().name("SYSXMLSD").dsn("${props.hlq}.${props.errPrefix}.SYSXMLSD.XML").options(props.cobol_compileErrorFeedbackXmlOptions))
	}

	compile.dd(new DDStatement().name("SYSPRINT").options(props.cobol_printTempOptions))
	compile.copy(new CopyToHFS().ddName("SYSPRINT").file(logFile).hfsEncoding(props.logEncoding).append(true))

	compile.dd(new DDStatement().name("SYSMDECK").options(props.cobol_tempOptions))
	(1..17).toList().each { num ->
		compile.dd(new DDStatement().name("SYSUT$num").options(props.cobol_tempOptions))
	}

	
	// NJL added support for COB4 sep debug file 
	// see build-conf/Cobol.properties for dd info and updates to build-conf/Utilities/BuildUtilties.groovy for debugDataset alloc 
	compile.dd(new DDStatement().name("SYSDEBUG").dsn("${props.cobol_sysdebugPDS}($member)").options("shr"))

	
	return compile
}


/*
 * createLinkEditCommand - creates a MVSExec xommand for link editing the COBOL object module produced by the compile
 */
def createLinkEditCommand(String buildFile, LogicalFile logicalFile, String member, File logFile) {
	String parms = props.getFileProperty('cobol_linkEditParms', buildFile)
	String linker = props.getFileProperty('cobol_linkEditor', buildFile)
	String linkEditStream = props.getFileProperty('cobol_linkEditStream', buildFile)
	String linkDebugExit = props.getFileProperty('cobol_linkDebugExit', buildFile)

	// define the MVSExec command to link edit the program
	MVSExec linkedit = new MVSExec().file(buildFile).pgm(linker).parm(parms)

	// Create a physical link card
	if ( (linkEditStream) || (props.debug && linkDebugExit!= null)) {
		def langQualifier = "linkedit"
		buildUtils.createLanguageDatasets(langQualifier)
		def lnkFile = new File("${props.buildOutDir}/linkCard.lnk")
		if (lnkFile.exists())
			lnkFile.delete()

		if 	(linkEditStream)
			lnkFile << "  " + linkEditStream.replace("\\n","\n").replace('@{member}',member)
		else
			lnkFile << "  " + linkDebugExit.replace("\\n","\n").replace('@{member}',member)

		if (props.verbose)
			println("Copying ${props.buildOutDir}/linkCard.lnk to ${props.linkedit_srcPDS}($member)")
		new CopyToPDS().file(lnkFile).dataset(props.linkedit_srcPDS).member(member).execute()
		// Alloc SYSLIN
		linkedit.dd(new DDStatement().name("SYSLIN").dsn("${props.linkedit_srcPDS}($member)").options("shr"))
		// add the obj DD
		linkedit.dd(new DDStatement().name("OBJECT").dsn("${props.cobol_objPDS}($member)").options('shr'))

	} else { // no debug && no link card
		// Use &&TEMP from Compile
	}

	// add DD statements to the linkedit command
	String deployType = buildUtils.getDeployType("cobol", buildFile, logicalFile)
	

	//njl mod extend support for distinct loads 
	if (buildUtils.isCICS(logicalFile))	deployType = 'CICSLOAD'
	if (isZUnitTestCase) 				deployType = 'ZUNIT-TESTCASE'
	if (deployType == null) 			deployType = 'LOAD'					
			
			
	// convert the old 'if' logic to a switch for readable and other other subsys like ims ...
	// note the new load libs need to be allocated see 
	switch(deployType) {
  		case "CICSLOAD":
    		loadPDS= "${props.cobol_CICSloadPDS}($member)"
    		break
  		case "ZUNIT-TESTCASE":
    		loadPDS= "${props.cobol_testcase_loadPDS}($member)"
    		break
  		case "LOAD":
    		loadPDS= "${props.cobol_loadPDS}($member)"
    		break
		default:
    		loadPDS= "${props.cobol_loadPDS}($member)"
			println "\n**! WARNING: default deployType of $deployType mapped to syslmod DD of $loadPDS"

    		break		
	}

    linkedit.dd(new DDStatement().name("SYSLMOD").dsn(loadPDS).options('shr').output(true).deployType(deployType) )
 

	linkedit.dd(new DDStatement().name("SYSPRINT").options(props.cobol_printTempOptions))
	linkedit.dd(new DDStatement().name("SYSUT1").options(props.cobol_tempOptions))

	// add RESLIB if needed
	if ( props.RESLIB ) {
		linkedit.dd(new DDStatement().name("RESLIB").dsn(props.RESLIB).options("shr"))
	}

	// add a syslib to the compile command with other optional concatenations
	linkedit.dd(new DDStatement().name("SYSLIB").dsn(props.cobol_objPDS).options("shr"))
	
	println "PATCH: njl CG mod syslib with local user load for static call resolution"
	linkedit.dd(new DDStatement().name().dsn(props.cobol_loadPDS).options("shr"))

	
	
	// add custom concatenation
	def linkEditSyslibConcatenation = props.getFileProperty('cobol_linkEditSyslibConcatenation', buildFile) ?: ""
	if (linkEditSyslibConcatenation) {
		def String[] syslibDatasets = linkEditSyslibConcatenation.split(',');
		for (String syslibDataset : syslibDatasets )
		linkedit.dd(new DDStatement().dsn(syslibDataset).options("shr"))
	}
	linkedit.dd(new DDStatement().dsn(props.SCEELKED).options("shr"))

	// Add Debug Dataset to find the debug exit to SYSLIB
	if (props.debug && props.SEQAMOD)
		linkedit.dd(new DDStatement().dsn(props.SEQAMOD).options("shr"))

	if (buildUtils.isCICS(logicalFile))
		linkedit.dd(new DDStatement().dsn(props.SDFHLOAD).options("shr"))
	
	if (buildUtils.isSQL(logicalFile))
		linkedit.dd(new DDStatement().dsn(props.SDSNLOAD).options("shr"))

	String isMQ = props.getFileProperty('cobol_isMQ', buildFile)
	if (isMQ && isMQ.toBoolean())
		linkedit.dd(new DDStatement().dsn(props.SCSQLOAD).options("shr"))

	// add a copy command to the linkedit command to append the SYSPRINT from the temporary dataset to the HFS log file
	linkedit.copy(new CopyToHFS().ddName("SYSPRINT").file(logFile).hfsEncoding(props.logEncoding).append(true))
		

	return linkedit
}


def getRepositoryClient() {
	if (!repositoryClient && props."dbb.RepositoryClient.url")
		repositoryClient = new RepositoryClient().forceSSLTrusted(true)

	return repositoryClient
}

boolean buildListContainsTests(List<String> buildList) {
	boolean containsZUnitTestCase = buildList.find { buildFile -> props.getFileProperty('cobol_testcase', buildFile).equals('true')}
	return containsZUnitTestCase ? true : false
}


// njl - new DB2 Precomp
def createPreCompileCommand(String buildFile, LogicalFile logicalFile, String member, File logFile) {
	String parms        = props.getFileProperty('cobol_compileSQLParms', buildFile) ?: ""
	MVSExec precompiler = new MVSExec().file(buildFile).pgm('DSNHPC').parm(parms)
	precompiler.dd(new DDStatement().name("TASKLIB").dsn(props."SDSNLOAD").options("shr"))
		
	// input Source code   
	precompiler.dd(new DDStatement().name("SYSIN").dsn("${props.cobol_srcPDS}($member)").options('shr').report(true))

	// input syslibs    TODO review/add  the full syslib dataset concatention like DCLGENs    
	precompiler.dd(new DDStatement().name("SYSLIB").dsn(props.cobol_cpyPDS).options("shr"))	
    // sample concat to syslib below  
	// precompiler.dd(new DDStatement().dsn(???).options("shr"))
        
	// output precompiled source -  using Allens DCB UNIT=SYSDA,SPACE=(CYL,(1,1))     
    precompiler.dd(new DDStatement().name("SYSCIN").dsn("&&DSNHOUT").options("cyl space(1,3) unit(vio) new").pass(true))
    
     
    // NJL - output DBRM  with multi region support see notes in pgmOptions file db2pgm      
    dbrmDeployType = "$props.cobol_dbrmDeployType"
    precompiler.dd(new DDStatement().name("DBRMLIB").dsn("$props.cobol_dbrmPDS($member)").options('shr').output(true).deployType(dbrmDeployType))
 		
    // work files
	precompiler.dd(new DDStatement().name("SYSUT1").options(props.cobol_tempOptions))
	precompiler.dd(new DDStatement().name("SYSUT2").options(props.cobol_tempOptions))	
	precompiler.dd(new DDStatement().name("SYSPRINT").options(props.cobol_printTempOptions))
	precompiler.copy(new CopyToHFS().ddName("SYSPRINT").file(logFile).hfsEncoding(props.logEncoding).append(true))	   
	
	return precompiler
}


//njl - CICS Translator 
def createCicsTranslatorCommand(String buildFile, LogicalFile logicalFile, String member, File logFile) {
	String parms        = props.getFileProperty('cobol_compileCICSParms', buildFile) ?: ""		
	MVSExec translator  = new MVSExec().file(buildFile).pgm('DFHECP1$').parm(parms)	 
	translator.dd(new DDStatement().name("TASKLIB").dsn(props."SDFHLOAD").options("shr"))
			
	// input Source code  
	sysinDSN =	"${props.cobol_srcPDS}($member)"
	if (buildUtils.isSQL(logicalFile) ) sysinDSN = "&&DSNHOUT"
	translator.dd(new DDStatement().name("SYSIN").dsn(sysinDSN).options('shr').report(true))
	    
	// output translated source     
    translator.dd(new DDStatement().name("SYSPUNCH").dsn("&&CSORC").options("cyl space(1,3) unit(vio) new").pass(true))
	
    translator.dd(new DDStatement().name("SYSPRINT").options("cyl space(1,3) recfm(f,b,m) LRECL(121) unit(vio) new"))
    translator.copy(new CopyToHFS().ddName("SYSPRINT").file(logFile).hfsEncoding(props.logEncoding).append(true))	   

	return translator
}			
