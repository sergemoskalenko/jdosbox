package jdos.cpu;

import jdos.Dosbox;
import jdos.hardware.Memory;
import jdos.misc.Log;
import jdos.misc.setup.Config;
import jdos.misc.setup.Module_base;
import jdos.misc.setup.Section;
import jdos.types.LogSeverities;
import jdos.types.LogTypes;
import jdos.util.IntRef;

public class Paging extends Module_base {
    public static final int MEM_PAGE_SIZE = 4096;
    public static final int XMS_START = 0x110;

    public static final int PFLAG_READABLE = 0x1;
    public static final int PFLAG_WRITEABLE = 0x2;
    public static final int PFLAG_HASROM = 0x4;
    public static final int PFLAG_HASCODE = 0x8;                //Page contains dynamic code
    public static final int PFLAG_NOCODE = 0x10;            //No dynamic code can be generated here
    public static final int PFLAG_INIT = 0x20;            //No dynamic code can be generated here

    static public final int PAGING_LINKS = (128 * 1024 / 4);
    static public final int TLB_SIZE;
    static public final int BANK_SHIFT = 28;
    static public final int BANK_MASK = 0xffff; // always the same as TLB_SIZE-1?
    static public final int TLB_BANKS;

    static public final int LINK_START = ((1024 + 64) / 4); //Start right after the HMA

    static {
        if (Config.USE_FULL_TLB) {
            TLB_SIZE = (1024 * 1024);
        } else {
            TLB_SIZE = 65536;    // This must a power of 2 and greater then LINK_START
        }
        TLB_BANKS = ((1024 * 1024 / TLB_SIZE) - 1);
    }

    static public class PageHandler {
        public /*Bitu*/int readb(/*PhysPt*/int addr) {
            Log.exit("No byte handler for read from " + Long.toString(addr, 16));
            return 0;
        }

        public /*Bitu*/int readw(/*PhysPt*/int addr) {
            return readb(addr + 0) | (readb(addr + 1) << 8);
        }

        public /*Bitu*/int readd(/*PhysPt*/int addr) {
            return readb(addr + 0) | (readb(addr + 1) << 8) | (readb(addr + 2) << 16) | (readb(addr + 3) << 24);
        }

        public void writeb(/*PhysPt*/int addr,/*Bitu*/int val) {
            Log.exit("No byte handler for write to " + Long.toString(addr, 16));
        }

        public void writew(/*PhysPt*/int addr,/*Bitu*/int val) {
            writeb(addr + 0, (val));
            writeb(addr + 1, (val >> 8));
        }

        public void writed(/*PhysPt*/int addr,/*Bitu*/int val) {
            writeb(addr + 0, (val));
            writeb(addr + 1, (val >> 8));
            writeb(addr + 2, (val >> 16));
            writeb(addr + 3, (val >> 24));
        }

        public /*HostPt*/int GetHostReadPt(/*Bitu*/int phys_page) {
            return 0;
        }

        public /*HostPt*/int GetHostWritePt(/*Bitu*/int phys_page) {
            return 0;
        }

        public /*Bitu*/ int flags;
    }

    public static class X86_PageEntryBlock {
        /*Bit32u*/ int p;
        /*Bit32u*/ int wr;
        /*Bit32u*/ int us;
        /*Bit32u*/ int pwt;
        /*Bit32u*/ int pcd;
        /*Bit32u*/ int a;
        /*Bit32u*/ int d;
        /*Bit32u*/ int pat;
        /*Bit32u*/ int g;
        /*Bit32u*/ int avl;
        /*Bit32u*/ int base;
    }

    public static class X86PageEntry {

        public int load() {
            int load = 0;

            load |= block.p & 0x1;
            load |= (block.wr & 0x1) << 1;
            load |= (block.us & 0x1) << 2;
            load |= (block.pwt & 0x1) << 3;
            load |= (block.pcd & 0x1) << 4;
            load |= (block.a & 0x1) << 5;
            load |= (block.d & 0x1) << 6;
            load |= (block.pat & 0x1) << 7;
            load |= (block.g & 0x1) << 8;
            load |= (block.avl & 0x7) << 9;
            load |= (block.base & 0xFFFFF) << 12;
            return load;
        }

        public void load(int value) {
            block.p = value & 0x1;
            block.wr = (value >> 1) & 0x1;
            block.us = (value >> 2) & 0x1;
            block.pwt = (value >> 3) & 0x1;
            block.pcd = (value >> 4) & 0x1;
            block.a = (value >> 5) & 0x1;
            block.d = (value >> 6) & 0x1;
            block.pat = (value >> 7) & 0x1;
            block.g = (value >> 8) & 0x1;
            block.avl = (value >> 9) & 0x7;
            block.base = (value >>> 12) & 0xFFFFF;
        }

        X86_PageEntryBlock block = new X86_PageEntryBlock();
    }

    static public class PagingBlock {
        public PagingBlock() {
            tlb = new Tlb();
        }

        public /*Bitu*/ int cr3;
        public /*Bitu*/ int cr2;
        public boolean wp;

        public class Base {
            public /*Bitu*/ int page;
            public /*PhysPt*/ int addr;
        }

        public Base base = new Base();

        public class Tlb {
            public /*HostPt*/ int[] read = new int[TLB_SIZE];
            public /*HostPt*/ int[] write = new int[TLB_SIZE];
            public PageHandler[] readhandler = new PageHandler[TLB_SIZE];
            public PageHandler[] writehandler = new PageHandler[TLB_SIZE];
            public /*Bit32u*/ int[] phys_page = new int[TLB_SIZE];
        }

        public Tlb tlb;

        public class Links {
            public /*Bitu*/ int used;
            public /*Bit32u*/ int[] entries = new int[PAGING_LINKS];
        }

        public class UR_Links {
            public /*Bitu*/ int used;
            public /*Bit32u*/ int[] entries = new int[PAGING_LINKS];
        }

        public class KRW_Links {
            public /*Bitu*/ int used;
            public /*Bit32u*/ int[] entries = new int[PAGING_LINKS];
        }

        public class KR_Links {
            public /*Bitu*/ int used;
            public /*Bit32u*/ int[] entries = new int[PAGING_LINKS];
        }

        public Links links = new Links();
        public UR_Links ur_links = new UR_Links();
        public KRW_Links krw_links = new KRW_Links();
        public KR_Links kr_links = new KR_Links();
        public /*Bit32u*/ long[] firstmb = new long[LINK_START];
        public boolean enabled;
    }

    private static /*HostPt*/int get_tlb_read(/*PhysPt*/int address) {
        return paging.tlb.read[address >>> 12];
    }

    private static /*HostPt*/int get_tlb_write(/*PhysPt*/int address) {
        return paging.tlb.write[address >>> 12];
    }

    public static PageHandler get_tlb_readhandler(/*PhysPt*/int address) {
        return paging.tlb.readhandler[address >>> 12];
    }

