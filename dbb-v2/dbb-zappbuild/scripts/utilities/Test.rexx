/* rexx Unit test Batch jobs   Nlopez IBM = DAT 
    run from home as 
     ./waziDBB/dbb-v2/dbb-zappbuild/scripts/utilities/batchTest.rexx
    Notes: wip run a batch job as a unit test after CD 
*/

/* clean out the target loadlib used by UCD */
say 'Integration Testing  started ...'
x=sleep(5)


call InitJob
jobid = submit(job.)
say 'Submitted Integration test job ... jobid('jobid')...'        
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
 job.3="//STEPLIB  DD  DISP=SHR,DSN=IBMUSER.PIPELINE.LOAD" 
 job.4="//SYSOUT   DD SYSOUT=*,HOLD=YES"
return 



