package osp.Memory;

import osp.Hardware.*;
import osp.Tasks.*;
import osp.Threads.*;
import osp.Devices.*;
import osp.Utilities.*;
import osp.IFLModules.*;
/**
   The PageTableEntry object contains information about a specific virtual
   page in memory, including the page frame in which it resides.
   @OSPProject Memory
*/

public class PageTableEntry extends IflPageTableEntry
{
    /**
       The constructor. Must call
       	   super(ownerPageTable,pageNumber);
       as its first statement.
       @OSPProject Memory
    */
	long createTime;
	long reftimer;
	
    public PageTableEntry(PageTable ownerPageTable, int pageNumber)
    {
    	super(ownerPageTable,pageNumber);
    	this.createTime = System.currentTimeMillis();
    	this.reftimer = HClock.get();

    }


    /**
       This method increases the lock count on the page by one.
	   The method must FIRST increment lockCount, THEN
	   check if the page is valid, and if it is not and no
	   page validation event is present for the page, start page fault
	   by calling PageFaultHandler.handlePageFault().
	   @return SUCCESS or FAILURE
	   FAILURE happens when the pagefault due to locking fails or the
	   that created the IORB thread gets killed.
	   @OSPProject Memory
    */
    public int do_lock(IORB iorb) {
    	if(this.isValid()) {
    		getFrame().incrementLockCount();
    		return SUCCESS;
    	}
    	
    	else {
    		if(getValidatingThread() == null) {
    			if(PageFaultHandler.handlePageFault(iorb.getThread(),MemoryLock,this)==FAILURE)
    				return FAILURE;
    			else {
    				getFrame().incrementLockCount();
    				return SUCCESS;
    			}
    		}
    		else if(getValidatingThread()==iorb.getThread()) {
    			getFrame().incrementLockCount();
        		return SUCCESS;
    		}
    		else {
    			iorb.getThread().suspend(this);
    			if(iorb.getThread().getStatus()==ThreadKill)
    				return FAILURE;
    			else {
    				getFrame().incrementLockCount();
    	    		return SUCCESS;
    			}
    				
    		}
    	}
    }

    /**
       This method decreases the lock count on the page by one.
	   This method must decrement lockCount, but not below zero.
	   @OSPProject Memory
    */
    public void do_unlock() {
    	//CHECK WHY > 0 
        if(getFrame().getLockCount() > 0)
        	getFrame().decrementLockCount();
    }
}