    private static PageHandler get_tlb_writehandler(/*PhysPt*/int address) {
        return paging.tlb.writehandler[address >>> 12];
    }

    /* Use these helper functions to access linear addresses in readX/writeX functions */
    public static /*PhysPt*/int PAGING_GetPhysicalPage(/*PhysPt*/int linePage) {
        return (paging.tlb.phys_page[linePage >>> 12] << 12);
    }

    public static /*PhysPt*/int PAGING_GetPhysicalAddress(/*PhysPt*/int linAddr) {
        return (paging.tlb.phys_page[linAddr >>> 12] << 12) | (linAddr & 0xfff);
    }

    /* Special inlined memory reading/writing */

    public static int getDirectIndex(int address) {
        if (Config.DEBUG_DEDERMINISTIC)
            return -1;
        /*HostPt*/
        int tlb_addr = get_tlb_read(address);
        if (tlb_addr != Integer.MIN_VALUE) {
            tlb_addr = get_tlb_write(address);
            if (tlb_addr == Integer.MIN_VALUE)
                return -1;
            if ((get_tlb_writehandler(address).flags & PFLAG_HASCODE)!=0)
                return -1;
        }
        if (tlb_addr != Integer.MIN_VALUE) return tlb_addr + address;
        get_tlb_readhandler(address).readb(address);
        tlb_addr = get_tlb_read(address);
        if (tlb_addr == Integer.MIN_VALUE)
            return -1;
        tlb_addr = get_tlb_write(address);
        if (tlb_addr == Integer.MIN_VALUE)
            return -1;
        if ((get_tlb_writehandler(address).flags & PFLAG_HASCODE)!=0)
            return -1;
        return tlb_addr + address;
    }

    public static int getDirectIndexRO(int address) {
        if (Config.DEBUG_DEDERMINISTIC)
            return -1;
        /*HostPt*/
        int tlb_addr = get_tlb_read(address);
        if (tlb_addr != Integer.MIN_VALUE) return tlb_addr + address;
        get_tlb_readhandler(address).readb(address);
        tlb_addr = get_tlb_read(address);
        if (tlb_addr == Integer.MIN_VALUE)
            return -1;
        return (int) (tlb_addr + address);
    }

    public static /*Bit8u*/short mem_readb_inline(/*PhysPt*/int address) {
        /*HostPt*/
        int tlb_addr = get_tlb_read(address);
        if (tlb_addr != Integer.MIN_VALUE)
            return Memory.host_readb(tlb_addr + address);
        else return (short) (get_tlb_readhandler(address)).readb(address);
    }

    public static /*Bit16u*/int mem_readw_inline(/*PhysPt*/int address) {
        if ((address & 0xfff) < 0xfff) {
            /*HostPt*/
            int tlb_addr = get_tlb_read(address);
            if (tlb_addr != Integer.MIN_VALUE) return Memory.host_readw(tlb_addr + address);
            else return (get_tlb_readhandler(address)).readw(address);
        } else return Memory.mem_unalignedreadw(address);
    }

    public static /*Bit32u*/int mem_readd_inline(/*PhysPt*/int address) {
        if ((address & 0xfff) < 0xffd) {
            /*HostPt*/
            int tlb_addr = get_tlb_read(address);
            if (tlb_addr != Integer.MIN_VALUE) return Memory.host_readd(tlb_addr + address);
            else return (get_tlb_readhandler(address)).readd(address);
        } else return Memory.mem_unalignedreadd(address);
    }

    public static void mem_writeb_inline(/*PhysPt*/int address,/*Bit8u*/short val) {
        /*HostPt*/
        int tlb_addr = get_tlb_write(address);
        if (tlb_addr != Integer.MIN_VALUE) Memory.host_writeb(tlb_addr + address, val);
        else (get_tlb_writehandler(address)).writeb(address, val);
    }

    public static void mem_writew_inline(/*PhysPt*/int address,/*Bit16u*/int val) {
        if ((address & 0xfff) < 0xfff) {
            /*HostPt*/
            int tlb_addr = get_tlb_write(address);
            if (tlb_addr != Integer.MIN_VALUE) Memory.host_writew(tlb_addr + address, val);
            else (get_tlb_writehandler(address)).writew(address, val);
        } else Memory.mem_unalignedwritew(address, val);
    }

    public static void mem_writed_inline(/*PhysPt*/int address,/*Bit32u*/int val) {
        if ((address & 0xfff) < 0xffd) {
            /*HostPt*/
            int tlb_addr = get_tlb_write(address);
            if (tlb_addr != Integer.MIN_VALUE) Memory.host_writed(tlb_addr + address, val);
            else (get_tlb_writehandler(address)).writed(address, val);
        } else Memory.mem_unalignedwrited(address, val);
    }

    private static final int LINK_TOTAL = (64 * 1024);

    private static boolean USERWRITE_PROHIBITED() {
        return ((CPU.cpu.cpl & CPU.cpu.mpl) == 3);
    }

    static public PagingBlock paging;

    static private class PF_Entry {
        /*Bitu*/ int cs;
        /*Bitu*/ int eip;
        /*Bitu*/ int page_addr;
        /*Bitu*/ int mpl;
    }

    static private final int PF_QUEUESIZE = 16;

    static private class Pf_queue {
        public Pf_queue() {
            for (int i = 0; i < entries.length; i++)
                entries[i] = new PF_Entry();
        }

        /*Bitu*/ int used;
        PF_Entry[] entries = new PF_Entry[PF_QUEUESIZE];
    }

    static private Pf_queue pf_queue = new Pf_queue();

    static private CPU.CPU_Decoder PageFaultCore = new CPU.CPU_Decoder() {
        public /*Bits*/int call() {
            CPU.CPU_CycleLeft += CPU.CPU_Cycles;
            CPU.CPU_Cycles = 1;
            /*Bits*/
            int ret = Core_full.CPU_Core_Full_Run.call();
            CPU.CPU_CycleLeft += CPU.CPU_Cycles;
            if (ret < 0) Log.exit("Got a dosbox close machine in pagefault core?");
            if (ret != 0)
                return ret;
            if (pf_queue.used == 0) Log.exit("PF Core without PF");
            PF_Entry entry = pf_queue.entries[pf_queue.used - 1];
            X86PageEntry pentry = new X86PageEntry();
            pentry.load(Memory.phys_readd(entry.page_addr));
            if (pentry.block.p != 0 && entry.cs == CPU.Segs_CSval && entry.eip == CPU_Regs.reg_eip) {
                CPU.cpu.mpl = entry.mpl;
                return -1;
            } else if (CPU.iret) {
                CPU.iret = false;
                if (Callback.inHandler==0) {
                    CPU.cpu.mpl = pf_queue.entries[0].mpl;
                    while (pf_queue.used>0) {
                        Core_full.removeState();
                        pf_queue.used--;
                    }
                    CPU.cpudecoder = Core_dynamic.CPU_Core_Dynamic_Run;
                    System.out.println("Forcing PF exit");
                    throw new PageFaultException();
                }
            }
            return 0;
        }
    };

