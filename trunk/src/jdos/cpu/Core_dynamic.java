package jdos.cpu;

import jdos.cpu.core_dynamic.*;
import jdos.cpu.core_share.Constants;
import jdos.cpu.core_share.Data;
import jdos.misc.setup.Config;
import jdos.misc.Log;
import jdos.debug.Debug;
import jdos.hardware.Pic;

public class Core_dynamic {
    static public final int CACHE_MAXSIZE = 4096*2;
    static public final int CACHE_PAGES	= 512;
    static public final int CACHE_BLOCKS = 128*1024;
    static public final int CACHE_ALIGN = 16;
    static public final int DYN_HASH_SHIFT = 4;
    static public final int DYN_PAGE_HASH = 4096>>DYN_HASH_SHIFT;
    static public final int DYN_LINKS = 16;

    // identificator to signal self-modification of the currently executed block
    static public final int SMC_CURRENT_BLOCK = 0xffff;

    static public int instruction_count = 128;

    public static void CPU_Core_Dynamic_Init() {
    }

    static public void CPU_Core_Dynamic_Cache_Init(boolean enable_cache) {
        Cache.cache_init(enable_cache);
    }

    static public void CPU_Core_Dynamic_Cache_Close() {

    }
    public static final class CodePageHandlerDynRecRef {
        public CodePageHandlerDynRec value;
    }
    static private final CodePageHandlerDynRecRef chandlerRef = new  CodePageHandlerDynRecRef();

    public static final CPU.CPU_Decoder CPU_Core_Dynrec_Trap_Run = new CPU.CPU_Decoder() {
        public /*Bits*/int call() {
            /*Bits*/int oldCycles = CPU.CPU_Cycles;
            CPU.CPU_Cycles = 1;
            CPU.cpu.trap_skip = false;

            // let the normal core execute the next (only one!) instruction
            /*Bits*/int ret=Core_normal.CPU_Core_Normal_Run.call();

            // trap to int1 unless the last instruction deferred this
            // (allows hardware interrupts to be served without interaction)
            if (!CPU.cpu.trap_skip) CPU.CPU_HW_Interrupt(1);

            CPU.CPU_Cycles = oldCycles-1;
            // continue (either the trapflag was clear anyways, or the int1 cleared it)
            CPU.cpudecoder = CPU_Core_Dynamic_Run;

            return ret;
        }
    };

    private static CacheBlockDynRec LinkBlocks(CacheBlockDynRec running, /*BlockReturn*/int ret) {
        CacheBlockDynRec block=null;
        // the last instruction was a control flow modifying instruction
        /*Bitu*/long temp_ip=CPU.Segs_CSphys+CPU_Regs.reg_eip;
        Paging.PageHandler handler = Paging.get_tlb_readhandler((int)temp_ip);
        if (handler instanceof CodePageHandlerDynRec) {
            CodePageHandlerDynRec temp_handler=(CodePageHandlerDynRec)handler;
            if ((temp_handler.flags & Paging.PFLAG_HASCODE)!=0) {
                // see if the target is an already translated block
                block=temp_handler.FindCacheBlock((int)(temp_ip & 4095));
                if (block==null) return null;

                // found it, link the current block to
                running.LinkTo(ret==Constants.BR_Link2?1:0,block);
                return block;
            }
        }
        return null;
    }

    public static final CPU.CPU_Decoder CPU_Core_Dynamic_Run = new CPU.CPU_Decoder() {
        public /*Bits*/int call() {
            Core.base_ds=CPU.Segs_DSphys;
            Core.base_ss=CPU.Segs_SSphys;
            Core.base_val_ds=CPU_Regs.ds;
            while (CPU.CPU_Cycles>0) {
                // Determine the linear address of CS:EIP
                /*PhysPt*/long ip_point=(CPU.Segs_CSphys+ CPU_Regs.reg_eip()) & 0xFFFFFFFFl;

                Paging.PageHandler handler=Paging.get_tlb_readhandler((int)ip_point);
                CodePageHandlerDynRec chandler=null;
                int page_ip_point = (int)ip_point&4095;

                if (handler != null && handler instanceof CodePageHandlerDynRec)
                    chandler = (CodePageHandlerDynRec)handler;
                if (chandler == null) {
                    // see if the current page is present and contains code
                    if (Decoder_basic.MakeCodePage(ip_point, chandlerRef)) {
                        // page not present, throw the exception
                        CPU.CPU_Exception(CPU.cpu.exception.which,CPU.cpu.exception.error);
                        continue;
                    }
                    chandler = chandlerRef.value;
                }
                // page doesn't contain code or is special
                if (chandler==null)
                    return Core_normal.CPU_Core_Normal_Run.call();

                // find correct Dynamic Block to run
                CacheBlockDynRec block=chandler.FindCacheBlock(page_ip_point);
                if (block==null) {
                    // no block found, thus translate the instruction stream
                    // unless the instruction is known to be modified
                    if (chandler.invalidation_map==null || (chandler.invalidation_map.p[page_ip_point]<4)) {
                        // translate up to 32 instructions
                        block= Decoder.CreateCacheBlock(chandler,ip_point,instruction_count);
                    } else {
                        // let the normal core handle this instruction to avoid zero-sized blocks
                        /*Bitu*/int old_cycles=CPU.CPU_Cycles;
                        CPU.CPU_Cycles=1;
                        /*Bits*/int nc_retcode=Core_normal.CPU_Core_Normal_Run.call();
                        if (nc_retcode==0) {
                            CPU.CPU_Cycles=old_cycles-1;
                            continue;
                        }
                        CPU.CPU_CycleLeft+=old_cycles;
                        return nc_retcode;
                    }
                }

        //run_block:
                while (true) {
                    // now we're ready to run the dynamic code block
            //		BlockReturn ret=((BlockReturn (*)(void))(block->cache.start))();
                    /*BlockReturn*/int ret=block.code.call2();

                    switch (ret) {
                    case Constants.BR_CBRet_None:
                        return Callback.CBRET_NONE;

                    case Constants.BR_Normal:
                    case Constants.BR_Jump:
                        // the block was exited due to a non-predictable control flow
                        // modifying instruction (like ret) or some nontrivial cpu state
                        // changing instruction (for example switch to/from pmode),
                        // or the maximal number of instructions to translate was reached
                        if (Config.C_HEAVY_DEBUG)
                            if (Debug.DEBUG_HeavyIsBreakpoint()) return Debug.debugCallback;
                        break;

                    case Constants.BR_CallBack:
                        // the callback code is executed in dosbox.conf, return the callback number
                        Flags.FillFlags();
                        return Data.callback;
                    case Constants.BR_Link1:
                    case Constants.BR_Link2:
                    {
                        CacheBlockDynRec next = block.link[ret==Constants.BR_Link2?1:0].to;
                        if (next == null)
                            block=LinkBlocks(block, ret);
                        else
                            block = next;
                        if (block!=null && CPU.CPU_Cycles>0) continue;
                        break;
                    }
                    case Constants.BR_Illegal:
                        CPU.CPU_Exception(6,0);
                        break;
                    default:
                        Log.exit("Invalid return code "+ret);
                    }
                    break;
                }
            }
            Flags.FillFlags();
            return Callback.CBRET_NONE;
        }
    };
}
