/**
 * Authors: Rawan AlJabali, Sara Alabbasi
 * IDs    :    1617019    ,    1606983        
 */
package osp.Memory;

import java.util.*;
import osp.Hardware.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.FileSys.FileSys;
import osp.FileSys.OpenFile;
import osp.IFLModules.*;
import osp.Interrupts.*;
import osp.Utilities.*;
import osp.IFLModules.*;
import java.lang.*;

/**
	The page fault handler is responsible for handling a page
	fault.  If a swap in or swap out operation is required, the page fault
	handler must request the operation.
	
	@OSPProject Memory
*/
public class PageFaultHandler extends IflPageFaultHandler
{
	public static int numFreeFrames;
	/**
	    This method handles a page fault.
	
	    It must check and return if the page is valid,
	
	    It must check if the page is already being brought in by some other
		thread, i.e., if the page has already pagefaulted
		(for instance, using getValidatingThread()).
	    If that is the case, the thread must be suspended on that page.
	
	    If none of the above is true, a new frame must be chosen
	    and reserved until the swap in of the requested
	    page into this frame is complete.
	
		Note that you have to make sure that the validating thread of
		a page is set correctly. To this end, you must set the page's
		validating thread using setValidatingThread() when a pagefault
		happens and you must set it back to null when the pagefault is over.
	
		If no free frame could be found, then a page replacement algorithm
		must be used to select a victim page to be replaced.
	
	    If a swap-out is necessary (because the chosen frame is
	    dirty), the victim page must be dissasociated
	    from the frame and marked invalid. After the swap-in, the
	    frame must be marked clean. The swap-ins and swap-outs
	    must be preformed using regular calls to read() and write().
	
	    The student implementation should define additional methods, e.g,
	    a method to search for an available frame, and a method to select
	    a victim page making its frame available.
	
		Note: multiple threads might be waiting for completion of the
		page fault. The thread that initiated the pagefault would be
		waiting on the IORBs that are tasked to bring the page in (and
		to free the frame during the swapout). However, while
		pagefault is in progress, other threads might request the same
		page. Those threads won't cause another pagefault, of course,
		but they would enqueue themselves on the page (a page is also
		an Event!), waiting for the completion of the original
		pagefault. It is thus important to call notifyThreads() on the
		page at the end -- regardless of whether the pagefault
		succeeded in bringing the page in or not.
	
	    @param thread		 the thread that requested a page fault
	    @param referenceType whether it is memory read or write
	    @param page 		 the memory page
	
		@return SUCCESS 	 is everything is fine; FAILURE if the thread
		dies while waiting for swap in or swap out or if the page is
		already in memory and no page fault was necessary (well, this
		shouldn't happen, but...). In addition, if there is no frame
		that can be allocated to satisfy the page fault, then it
		should return NotEnoughMemory
	
	    @OSPProject Memory
	*/
	
	public static int do_handlePageFault(ThreadCB thread,int referenceType,PageTableEntry page)
    {
		// your code goes here
		
		//check if the page that is passed as a parameter is valid (already has a page frame assigned to it) and return FAILURE if it is
		if (page.isValid()) { 
			page.notifyThreads(); // page 96, 2nd paragraph
			ThreadCB.dispatch();
			return FAILURE;
		}
		
		if (page.getValidatingThread()!=null) { 
			thread.suspend(page);
		}
		 
		//it is possible that all frames are either locked,or reserved and so it is not possible to find a victim page to evict and free up aframe. Return NotEnoughMemory if this is the case.
    	boolean no_victim=true;
    	for(int i = 0 ; i < MMU.getFrameTableSize(); i++) {
    		if(MMU.getFrame(i).getLockCount()<=0 && !MMU.getFrame(i).isReserved()) {
    			no_victim=false;
    		}
    	}
    	
    	if(no_victim) {
    		page.notifyThreads();
    		ThreadCB.dispatch();
    		return NotEnoughMemory;
        }
		
    	//check after each swap in and swap out if the thread was killed, return FAILURE in that case
		else {// A victim can be found
			//Normal processing of a pagefault - page 96 - paragraph 5
			SystemEvent pfevent = new SystemEvent("PageFault"); // Page 78 - Create new SystemEvent object
			thread.suspend(pfevent); 
			page.setValidatingThread(thread); 
			
			FrameTableEntry frame = getFreeFrame();
			
			if(frame != null) { //A free frame is already there 
				//the page’s frame attribute can be updated and a swap-in operation can be performed right away
				frame.setReserved(thread.getTask());
				page.setFrame(frame);
				swap_in(thread, page);
				if (thread.getStatus() == ThreadKill) { 
					page.notifyThreads();
					page.setValidatingThread(null);
					page.setFrame(null);
					pfevent.notifyThreads();
					ThreadCB.dispatch();
					return FAILURE;
				}
				//Update 
				page.setValid(true);
				frame.setPage(page);
				frame.setReferenced(true);
				if(referenceType == MemoryWrite) {
					frame.setDirty(true);
				}
				frame.setUnreserved(thread.getTask());
				page.setValidatingThread(null);
				page.notifyThreads();
				pfevent.notifyThreads();
				ThreadCB.dispatch();
				return SUCCESS;
			}
			
			
			else { //No free frames -> Page Replacement
				frame = SecondChance();
				frame.setReserved(thread.getTask());
				
				if(!frame.isDirty()) {//If the frame contains a clean page, the frame should be freed and then a swap-in operation should be performed
					page.setFrame(frame);
					swap_in(thread, page);
					if(thread.getStatus() == ThreadKill) {
						page.notifyThreads();
						page.setValidatingThread(null);
						page.setFrame(null);
						pfevent.notifyThreads();
						ThreadCB.dispatch();
						return FAILURE;
					}
					//Update
					page.setValid(true);
					frame.setPage(page);
					frame.setReferenced(true);
					if (referenceType == MemoryWrite) {
						frame.setDirty(true);
					}
					frame.setUnreserved(thread.getTask());

				}
				else { //if the frame is dirty, swap-out must be performed, followed by freeing the frame, followed by a swap-in
					PageTableEntry ppage = frame.getPage();
					swap_out(thread,ppage);
					if(thread.getStatus() == ThreadKill) {
						page.notifyThreads();
						page.setValidatingThread(null);
						page.setFrame(null);
						pfevent.notifyThreads();
						ThreadCB.dispatch();
						return FAILURE;
					}
					//free the frame
					frame.setReferenced(false); 
					ppage.setValid(false);
					ppage.setFrame(null);
					frame.setDirty(false);
					frame.setPage(null);
					
					//update
					page.setFrame(frame);
					
					
					swap_in(thread,page);
					if(thread.getStatus() == ThreadKill) {
						page.notifyThreads();
						page.setValidatingThread(null);
						page.setFrame(null);
						pfevent.notifyThreads();
						ThreadCB.dispatch();
						return FAILURE;
					}
					
					//update
					page.setValid(true);
					frame.setPage(page);
					frame.setReferenced(true);
					if (referenceType == MemoryWrite) {
						frame.setDirty(true);
					}
					frame.setUnreserved(thread.getTask());
				}
				page.setValidatingThread(null);
				page.notifyThreads();
				pfevent.notifyThreads();
				ThreadCB.dispatch();
				return SUCCESS;
				
			}
		}



	}
	