    final static private int ACCESS_KR = 0;
    final static private int ACCESS_KRW = 1;
    final static private int ACCESS_UR = 2;
    final static private int ACCESS_URW = 3;
    final static private int ACCESS_TABLEFAULT = 4;
    final static private String[] mtr = new String[]{"KR ", "KRW", "UR ", "URW", "PFL"};

// bit0 entry write
// bit1 entry access
// bit2 table write
// bit3 table access
// These arrays define how the access bits in the page table and entry
// result in access rights.
// The used array is switched depending on the CPU under emulation.

    // Intel says the lowest numeric value wins for both 386 and 486+
// There's something strange about KR with WP=1 though
    private final static /*Bit8u*/ int[] translate_array = new int[]{
            ACCESS_KR,        // 00 00
            ACCESS_KR,        // 00 01
            ACCESS_KR,        // 00 10
            ACCESS_KR,        // 00 11
            ACCESS_KR,        // 01 00
            ACCESS_KRW,        // 01 01
            ACCESS_KR, //	ACCESS_KRW,		// 01 10
            ACCESS_KRW,        // 01 11
            ACCESS_KR,        // 10 00
            ACCESS_KR, //	ACCESS_KRW,		// 10 01
            ACCESS_UR,        // 10 10
            ACCESS_UR,        // 10 11
            ACCESS_KR,        // 11 00
            ACCESS_KRW,        // 11 01
            ACCESS_UR,        // 11 10
            ACCESS_URW        // 11 11
    };

    // This array defines how a page is mapped depending on
    // page access right, cpl==3, and WP.
    // R = map handler as read, W = map handler as write, E = map exception handler
    final static private int ACMAP_RW = 0;
    final static private int ACMAP_RE = 1;
    final static private int ACMAP_EE = 2;

    final static private String[] lnm = new String[]{"RW ", "RE ", "EE "}; // debug stuff

    // bit0-1 ACCESS_ type
    // bit2   1=user mode
    // bit3   WP on

    private final static /*Bit8u*/ int[] xlat_mapping = new int[]{
            //  KR        KRW       UR        URW
            // index 0-3   kernel, wp 0
            ACMAP_RW, ACMAP_RW, ACMAP_RW, ACMAP_RW,
            // index 4-7   user,   wp 0
            ACMAP_EE, ACMAP_EE, ACMAP_RE, ACMAP_RW,
            // index 8-11  kernel, wp 1
            ACMAP_RE, ACMAP_RW, ACMAP_RE, ACMAP_RW,
            // index 11-15 user,   wp 1 (same as user, wp 0)
            ACMAP_EE, ACMAP_EE, ACMAP_RE, ACMAP_RW,
    };

    // This table can figure out if we are going to fault right now in the init handler
    // (1=fault)
    // bit0-1 ACCESS_ type
    // bit2   1=user mode
    // bit3   1=writing
    // bit4   wp

    private final static /*Bit8u*/ int[] fault_table = new int[]{
            //	KR	KRW	UR	URW
            // WP 0
            // index 0-3   kernel, reading
            0, 0, 0, 0,
            // index 4-7   user,   reading
            1, 1, 0, 0,
            // index 8-11  kernel, writing
            0, 0, 0, 0,
            // index 11-15 user,   writing
            1, 1, 1, 0,
            // WP 1
            // index 0-3   kernel, reading
            0, 0, 0, 0,
            // index 4-7   user,   reading
            1, 1, 0, 0,
            // index 8-11  kernel, writing
            1, 0, 1, 0,
            // index 11-15 user,   writing
            1, 1, 1, 0,
    };

    final static private int PHYSPAGE_DITRY = 0x10000000;
    final static private int PHYSPAGE_ADDR = 0x000FFFFF;

    // helper functions for calculating table entry addresses
    private static /*PhysPt*/int GetPageDirectoryEntryAddr(/*PhysPt*/int lin_addr) {
        return paging.base.addr | ((lin_addr >>> 22) << 2);
    }

    private static /*PhysPt*/int GetPageTableEntryAddr(/*PhysPt*/int lin_addr, X86PageEntry dir_entry) {
        return (dir_entry.block.base << 12) | ((lin_addr >>> 10) & 0xffc);
    }
/*
void PrintPageInfo(const char* string, PhysPt lin_addr, bool writing, bool prepare_only) {

	Bitu lin_page=lin_addr >> 12;

	X86PageEntry dir_entry, table_entry;
	bool isUser = (((cpu.cpl & cpu.mpl)==3)? true:false);

	PhysPt dirEntryAddr = GetPageDirectoryEntryAddr(lin_addr);
	PhysPt tableEntryAddr = 0;
	dir_entry.load=phys_readd(dirEntryAddr);
	Bitu result = 4;
	bool dirty = false;
	Bitu ft_index = 0;

	if (dir_entry.block.p) {
		tableEntryAddr = GetPageTableEntryAddr(lin_addr, dir_entry);
		table_entry.load=phys_readd(tableEntryAddr);
		if (table_entry.block.p) {
			result =
				translate_array[((dir_entry.load<<1)&0xc) | ((table_entry.load>>1)&0x3)];

			ft_index = result | (writing? 8:0) | (isUser? 4:0) |
				(paging.wp? 16:0);

			dirty = table_entry.block.d? true:false;
		}
	}
	LOG_MSG("%s %s LIN% 8x PHYS% 5x wr%x ch%x wp%x d%x c%x m%x f%x a%x [%x/%x/%x]",
		string, mtr[result], lin_addr, table_entry.block.base,
		writing, prepare_only, paging.wp,
		dirty, cpu.cpl, cpu.mpl, fault_table[ft_index],
		((dir_entry.load<<1)&0xc) | ((table_entry.load>>1)&0x3),
		dirEntryAddr, tableEntryAddr, table_entry.load);
}
*/

    static public class PageFaultException extends RuntimeException {}

    static public boolean pageFault = false;

