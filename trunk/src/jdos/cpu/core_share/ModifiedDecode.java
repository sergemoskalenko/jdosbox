package jdos.cpu.core_share;

import jdos.cpu.*;
import jdos.cpu.core_dynamic.Op;
import jdos.cpu.core_normal.Prefix_helpers;

public class ModifiedDecode {
    static public int call(int opcode_index, int prefixes, boolean EA16) {
        Core.cseip = CPU.Segs_CSphys + CPU_Regs.reg_eip;
        Core.prefixes = prefixes;
        Table_ea.EA16 = EA16;
        while (true) {
            int c = opcode_index + Core.Fetchb.call();
//                    last = c;
//                    Debug.start(Debug.TYPE_CPU, c);
//                    try {
            try {
                int result = jdos.cpu.core_normal.Prefix_none.ops[c].call();
                if (result != Prefix_helpers.HANDLED) {
                    if (result == Prefix_helpers.CONTINUE) {
                        break;
                    } else if (result == Prefix_helpers.RETURN) {
                        Data.callback = jdos.cpu.core_normal.Prefix_none.returnValue;
                        return Constants.BR_CallBack;
                    } else if (result == Prefix_helpers.RESTART) {
                        continue;
                    } else if (result == Prefix_helpers.CBRET_NONE) {
                        return Constants.BR_CBRet_None;
                    } else if (result == Prefix_helpers.DECODE_END) {
                        Prefix_helpers.SAVEIP();
                        Flags.FillFlags();
                        return Constants.BR_CBRet_None;
                    } else if (result == Prefix_helpers.NOT_HANDLED || result == Prefix_helpers.ILLEGAL_OPCODE) {
                        CPU.CPU_Exception(6, 0);
                        break;
                    }
                }
                // necessary for Prefix_helpers.EXCEPTION
            } catch (Prefix_helpers.ContinueException e) {
                break;
            }
//                    } finally {
//                        Debug.stop(Debug.TYPE_CPU, c);
//                    }

            // inlined
            // SAVEIP();
            CPU_Regs.reg_eip = Core.cseip - CPU.Segs_CSphys;
            break;
        }
        return Constants.BR_Jump;
    }
}