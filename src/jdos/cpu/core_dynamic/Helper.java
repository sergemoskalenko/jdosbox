package jdos.cpu.core_dynamic;

import jdos.cpu.CPU_Regs;
import jdos.cpu.Core_dynamic;
import jdos.hardware.Memory;
import jdos.misc.Log;

public class Helper extends CPU_Regs {
    static private Core_dynamic.CodePageHandlerDynRecRef codeRef = new Core_dynamic.CodePageHandlerDynRecRef();
    public static final DynDecode decode = new DynDecode();
    static protected boolean EA16 = false;
    static protected int prefixes = 0;
    static protected int opcode_index = 0;

    static protected final long[] AddrMaskTable={0x0000ffffl,0xffffffffl};
    static protected final int[] AddrMaskTable1={0x0000ffff,0xffffffff};
    
    protected static final int OPCODE_NONE=0x000;
    protected static final int OPCODE_0F=0x100;
    protected static final int OPCODE_SIZE=0x200;
    
    static final protected boolean CPU_TRAP_CHECK = true;
    static protected final boolean CPU_PIC_CHECK = true;

    public final static int RESULT_HANDLED = 0;
    public final static int RESULT_ILLEGAL_INSTRUCTION = 1;
    public final static int RESULT_CALLBACK = 2;
    public final static int RESULT_CONTINUE = 3;
    public final static int RESULT_RETURN = 4;
    public final static int RESULT_ANOTHER = 5;
    public final static int RESULT_JUMP = 6;
    public final static int RESULT_CONTINUE_SEG = 7;

    // fetch the next byte of the instruction stream
    static /*Bit8u*/byte decode_fetchbs() {
        return (byte)decode_fetchb();
    }
    static class ChangePageException extends RuntimeException {
    }

    static /*Bit8u*/short decode_fetchb() {
        if (decode.page.index>=4096) {
            throw new ChangePageException();
            //decode_advancepage();
        }
        if (decode.page.invmap!=null && decode.page.invmap.p[decode.page.index]>=4) {
            decode.modifiedAlot = true;
        }
        decode.page.wmap.p[decode.page.index]+=0x01;
        decode.page.index++;
        decode.code+=1;
        return Memory.mem_readb(decode.code-1);
    }
    static void decode_putback(int count) {
        for (int i=0;i<count;i++) {
            decode.page.index--;
            // :TODO: handle page change
            if (decode.page.index<0) {
                Log.exit("Dynamic Core:  Self modifying code across page boundries not implemented yet");
            }
            decode.page.wmap.p[decode.page.index]-=0x01;
        }
    }
    static short decode_fetchws() {
        return (short)decode_fetchw();
    }
    // fetch the next word of the instruction stream
    static /*Bit16u*/int decode_fetchw() {
        if (decode.page.index>=4095) {
            /*Bit16u*/int val=decode_fetchb();
            val|=decode_fetchb() << 8;
            return val;
        }
        if (decode.page.invmap!=null && (decode.page.invmap.p[decode.page.index]>=4 || decode.page.invmap.p[decode.page.index+1]>=4)) {
            decode.modifiedAlot = true;
        }
        decode.page.wmap.p[decode.page.index]+=0x01;
        decode.page.wmap.p[decode.page.index+1]+=0x01;
        decode.code+=2;decode.page.index+=2;
        return Memory.mem_readw(decode.code-2);
    }
    static int decode_fetchds() {
        return decode_fetchd();
    }

    // fetch the next dword of the instruction stream
    static /*Bit32u*/int decode_fetchd() {
        if (decode.page.index>=4093) {
            /*Bit32u*/int val=decode_fetchb();
            val|=decode_fetchb() << 8;
            val|=decode_fetchb() << 16;
            val|=(long)decode_fetchb() << 24;
            return val;
            /* Advance to the next page */
        }
        if (decode.page.invmap!=null && (decode.page.invmap.p[decode.page.index]>=4 || decode.page.invmap.p[decode.page.index+1]>=4 || decode.page.invmap.p[decode.page.index+2]>=4 || decode.page.invmap.p[decode.page.index+3]>=4)) {
            decode.modifiedAlot = true;
        }
        decode.page.wmap.p[decode.page.index]+=0x01;
        decode.page.wmap.p[decode.page.index+1]+=0x01;
        decode.page.wmap.p[decode.page.index+2]+=0x01;
        decode.page.wmap.p[decode.page.index+3]+=0x01;
        decode.code+=4;decode.page.index+=4;
        return Memory.mem_readd(decode.code - 4);
    }
}