    // PAGING_NewPageFault
    // lin_addr, page_addr: the linear and page address the fault happened at
    // prepare_only: true in case the calling core handles the fault, else the PageFaultCore does
    static void PAGING_NewPageFault(/*PhysPt*/int lin_addr, /*Bitu*/int page_addr, boolean prepare_only, /*Bitu*/int faultcode) {
        paging.cr2 = (int)lin_addr;

        //LOG_MSG("FAULT q%d, code %x",  pf_queue.used, faultcode);
        //PrintPageInfo("FA+",lin_addr,faultcode, prepare_only);

        if (pageFault) {
            Log.exit("Double PageFault");
        }
        if (prepare_only) {
            CPU.cpu.exception.which = CPU.EXCEPTION_PF;
            CPU.cpu.exception.error = faultcode;
        } else {
            CPU.iret = false;
            if (Callback.inHandler==0) {
                CPU_Regs.FillFlags();
                pageFault = true;
                CPU.CPU_Exception(CPU.EXCEPTION_PF, faultcode);
                pageFault = false;
                throw new PageFaultException();
            }
            // Save the state of the cpu cores
            LazyFlags old_lflags = new LazyFlags(Flags.lflags);
            CPU.CPU_Decoder old_cpudecoder;
            old_cpudecoder = CPU.cpudecoder;
            CPU.cpudecoder = PageFaultCore;

            if (pf_queue.used >= PF_QUEUESIZE) Log.exit("PF queue overrun.");
            PF_Entry entry = pf_queue.entries[pf_queue.used++];
            entry.cs = CPU.Segs_CSval;
            entry.eip = CPU_Regs.reg_eip;
            entry.page_addr = page_addr;
            entry.mpl = CPU.cpu.mpl;
            CPU.cpu.mpl = 3;

            pageFault = true;
            CPU.CPU_Exception(CPU.EXCEPTION_PF, faultcode);
            pageFault = false;

            Core_full.pushState();
            Dosbox.DOSBOX_RunMachinePF();
            Core_full.popState();

            pf_queue.used--;
            if (Log.level <= LogSeverities.LOG_NORMAL)
                Log.log(LogTypes.LOG_PAGING, LogSeverities.LOG_NORMAL, "Left PageFault for " + Long.toString(lin_addr, 16) + " queue " + pf_queue.used);
            Flags.lflags.copy(old_lflags);
            CPU.cpudecoder = old_cpudecoder;
            //LOG_MSG("FAULT exit");
        }
    }

    static private class PageFoilHandler extends PageHandler {
        private void work(/*PhysPt*/int addr) {
            /*Bitu*/
            int lin_page = addr >>> 12;
            /*Bit32u*/
            int phys_page = paging.tlb.phys_page[lin_page] & PHYSPAGE_ADDR;

            // set the page dirty in the tlb
            paging.tlb.phys_page[lin_page] |= PHYSPAGE_DITRY;

            // mark the page table entry dirty
            X86PageEntry dir_entry = new X86PageEntry(), table_entry = new X86PageEntry();

            /*PhysPt*/
            int dirEntryAddr = GetPageDirectoryEntryAddr(addr);
            dir_entry.load(Memory.phys_readd(dirEntryAddr));
            if (dir_entry.block.p == 0) Log.exit("Undesired situation 1 in page foiler.");

            /*PhysPt*/
            int tableEntryAddr = GetPageTableEntryAddr(addr, dir_entry);
            table_entry.load((int) Memory.phys_readd(tableEntryAddr));
            if (table_entry.block.p == 0) Log.exit("Undesired situation 2 in page foiler.");

            // for debugging...
            if (table_entry.block.base != phys_page)
                if (table_entry.block.p == 0) Log.exit("Undesired situation 3 in page foiler.");

            // map the real write handler in our place
            PageHandler handler = Memory.MEM_GetPageHandler(phys_page);

            // debug
//		LOG_MSG("FOIL            LIN% 8x PHYS% 8x [%x/%x/%x] WRP % 8x", addr, phys_page,
//			dirEntryAddr, tableEntryAddr, table_entry.load, wtest);

            // this can happen when the same page table is used at two different
            // page directory entries / linear locations (WfW311)
            // if (table_entry.block.d) E_Exit("% 8x Already dirty!!",table_entry.load);


            // set the dirty bit
            table_entry.block.d = 1;
            Memory.phys_writed(tableEntryAddr, table_entry.load());

            // replace this handler with the real thing
            if ((handler.flags & PFLAG_WRITEABLE) != 0)
                paging.tlb.write[lin_page] = handler.GetHostWritePt(phys_page) - (lin_page << 12);
            else paging.tlb.write[lin_page] = Integer.MIN_VALUE;
            paging.tlb.writehandler[lin_page] = handler;
        }

        private void read() {
            Log.exit("The page foiler shouldn't be read.");
        }

        public PageFoilHandler() {
            flags = PFLAG_INIT | PFLAG_NOCODE; // ???
        }


        public /*Bitu*/int readb(/*PhysPt*/int addr) {
            read();
            return 0;
        }

        public /*Bitu*/int readw(/*PhysPt*/int addr) {
            read();
            return 0;
        }

        public /*Bitu*/int readd(/*PhysPt*/int addr) {
            read();
            return 0;
        }

        public void writeb(/*PhysPt*/int addr,/*Bitu*/int val) {
            work(addr);
            // execute the write:
            // no need to care about mpl because we won't be entered
            // if write isn't allowed
            Memory.mem_writeb(addr, val);
        }

        public void writew(/*PhysPt*/int addr,/*Bitu*/int val) {
            work(addr);
            Memory.mem_writew(addr, val);
        }

        public void writed(/*PhysPt*/int addr,/*Bitu*/int val) {
            work(addr);
            Memory.mem_writed(addr, val);
        }
    }

    ;

    static private class ExceptionPageHandler extends PageHandler {
        private PageHandler getHandler(/*PhysPt*/int addr) {
            /*Bitu*/
            int lin_page = addr >>> 12;
            /*Bit32u*/
            int phys_page = paging.tlb.phys_page[lin_page] & PHYSPAGE_ADDR;
            PageHandler handler = Memory.MEM_GetPageHandler(phys_page);
            return handler;
        }

        private boolean hack_check(/*PhysPt*/int addr) {
            // First Encounters
            // They change the page attributes without clearing the TLB.
            // On a real 486 they get away with it because its TLB has only 32 or so
            // elements. The changed page attribs get overwritten and re-read before
            // the exception happens. Here we have gazillions of TLB entries so the
            // exception occurs if we don't check for it.
            /*Bitu*/
            int old_attirbs = paging.tlb.phys_page[addr >>> 12] >> 30;
            X86PageEntry dir_entry = new X86PageEntry(), table_entry = new X86PageEntry();

            dir_entry.load((int) Memory.phys_readd(GetPageDirectoryEntryAddr(addr)));
            if (dir_entry.block.p == 0) return false;
            table_entry.load((int) Memory.phys_readd(GetPageTableEntryAddr(addr, dir_entry)));
            if (table_entry.block.p == 0) return false;
            /*Bitu*/
            int result = translate_array[((dir_entry.load() << 1) & 0xc) | ((table_entry.load() >> 1) & 0x3)];
            if (result != old_attirbs) return true;
            return false;
        }

