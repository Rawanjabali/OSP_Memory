package osp.Memory;

import java.util.*;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Interrupts.*;

/**
    The MMU class contains the student code that performs the work of
    handling a memory reference.  It is responsible for calling the
    interrupt handler if a page fault is required.

    @OSPProject Memory
*/
public class MMU extends IflMMU
{
	public static int Cursor;
	public static int wantFree;
	public static ArrayList<FrameTableEntry> fifo;
    /**
        This method is called once before the simulation starts.
		Can be used to initialize the frame table and other static variables.

        @OSPProject Memory
    */
    public static void init()
    {
        // your code goes here
    	// set Cursor to 0 and set wantFree to 1.
    	Cursor = 0;
    	wantFree = 1;
    	for(int i = 0; i < MMU.getFrameTableSize(); i++) {
    		setFrame(i, new FrameTableEntry(i));
    	}
    	
    	fifo = new ArrayList<>();

    }

    /**
       This method handlies memory references. The method must
       calculate, which memory page contains the memoryAddress,
       determine, whether the page is valid, start page fault
       by making an interrupt if the page is invalid, finally,
       if the page is still valid, i.e., not swapped out by another
       thread while this thread was suspended, set its frame
       as referenced and then set it as dirty if necessary.
       (After pagefault, the thread will be placed on the ready queue,
       and it is possible that some other thread will take away the frame.)

       @param memoryAddress A virtual memory address
       @param referenceType The type of memory reference to perform
       @param thread that does the memory access
       (e.g., MemoryRead or MemoryWrite).
       @return The referenced page.

       @OSPProject Memory
    */
    static public PageTableEntry do_refer(int memoryAddress,int referenceType, ThreadCB thread)
    {
        // your code goes here
    	int virtual_address_bits = getVirtualAddressBits();
    	int page_address_bits = getPageAddressBits();
    	
    	int page_size = (int) Math.pow( 2, virtual_address_bits - page_address_bits);
    	int page_no = memoryAddress / page_size;
    	PageTableEntry p = getPTBR().pages[page_no];
    	if(! p.isValid()) {
    		if(p.getValidatingThread() == null) { // That means no other thread caused a pagefault on this invalid page.
    			// To cause an interrupt, set the various static fields of the class InterruptVector
    			InterruptVector.setPage(p);
    			InterruptVector.setReferenceType(referenceType);
    			InterruptVector.setThread(thread);
    			CPU.interrupt(PageFault);//By this, the page will be in the main memory and the thread will be in the ready queue
    		}
    		else {
    			thread.suspend(p); //suspend the input thread on the page
    		}
    		if(thread.getStatus() == ThreadKill) { // the dirty and referenced bit settings must not be changed if long time passed between the initial pagefault and the time the faulty page becomes valid
    			return p;
    		}
    		
    	}
    	
    	//if the page is valid, set the referenced and the dirty bits of the page. then quit.
    	p.getFrame().setReferenced(true); 
    	if(referenceType== MemoryWrite)
    		p.getFrame().setDirty(true);
    	return p;
    }

    /**
     * This method returns the current number of free frames. 
     * It does not matter where the search in the frame table starts, 
     * but this method must not change the value of the reference bits, 
     * dirty bits or MMU.Cursor.
     * @return number of free frames
     */
    
    

    /** Called by OSP after printing an error message. The student can
		insert code here to print various tables and data structures
		in their state just after the error happened.  The body can be
		left empty, if this feature is not used.

		@OSPProject Memory
     */
    public static void atError()
    {
        // your code goes here (if needed)

    }

    /** Called by OSP after printing a warning message. The student
		can insert code here to print various tables and data
		structures in their state just after the warning happened.
		The body can be left empty, if this feature is not used.

      @OSPProject Memory
     */
    public static void atWarning()
    {
        // your code goes here (if needed)

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */
    

}

/*
      Feel free to add local classes to improve the readability of your code
*/
