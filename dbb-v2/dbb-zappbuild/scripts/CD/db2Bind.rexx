/* rexx - Sample exec -  nlopez IBM DAT 2023 
   This exec runs on zUnix to bind a program
*/

say 'db2Bind.rexx started' 
tcmd =' dsn bind'

call outtrap tcmdOut. 
address tso tcmd ; lrc = rc 
x=outtrap('OFF')     
if lrc <> 0 then address tso tcmd  /* show err */
say tcmdOut.1


/*****************************************************************************/
/*( REXX END ) add a trailing line at bottom */

no good   