        void Exception(/*PhysPt*/int addr, boolean writing, boolean checked) {
            //PrintPageInfo("XCEPT",addr,writing, checked);
            //LOG_MSG("XCEPT LIN% 8x wr%d, ch%d, cpl%d, mpl%d",addr, writing, checked, cpu.cpl, cpu.mpl);
            /*PhysPt*/
            int tableaddr = 0;
            if (!checked) {
                X86PageEntry dir_entry = new X86PageEntry();
                dir_entry.load(Memory.phys_readd(GetPageDirectoryEntryAddr(addr)));
                if (dir_entry.block.p == 0) Log.exit("Undesired situation 1 in exception handler.");

                // page table entry
                tableaddr = GetPageTableEntryAddr(addr, dir_entry);
                //Bitu d_index=(addr >> 12) >> 10;
                //tableaddr=(paging.base.page<<12) | (d_index<<2);
            }
            PAGING_NewPageFault(addr, tableaddr, checked, 1 | (writing ? 2 : 0) | (((CPU.cpu.cpl & CPU.cpu.mpl) == 3) ? 4 : 0));

            PAGING_ClearTLB(); // TODO got a better idea?
        }

        /*Bitu*/int readb_through(/*PhysPt*/int addr) {
            /*Bitu*/
            int lin_page = addr >>> 12;
            /*Bit32u*/
            int phys_page = paging.tlb.phys_page[lin_page] & PHYSPAGE_ADDR;
            PageHandler handler = Memory.MEM_GetPageHandler(phys_page);
            if ((handler.flags & PFLAG_READABLE) != 0) {
                return Memory.host_readb(handler.GetHostReadPt(phys_page) + (int) (addr & 0xfff));
            } else {
                return handler.readb(addr);
            }
        }

        /*Bitu*/int readw_through(/*PhysPt*/int addr) {
            /*Bitu*/
            int lin_page = addr >>> 12;
            /*Bit32u*/
            int phys_page = paging.tlb.phys_page[lin_page] & PHYSPAGE_ADDR;
            PageHandler handler = Memory.MEM_GetPageHandler(phys_page);
            if ((handler.flags & PFLAG_READABLE) != 0) {
                return Memory.host_readw((int) (handler.GetHostReadPt(phys_page) + (addr & 0xfff)));
            } else {
                return handler.readw(addr);
            }
        }

        /*Bitu*/int readd_through(/*PhysPt*/int addr) {
            /*Bitu*/
            int lin_page = addr >>> 12;
            /*Bit32u*/
            int phys_page = paging.tlb.phys_page[lin_page] & PHYSPAGE_ADDR;
            PageHandler handler = Memory.MEM_GetPageHandler(phys_page);
            if ((handler.flags & PFLAG_READABLE) != 0) {
                return Memory.host_readd((int) (handler.GetHostReadPt(phys_page) + (addr & 0xfff)));
            } else {
                return handler.readd(addr);
            }
        }

        void writeb_through(/*PhysPt*/int addr, /*Bitu*/int val) {
            /*Bitu*/
            int lin_page = addr >>> 12;
            /*Bit32u*/
            int phys_page = paging.tlb.phys_page[lin_page] & PHYSPAGE_ADDR;
            PageHandler handler = Memory.MEM_GetPageHandler(phys_page);
            if ((handler.flags & PFLAG_WRITEABLE) != 0) {
                Memory.host_writeb((int) (handler.GetHostWritePt(phys_page) + (addr & 0xfff)), (short) val);
            } else {
                handler.writeb(addr, val);
            }
        }

        void writew_through(/*PhysPt*/int addr, /*Bitu*/int val) {
            /*Bitu*/
            int lin_page = addr >>> 12;
            /*Bit32u*/
            int phys_page = paging.tlb.phys_page[lin_page] & PHYSPAGE_ADDR;
            PageHandler handler = Memory.MEM_GetPageHandler(phys_page);
            if ((handler.flags & PFLAG_WRITEABLE) != 0) {
                Memory.host_writew((int) (handler.GetHostWritePt(phys_page) + (addr & 0xfff)), val);
            } else {
                handler.writew(addr, val);
            }
        }

        void writed_through(/*PhysPt*/int addr, /*Bitu*/int val) {
            /*Bitu*/
            int lin_page = addr >>> 12;
            /*Bit32u*/
            int phys_page = paging.tlb.phys_page[lin_page] & PHYSPAGE_ADDR;
            PageHandler handler = Memory.MEM_GetPageHandler(phys_page);
            if ((handler.flags & PFLAG_WRITEABLE) != 0) {
                Memory.host_writed((handler.GetHostWritePt(phys_page) + (addr & 0xfff)), val);
            } else {
                handler.writed(addr, (int) val);
            }
        }

        public ExceptionPageHandler() {
            flags = PFLAG_INIT | PFLAG_NOCODE; // ???
        }

        public /*Bitu*/int readb(/*PhysPt*/int addr) {
            if (CPU.cpu.mpl == 0) return readb_through(addr);

            Exception(addr, false, false);
            return Memory.mem_readb(addr); // read the updated page (unlikely to happen?)
        }

        public /*Bitu*/int readw(/*PhysPt*/int addr) {
            // access type is supervisor mode (temporary)
            // we are always allowed to read in superuser mode
            // so get the handler and address and read it
            if (CPU.cpu.mpl == 0) return readw_through(addr);

            Exception(addr, false, false);
            return Memory.mem_readw(addr);
        }

        public /*Bitu*/int readd(/*PhysPt*/int addr) {
            if (CPU.cpu.mpl == 0) return readd_through(addr);

            Exception(addr, false, false);
            return Memory.mem_readd(addr);
        }

        public void writeb(/*PhysPt*/int addr,/*Bitu*/int val) {
            if (CPU.cpu.mpl == 0) {
                writeb_through(addr, val);
                return;
            }
            Exception(addr, true, false);
            Memory.mem_writeb(addr, val);
        }

        public void writew(/*PhysPt*/int addr,/*Bitu*/int val) {
            if (CPU.cpu.mpl == 0) {
                // TODO Exception on a KR-page?
                writew_through(addr, val);
                return;
            }
            if (hack_check(addr)) {
                Log.log_msg("Page attributes modified without clear");
                PAGING_ClearTLB();
                Memory.mem_writew(addr, val);
                return;
            }
            // firstenc here
            Exception(addr, true, false);
            Memory.mem_writew(addr, val);
        }

        public void writed(/*PhysPt*/int addr,/*Bitu*/int val) {
            if (CPU.cpu.mpl == 0) {
                writed_through(addr, val);
                return;
            }
            Exception(addr, true, false);
            Memory.mem_writed(addr, val);
        }
    }

    static private class NewInitPageHandler extends PageHandler {
        public NewInitPageHandler() {
            flags = PFLAG_INIT | PFLAG_NOCODE;
        }

