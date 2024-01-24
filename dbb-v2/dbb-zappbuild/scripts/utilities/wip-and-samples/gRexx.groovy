/* DBB Groovy Class to demo how to convert a file with Rexx using DBB's TSOExec  api (NLOPEZ)   
 *  
 *  run under GROOVYZ a rexx exec - 
 *   - req0  	to convert cobol cldoe with changeman "-INC  xxx" in col 1 to normal cobol "COPY xxx" in col 8
 *  			uses 2 pds's and a rexx exec on MVS
 *  			PDS DD=in  source with potential -INC
 *  			PDS DD=out converted members PDS (prealloacted on 3.2) 
 *  			RC=0 nothing found 
 *  			RC=2 conversions made and output to DD=out      
 *  			In zDT run groovyz2 $PWD/this_script    cd to the right place first for pwd to work
 *  			this is the non auto daemon becuase it grapb the wrong userid!!
 * 
 * NOTE: 2023 wazi chgs - convert to just running a TSO CMD in wazi 
 *                      - need to precreate  /var/ispf/WORKAREA
 *                      - copy section below to /etc/ispf/ISPF.conf
 *        Req1          - alloc Log in uss and have TSO Rexx write to it  (passed)
 *        Req2          - can we concat instream and  pds mem?   
 */   
import com.ibm.dbb.build.* 
import com.ibm.jzos.ZFile
 
// define the tsoexec object 
TSOExec exec = new TSOExec()
exec.confDir('/u/ibmuser/waziDBB/dbb-v2/conf')			// Runs the DBB helper - runIspf.sh
def systprt = new File('/u/ibmuser/tmp/TSOExec.log')    // systpry in a uss logfile 
exec.logFile(systprt)                           
exec.keepCommandScript(true)  							// dont delete CMDSCP- good for debugging
exec.addDDStatement("CMDSCP", "IBMUSER.ISPFGWY.EXEC", "RECFM(F,B) LRECL(80) TRACKS SPACE(1,1) DSORG(PS)", false)

/* Req1 Alloc a dataset for my rexx exec*/
mylogDCB="tracks space(1,1) dsorg(PS) blksize(0) lrecl(80) recfm(f,b) new catalog"
mylogDD="MYLOGDD"
mylogDSN="IBMUSER.GREXX.LOG"
if (ZFile.dsExists("//'$mylogDSN'")) ZFile.remove("//'$mylogDSN'")

exec.dd(new DDStatement().dsn(mylogDSN).name(mylogDD).options(mylogDCB)) 

//  setup the call to the exec with the above DD   
exec.setCommand("ex 'NLOPEZ.DAT.JCL(GREXX)' '${mylogDD}'")   


//  STEP Req2 - concat test
mypds="NLOPEZ.DAT.JCL(CONCATME)"
def sysRecs = ''' INSTREAM line1 \n INSTREAM line2 \n INSTREAM EOF'''
exec.dd(new DDStatement().name("SYSX").instreamData(sysRecs))
exec.dd(new DDStatement().dsn(mypds).options("shr"))


// call the exec  
int lastrc = exec.execute()
 
//copy the mvs file to Unix  
member='MYPGM'
logFile="/u/ibmuser/tmp/gRexx_${member}.log"
new CopyToHFS().dataset(mylogDSN).file(new File(logFile)).copy() 

// catch any errors 			
if (lastrc != 0)  println "cat $systprt ".execute().text    // print the full log for debug }
else              println exec.getOutput()                  // show systprt output only     
 
println "REXX RC = $lastrc    logfile " + logFile      
System.exit(lastrc)
	 


//**************************************


// req1 related rexx 
// 
/* Rexx */
// 	parse arg mylogDD 
//	say "gRexx started. Args = " mylogDD
//	say "Writing to pre-allocate DD " mylogDD  
//	/* listalc status */
//
//
//	PUSH 'Testing output of one line test 2'
//	"EXECIO 1 DISKW " mylogDD " (FINIS"
//	say "gRexx ended  RC=" RC
// 


// req 0 notes
//exec.addDDStatement("IN",  "NLOPEZ.DAT.COBOL(CMINC)", "SHR", false)
//exec.addDDStatement("OUT", "NLOPEZ.DAT.COBOL2(CMINC)", "SHR", false)
	 
 
/* Sample rexx code to be copied to a pds om MVS and used above
*   need to all the enclosing slash asterisk to the comments and remove the leading asterisks
*   
*      
* REXX TO MIGRATE CM CBL FILE WITH -INC  (NLOPEZ) `                
* SAVE; TSO EX 'NLOPEZ.DAT.EXEC(CMMIG)'                            
* look for "-INC" in col 1 and convert to standard cobol copy stmt 
* COPY must start in margin A (col 8)                              
* See groovy script to dd allocs                                   
*                                                                    
*	say "dbb post migration processor "                                 
*	"execio * diskr in (stem l.)"                                       
*	newFile.0= null                                                     
*	nfx = 1                                                             
*                                                                    
*	rewrite=no                                                          
*	do x = 1 to l.0                                                     
*    	if left(l.x,4) = '-INC' then do                                 
*       		parse var l.x . copymem                                      
*       		new = "       COPY " copymem                                 
*       		newFile.nfx = new                                            
*       		nfx = nfx + 1                                                
*       		new = "      * Converted old text in col 1="l.x              
*       		newFile.nfx = new                                            
*       		nfx = nfx + 1                                                
*        rewrite = yes                               
*       end                                         
*    else do                                        
*       newFile.nfx = l.x                           
*       nfx = nfx+1                                 
*       end                                         
*	end                                                
*	"execio 0 diskr in (FINIS"                         
*                                                   
* if rewrite=yes then do                             
*	say "Rewriting to cmfixed"                      
*    	"execio * diskw out (finis stem newFile.)"      
*    	exit 2                                          
* end                                                
* exit 0                                             
*/

/*  sample /etc/ispf/ISPF.conf
*
* ISPF.conf - "Legacy ISPF Gateway" configuration file
*
* Note: This is a copy of ISPF's ISP.SISPSAMP(ISPZISPC)
*       with z/OS Explorer customizations
*
*
* REQUIRED:
* Below is the minimum requirements for ISPF allocation.
* Change the default ISPF dataset names below to match your host site.
* Add additional dsn concatenations on same line and separate by comma.
* Order of datasets listed is search order in concatenation.
*
* uncomment this blk 
*??ispllib=ISP.SISPLOAD,DSN.V12R1M0.SDSNLOAD
*ispmlib=ISP.SISPMENU
*isptlib=ISP.SISPTENU
*ispplib=ISP.SISPPENU
*ispslib=ISP.SISPSLIB
*sysproc=ISP.SISPCLIB
*steplib=DSN.V12R1M0.SDSNLOAD
* end this blk
*
*
* OPTIONAL:
* Include below your own additional user exec for data set allocations.
* A sample exec is found in ISPF's samplib, ISP.SISPSAMP(ISPZISP2).
* If required, remove the * below and provide the absolute reference to
* your exec. When using allocjob, Be careful not to undo allocations
* done earlier in ISPF.conf.
*
*allocjob = DOHERTL.EXEC(RDZLGN1)
*
*
* OPTIONAL:
* When using re-usable ISPF sessions then the following timeout
* parameter will determine the time a users ISPF session will remain
* idle between service requests before shutting itself down.
* At the next request a new session will be established automatically.
* If required, remove the * below and specify, in seconds, the default
* time for all sessions. If not set, the default idle time is 15 min.   .

*ISPF_timeout = 900
*/