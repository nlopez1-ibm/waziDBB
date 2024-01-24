/* rexx genral purpose DBB metadata util (Nlopez IBM)
    run from home as  ~/dbbUtils/dbbutil.rexx or from dbbUtils ./dbbutil.rexx
    notes: 
        assumes dbb v2 or better is install and env vars set 

        
*/

/* clean out the target loadlib used by UCD */
say 'Batch Unit Test Exec started.  Cleaning out old load module version...'
exit 

address tso "delete 'zdev.main.load(datbatch)'"
x=sleep(3)

call InitJob
call outtrap lds.
timeout = 30
do waiting = 0 to timeout
    say 'Waiting for UCD deployment...  iteration ' waiting 'of ' timeout     
    address tso "listds 'zdev.main.load(DATBATCH)'"
    if rc = 0 then leave
    x=sleep(5)  
end    
x=outtrap('OFF')

if waiting = timeout then do 
        say 'Error during deployment. Pgm not found zdev.main.load(DATBATCH)'
        say 'Review the UCD log '
        eixt 12
end
    
say 'Detected UCD deployment of zdev.main.load(DATBATCH)'          
jobid = submit(job.)
say 'Submitted Batch Unit test job ... jobid('jobid')...'        
x=sleep(5)
        
jout='ibmuser.jesout2.outlist'        
address tso "output IBMUSERB("jobid") print('"jout"') keep"; oRC= rc; x=sleep(1)
call bpxwdyn "alloc fi(JDD) da('"jout"') shr reuse "
address mvs "EXECIO * DISKR JDD (stem jlines. FINIS"          
                
if jlines.0 = 0 then do
    say "Error accessing sysout for IBMUSERB("jobid")"
    exit 12 
end 
        
say "Batch Unit test sysout results:" 
do jx = 1 to jlines.0 
   say '>' strip(jlines.jx)
end                        

exit 0 


/* */
initJob:
 job.0=4
 job.1="//IBMUSERB JOB CLASS=A,MSGCLASS=H,NOTIFY=IBMUSER,MSGLEVEL=(1,1)"
 job.2="//DBB EXEC PGM=DATBATCH"
 job.3="//STEPLIB  DD  DISP=SHR,DSN=ZDEV.MAIN.LOAD" 
 job.4="//SYSOUT   DD SYSOUT=*,HOLD=YES"
return 



/* sample 
tsocmd delete "'zdev.main.load(zbuild)'"
tsocmd listds "'zdev.main.load(DATBATCH)'"
ssh ibmuser@mywazi "export TSOPROFILE='prefix(ZDEV)'";  tsocmd "delete 'main.loa
*/