        public /*Bitu*/int readb(/*PhysPt*/int addr) {
            InitPage(addr, false, false);
            return Memory.mem_readb(addr);
        }
        public /*Bitu*/int readw(/*PhysPt*/int addr) {
            InitPage(addr, false, false);
            return Memory.mem_readw(addr);
        }
        public /*Bitu*/int readd(/*PhysPt*/int addr) {
            InitPage(addr, false, false);
            return Memory.mem_readd(addr);
        }

        public void writeb(/*PhysPt*/int addr,/*Bitu*/int val) {
            InitPage(addr, true, false);
    		Memory.mem_writeb(addr, val);
        }

        public void writew(/*PhysPt*/int addr,/*Bitu*/int val) {
            InitPage(addr, true, false);
    		Memory.mem_writew(addr, val);
        }

        public void writed(/*PhysPt*/int addr,/*Bitu*/int val) {
            InitPage(addr, true, false);
    		Memory.mem_writed(addr, val);
        }

        boolean InitPage(/*PhysPt*/int lin_addr, boolean writing, boolean prepare_only) {
            /*Bitu*/
            int lin_page = lin_addr >>> 12;
            /*Bitu*/
            int phys_page;
            if (paging.enabled) {
            //initpage_retry:
                while (true) {
                    X86PageEntry dir_entry = new X86PageEntry(), table_entry = new X86PageEntry();
                    boolean isUser = (((CPU.cpu.cpl & CPU.cpu.mpl)==3)? true:false);

                    // Read the paging stuff, throw not present exceptions if needed
                    // and find out how the page should be mapped
                    /*PhysPt*/int dirEntryAddr = GetPageDirectoryEntryAddr(lin_addr);
                    dir_entry.load((int)Memory.phys_readd(dirEntryAddr));

                    if (dir_entry.block.p==0) {
                        // table pointer is not present, do a page fault
                        PAGING_NewPageFault(lin_addr, dirEntryAddr, prepare_only, (writing? 2:0) | (isUser? 4:0));

                        if (prepare_only) return true;
                        else continue; //goto initpage_retry; // TODO maybe E_Exit after a few loops
                    }
                    /*PhysPt*/int tableEntryAddr = GetPageTableEntryAddr(lin_addr, dir_entry);
                    table_entry.load((int)(Memory.phys_readd(tableEntryAddr)));

                    // set page table accessed (IA manual: A is set whenever the entry is
                    // used in a page translation)
                    if (dir_entry.block.a==0) {
                        dir_entry.block.a = 1;
                        Memory.phys_writed(dirEntryAddr, dir_entry.load());
                    }

                    if (table_entry.block.p==0) {
                        // physpage pointer is not present, do a page fault
                        PAGING_NewPageFault(lin_addr, tableEntryAddr, prepare_only, (writing? 2:0) | (isUser? 4:0));

                        if (prepare_only) return true;
                        else continue; //goto initpage_retry;
                    }
                    //PrintPageInfo("INI",lin_addr,writing,prepare_only);

                    /*Bitu*/int result = translate_array[((dir_entry.load()<<1)&0xc) | ((table_entry.load()>>1)&0x3)];

                    // If a page access right exception occurs we shouldn't change a or d
                    // I'd prefer running into the prepared exception handler but we'd need
                    // an additional handler that sets the 'a' bit - idea - foiler read?
                    /*Bitu*/int ft_index = result | (writing? 8:0) | (isUser? 4:0) | (paging.wp? 16:0);

                    if (fault_table[ft_index]!=0) {
                        // exception error code format:
                        // bit0 - protection violation, bit1 - writing, bit2 - user mode
                        PAGING_NewPageFault(lin_addr, tableEntryAddr, prepare_only, 1 | (writing? 2:0) | (isUser? 4:0));

                        if (prepare_only) return true;
                        else continue; //goto initpage_retry; // unlikely to happen?
                    }

                    // save load to see if it changed later
                    /*Bit32u*/int table_load = table_entry.load();

                    // if we are writing we can set it right here to save some CPU
                    if (writing) table_entry.block.d = 1;

                    // set page accessed
                    table_entry.block.a = 1;

                    // update if needed
                    if (table_load != table_entry.load())
                        Memory.phys_writed(tableEntryAddr, table_entry.load());

                    // if the page isn't dirty and we are reading we need to map the foiler
                    // (dirty = false)
                    boolean dirty = table_entry.block.d!=0;
        /*
                    LOG_MSG("INIT  %s LIN% 8x PHYS% 5x wr%x ch%x wp%x d%x c%x m%x a%x [%x/%x/%x]",
                        mtr[result], lin_addr, table_entry.block.base,
                        writing, prepare_only, paging.wp,
                        dirty, cpu.cpl, cpu.mpl,
                        ((dir_entry.load<<1)&0xc) | ((table_entry.load>>1)&0x3),
                        dirEntryAddr, tableEntryAddr, table_entry.load);
        */
                    // finally install the new page
                    PAGING_LinkPageNew(lin_page, table_entry.block.base, result, dirty);
                    break;
                }
            } else { // paging off
                if (lin_page < LINK_START) phys_page = (int) paging.firstmb[lin_page];
                else phys_page = lin_page;
                PAGING_LinkPage(lin_page, phys_page);
            }
            return false;
        }
    }


    static public boolean PAGING_MakePhysPage(/*Bitu*/IntRef page) {
        if (paging.enabled) {
            /*Bitu*/
            int d_index = page.value >>> 10;
            /*Bitu*/
            int t_index = page.value & 0x3ff;
            X86PageEntry table = new X86PageEntry();
            table.load((int) (Memory.phys_readd((paging.base.page << 12) + d_index * 4)));
            if (table.block.p == 0) return false;
            X86PageEntry entry = new X86PageEntry();
            entry.load((int) (Memory.phys_readd((table.block.base << 12) + t_index * 4)));
            if (entry.block.p == 0) return false;
            page.value = entry.block.base;
        } else {
            if (page.value < LINK_START) page.value = (int) paging.firstmb[page.value];
            //Else keep it the same
        }
        return true;
    }

    static NewInitPageHandler init_page_handler = new NewInitPageHandler();
    static ExceptionPageHandler exception_handler = new ExceptionPageHandler();
    static PageFoilHandler foiling_handler = new PageFoilHandler();

    static public /*Bitu*/int PAGING_GetDirBase() {
        return paging.cr3;
    }

    private static void PAGING_InitTLB() {
        for (/*Bitu*/int i = 0; i < TLB_SIZE; i++) {
            paging.tlb.read[i] = Integer.MIN_VALUE;
            paging.tlb.write[i] = Integer.MIN_VALUE;
            paging.tlb.readhandler[i] = init_page_handler;
            paging.tlb.writehandler[i] = init_page_handler;
        }
        paging.ur_links.used=0;
	    paging.krw_links.used=0;
	    paging.kr_links.used=0;
        paging.links.used = 0;
    }