	/**
     * Returns the current number of free frames. 
     * It does not matter where the search in the frame table starts, 
     * but this method must not change the value of the reference bits, 
     * dirty bits or MMU.Cursor.
     * @return int
     */
    public static int numFreeFrames() {
    	numFreeFrames = 0;
    	for (int i = 0; i < MMU.getFrameTableSize(); i++) {
    		FrameTableEntry frame = MMU.getFrame(i);
	    	if(frame.getPage() == null && !frame.isReserved() && frame.getLockCount() <= 0 && !frame.isReferenced() && !frame.isDirty()) {
	    		numFreeFrames ++;
	    	}
    	}
    	return numFreeFrames;
    }
    /**
     * Returns the first free frame starting the search from frame[0]
     * @return FrameTableEntry
     */
    public static FrameTableEntry getFreeFrame() {
    	FrameTableEntry f = null;
    	for(int i = 0; i < MMU.getFrameTableSize(); i++) {
    		if(MMU.getFrame(i).getPage() == null && !MMU.getFrame(i).isReserved() && MMU.getFrame(i).getLockCount() <= 0) {
    			f = MMU.getFrame(i);
    			return f;
    		}
    	}
    	return f;
    }

	public static FrameTableEntry SecondChance() {
		FrameTableEntry frame = null;
		int frameid;
		boolean firstdirty = true;
		
		// Phase 1
		for (int j = 0; j < 2; j++) {
			for (int i = 0; i < MMU.getFrameTableSize(); i++) {
				//Make sure you free all "clean frames", only until the number of free frames becomes equal to wantFree.
				if(numFreeFrames() == MMU.wantFree)
					break;

				// 1-If a page�s reference bit is set, clear it and move to the next frame.
				if (MMU.getFrame(MMU.Cursor).isReferenced()) {
					MMU.getFrame(MMU.Cursor).setReferenced(false);
				}
				/**
        		 * 2- When you find a "clean frame", i.e. a frame containing a page and 
        		 * whose reference bit is not set, and the frame is not locked and not 
        		 * reserved and not dirty, then:
        		 */
				else if (MMU.getFrame(MMU.Cursor).getPage() != null 
						&& !MMU.getFrame(MMU.Cursor).isReferenced()
						&& !MMU.getFrame(MMU.Cursor).isDirty()
						&& !MMU.getFrame(MMU.Cursor).isReserved()
						&& MMU.getFrame(MMU.Cursor).getLockCount() <= 0) {
					// free and update
					MMU.getFrame(MMU.Cursor).setDirty(false);
					MMU.getFrame(MMU.Cursor).setReferenced(false);
					MMU.getFrame(MMU.Cursor).getPage().setValid(false);
					MMU.getFrame(MMU.Cursor).getPage().setFrame(null);
					MMU.getFrame(MMU.Cursor).setPage(null);
					
				}
				/*3-save the frame ID of the first "dirty frame" you come across that 
	      		  is not locked, not reserved, but dirty just in case there are not 
	      		  enough clean frames.
	      		  */
				if (MMU.getFrame(MMU.Cursor).isDirty() == true
					&& MMU.getFrame(MMU.Cursor).isReserved() == false
					&& MMU.getFrame(MMU.Cursor).getLockCount() <= 0
					&& firstdirty ){
					frameid = MMU.getFrame(MMU.Cursor).getID();
					frame = MMU.getFrame(MMU.Cursor);
					firstdirty = false; 
				}
				// 4-Make sure that the final value of MMU.Cursor is updated to start the next search from the next frame table entry
				MMU.Cursor=(MMU.Cursor+1)%MMU.getFrameTableSize();
			}
			
		}
		
		//Phase II
		if(numFreeFrames() < MMU.wantFree && firstdirty == false) 
			return frame;	
		// Phase III
		else {
			return getFreeFrame();
		}
	}
		
	public static void swap_out(ThreadCB thread, PageTableEntry page) {
    	TaskCB Task = page.getTask();
    	Task.getSwapFile().write(page.getID(), page, thread);
    }
	
	public static void swap_in(ThreadCB thread, PageTableEntry page) {
    	TaskCB Task = page.getTask();
    	Task.getSwapFile().read(page.getID(), page, thread);
    }

}