    public static void PAGING_ClearTLB() {
        for (int i = 0; paging.links.used > 0; paging.links.used--, i++) {
            /*Bitu*/
            int page = paging.links.entries[i];
            paging.tlb.read[page] = Integer.MIN_VALUE;
            paging.tlb.write[page] = Integer.MIN_VALUE;
            paging.tlb.readhandler[page] = init_page_handler;
            paging.tlb.writehandler[page] = init_page_handler;
        }
        paging.ur_links.used=0;
	    paging.krw_links.used=0;
	    paging.kr_links.used=0;
        paging.links.used = 0;
    }

    public static void PAGING_UnlinkPages(/*Bitu*/int lin_page,/*Bitu*/int pages) {
        for (; pages > 0; pages--) {
            paging.tlb.read[lin_page] = Integer.MIN_VALUE;
            paging.tlb.write[lin_page] = Integer.MIN_VALUE;
            paging.tlb.readhandler[lin_page] = init_page_handler;
            paging.tlb.writehandler[lin_page] = init_page_handler;
            lin_page++;
        }
    }

    public static void PAGING_MapPage(/*Bitu*/int lin_page,/*Bitu*/int phys_page) {
        if (lin_page < LINK_START) {
            paging.firstmb[lin_page] = phys_page;
            paging.tlb.read[lin_page] = Integer.MIN_VALUE;
            paging.tlb.write[lin_page] = Integer.MIN_VALUE;
            paging.tlb.readhandler[lin_page] = init_page_handler;
            paging.tlb.writehandler[lin_page] = init_page_handler;
        } else {
            PAGING_LinkPage(lin_page, phys_page);
        }
    }

    private static void PAGING_LinkPageNew(/*Bitu*/int lin_page, /*Bitu*/int phys_page, /*Bitu*/int linkmode, boolean dirty) {
        /*Bitu*/int xlat_index = linkmode | (paging.wp? 8:0) | ((CPU.cpu.cpl==3)? 4:0);
        /*Bit8u*/int outcome = xlat_mapping[xlat_index];

        // get the physpage handler we are going to map
        PageHandler handler=Memory.MEM_GetPageHandler(phys_page);
        /*Bitu*/int lin_base=lin_page << 12;

    //	LOG_MSG("MAPPG %s",lnm[outcome]);

        if (lin_page>=TLB_SIZE || phys_page>=TLB_SIZE)
            Log.exit("Illegal page");
        if (paging.links.used>=PAGING_LINKS) {
            Log.log(LogTypes.LOG_PAGING,LogSeverities.LOG_NORMAL, "Not enough paging links, resetting cache");
            PAGING_ClearTLB();
        }
        // re-use some of the unused bits in the phys_page variable
        // needed in the exception handler and foiler so they can replace themselves appropriately
        // bit31-30 ACMAP_
        // bit29	dirty
        // these bits are shifted off at the places paging.tlb.phys_page is read
        paging.tlb.phys_page[lin_page]= phys_page | (linkmode<< 30) | (dirty? PHYSPAGE_DITRY:0);
        switch(outcome) {
        case ACMAP_RW:
            // read
            if ((handler.flags & PFLAG_READABLE)!=0)
                paging.tlb.read[lin_page] = handler.GetHostReadPt(phys_page)-lin_base;
            else
                paging.tlb.read[lin_page]=Integer.MIN_VALUE;
            paging.tlb.readhandler[lin_page]=handler;

            // write
            if (dirty) { // in case it is already dirty we don't need to check
                if ((handler.flags & PFLAG_WRITEABLE)!=0)
                    paging.tlb.write[lin_page] = handler.GetHostWritePt(phys_page)-lin_base;
                else
                    paging.tlb.write[lin_page]=Integer.MIN_VALUE;
                paging.tlb.writehandler[lin_page]=handler;
            } else {
                paging.tlb.writehandler[lin_page]= foiling_handler;
                paging.tlb.write[lin_page]=Integer.MIN_VALUE;
            }
            break;
        case ACMAP_RE:
            // read
            if ((handler.flags & PFLAG_READABLE)!=0)
                paging.tlb.read[lin_page] = handler.GetHostReadPt(phys_page)-lin_base;
            else
                paging.tlb.read[lin_page]=Integer.MIN_VALUE;
            paging.tlb.readhandler[lin_page]=handler;
            // exception
            paging.tlb.writehandler[lin_page]= exception_handler;
            paging.tlb.write[lin_page]=Integer.MIN_VALUE;
            break;
        case ACMAP_EE:
            paging.tlb.readhandler[lin_page]= exception_handler;
            paging.tlb.writehandler[lin_page]= exception_handler;
            paging.tlb.read[lin_page]=Integer.MIN_VALUE;
            paging.tlb.write[lin_page]=Integer.MIN_VALUE;
            break;
        }

        switch(linkmode) {
        case ACCESS_KR:
            paging.kr_links.entries[paging.kr_links.used++]=lin_page;
            break;
        case ACCESS_KRW:
            paging.krw_links.entries[paging.krw_links.used++]=lin_page;
            break;
        case ACCESS_UR:
            paging.ur_links.entries[paging.ur_links.used++]=lin_page;
            break;
        case ACCESS_URW:	// with this access right everything is possible
                            // thus no need to modify it on a us <-> sv switch
            break;
        }
        paging.links.entries[paging.links.used++]=lin_page; // "master table"
    }

    static private void PAGING_LinkPage(/*Bitu*/int lin_page,/*Bitu*/int phys_page) {
        PageHandler handler = Memory.MEM_GetPageHandler(phys_page);
        /*Bitu*/
        int lin_base = lin_page << 12;
        if (lin_page >= TLB_SIZE || phys_page >= TLB_SIZE)
            Log.exit("Illegal page");

        if (paging.links.used >= PAGING_LINKS) {
            Log.log(LogTypes.LOG_PAGING, LogSeverities.LOG_NORMAL, "Not enough paging links, resetting cache");
            PAGING_ClearTLB();
        }

        paging.tlb.phys_page[lin_page] = phys_page;
        if ((handler.flags & PFLAG_READABLE) != 0)
            paging.tlb.read[lin_page] = handler.GetHostReadPt(phys_page) - lin_base;
        else paging.tlb.read[lin_page] = Integer.MIN_VALUE;
        if ((handler.flags & PFLAG_WRITEABLE) != 0) {
            paging.tlb.write[lin_page] = handler.GetHostWritePt(phys_page) - lin_base;
        }
        else paging.tlb.write[lin_page] = Integer.MIN_VALUE;

        paging.links.entries[paging.links.used++] = lin_page;
        paging.tlb.readhandler[lin_page] = handler;
        paging.tlb.writehandler[lin_page] = handler;
    }

     // parameter is the new cpl mode
    public static void PAGING_SwitchCPL(boolean isUser) {
        //	LOG_MSG("SWCPL u%d kr%d, krw%d, ur%d",
        //		isUser, paging.kro_links.used, paging.krw_links.used, paging.ure_links.used);

        // this function is worth optimizing
        // some of this cold be pre-stored?

        // krw - same for WP1 and WP0
        if (isUser) {
            // sv -> us: rw -> ee
            for(/*Bitu*/int i = 0; i < paging.krw_links.used; i++) {
                /*Bitu*/int tlb_index = paging.krw_links.entries[i];
                paging.tlb.readhandler[tlb_index] = exception_handler;
                paging.tlb.writehandler[tlb_index] = exception_handler;
                paging.tlb.read[tlb_index] = Integer.MIN_VALUE;
                paging.tlb.write[tlb_index] = Integer.MIN_VALUE;
            }
        } else {
            // us -> sv: ee -> rw
            for(/*Bitu*/int i = 0; i < paging.krw_links.used; i++) {
                /*Bitu*/int tlb_index = paging.krw_links.entries[i];
                /*Bitu*/int phys_page = paging.tlb.phys_page[tlb_index];
                /*Bitu*/int lin_base = tlb_index << 12;
                boolean dirty = (phys_page & PHYSPAGE_DITRY)!=0;
                phys_page &= PHYSPAGE_ADDR;
                PageHandler handler = Memory.MEM_GetPageHandler(phys_page);

                // map read handler
                paging.tlb.readhandler[tlb_index] = handler;
                if ((handler.flags & PFLAG_READABLE)!=0)
                    paging.tlb.read[tlb_index] = handler.GetHostReadPt(phys_page)-lin_base;
                else paging.tlb.read[tlb_index] = Integer.MIN_VALUE;

                // map write handler
                if (dirty) {
                    paging.tlb.writehandler[tlb_index] = handler;
                    if ((handler.flags & PFLAG_WRITEABLE)!=0)
                        paging.tlb.write[tlb_index] = handler.GetHostWritePt(phys_page)-lin_base;
                    else paging.tlb.write[tlb_index] = Integer.MIN_VALUE;
                } else {
                    paging.tlb.writehandler[tlb_index] = foiling_handler;
                    paging.tlb.write[tlb_index] = Integer.MIN_VALUE;
                }
            }
        }

        if (paging.wp) {
            // ur: no change with WP=1
            // kr
            if (isUser) {
                // sv -> us: re -> ee
                for(/*Bitu*/int i = 0; i < paging.kr_links.used; i++) {
                    /*Bitu*/int tlb_index = paging.kr_links.entries[i];
                    paging.tlb.readhandler[tlb_index] = exception_handler;
                    paging.tlb.read[tlb_index] = Integer.MIN_VALUE;
                }
            } else {
                // us -> sv: ee -> re
                for(/*Bitu*/int i = 0; i < paging.kr_links.used; i++) {
                    /*Bitu*/int tlb_index = paging.kr_links.entries[i];
                    /*Bitu*/int lin_base = tlb_index << 12;
                    /*Bitu*/int phys_page = paging.tlb.phys_page[tlb_index] & PHYSPAGE_ADDR;
                    PageHandler handler = Memory.MEM_GetPageHandler(phys_page);
                    paging.tlb.readhandler[tlb_index] = handler;
                    if ((handler.flags & PFLAG_READABLE)!=0)
                        paging.tlb.read[tlb_index] = handler.GetHostReadPt(phys_page)-lin_base;
                    else paging.tlb.read[tlb_index] = Integer.MIN_VALUE;
                }
            }
        } else { // WP=0
            // ur
            if (isUser) {
                // sv -> us: rw -> re
                for(/*Bitu*/int i = 0; i < paging.ur_links.used; i++) {
                    /*Bitu*/int tlb_index = paging.ur_links.entries[i];
                    paging.tlb.writehandler[tlb_index] = exception_handler;
                    paging.tlb.write[tlb_index] = Integer.MIN_VALUE;
                }
            } else {
                // us -> sv: re -> rw
                for(/*Bitu*/int i = 0; i < paging.ur_links.used; i++) {
                    /*Bitu*/int tlb_index = paging.ur_links.entries[i];
                    /*Bitu*/int phys_page = paging.tlb.phys_page[tlb_index];
                    boolean dirty = (phys_page & PHYSPAGE_DITRY)!=0;
                    phys_page &= PHYSPAGE_ADDR;
                    PageHandler handler = Memory.MEM_GetPageHandler(phys_page);
                    if (dirty) {
                        /*Bitu*/int lin_base = tlb_index << 12;
                        paging.tlb.writehandler[tlb_index] = handler;
                        if ((handler.flags & PFLAG_WRITEABLE)!=0)
                            paging.tlb.write[tlb_index] = handler.GetHostWritePt(phys_page)-lin_base;
                        else paging.tlb.write[tlb_index] = Integer.MIN_VALUE;
                    } else {
                        paging.tlb.writehandler[tlb_index] = foiling_handler;
                        paging.tlb.write[tlb_index] = Integer.MIN_VALUE;
                    }
                }
            }
        }
    }

    public static void PAGING_SetDirBase(/*Bitu*/int cr3) {
        paging.cr3 = cr3;

        paging.base.page = cr3 >>> 12;
        paging.base.addr=cr3 & ~0xFFF;
//	Log.log(LogTypes.LOG_PAGING,LogSeverities.LOG_NORMAL,"CR3:%X Base %X",cr3,paging.base.page);
        if (paging.enabled) {
            PAGING_ClearTLB();
        }
    }

    public static void PAGING_SetWP(boolean wp) {
	    paging.wp = wp;
	    if (paging.enabled)
		    PAGING_ClearTLB();
    }

    public static void PAGING_Enable(boolean enabled) {
        /* If paging is disable we work from a default paging table */
        if (paging.enabled == enabled) return;
        paging.enabled = enabled;
        if (enabled) {
//		Log.log(LogTypes.LOG_PAGING,LogSeverities.LOG_NORMAL,"Enabled");
            PAGING_SetDirBase(paging.cr3);
        }
        PAGING_ClearTLB();
    }

    public static boolean PAGING_Enabled() {
        return paging.enabled;
    }

    static private Paging test;

    public Paging(Section configuration) {
        super(configuration);
        paging = new PagingBlock();
        paging.enabled = false;
        paging.wp=false;
        PAGING_InitTLB();
        /*Bitu*/
        int i;
        for (i = 0; i < LINK_START; i++) {
            paging.firstmb[i] = i;
        }
        pf_queue.used = 0;
    }

    public static Section.SectionFunction PAGING_ShutDown = new Section.SectionFunction() {
        public void call(Section section) {
            paging = null;
        }
    };

    public static Section.SectionFunction PAGING_Init = new Section.SectionFunction() {
        public void call(Section section) {
            test = new Paging(section);
            section.AddDestroyFunction(PAGING_ShutDown);
        }
    };
}
