package jdos.hardware;

import jdos.Dosbox;
import jdos.misc.Log;
import jdos.misc.setup.Section;
import jdos.types.LogSeverities;
import jdos.types.LogTypes;
import jdos.types.MachineType;

public class Keyboard {
    public static final class KBD_KEYS {
        static public final int KBD_NONE=0;
        static public final int KBD_1=1;
        static public final int KBD_2=2;
        static public final int KBD_3=3;
        static public final int KBD_4=4;
        static public final int KBD_5=5;
        static public final int KBD_6=6;
        static public final int KBD_7=7;
        static public final int KBD_8=8;
        static public final int KBD_9=9;
        static public final int KBD_0=10;
        static public final int KBD_q=11;
        static public final int KBD_w=12;
        static public final int KBD_e=13;
        static public final int KBD_r=14;
        static public final int KBD_t=15;
        static public final int KBD_y=16;
        static public final int KBD_u=17;
        static public final int KBD_i=18;
        static public final int KBD_o=19;
        static public final int KBD_p=20;
        static public final int KBD_a=21;
        static public final int KBD_s=22;
        static public final int KBD_d=23;
        static public final int KBD_f=24;
        static public final int KBD_g=25;
        static public final int KBD_h=26;
        static public final int KBD_j=27;
        static public final int KBD_k=28;
        static public final int KBD_l=29;
        static public final int KBD_z=30;
        static public final int KBD_x=31;
        static public final int KBD_c=32;
        static public final int KBD_v=33;
        static public final int KBD_b=34;
        static public final int KBD_n=35;
        static public final int KBD_m=36;
        static public final int KBD_f1=37;
        static public final int KBD_f2=38;
        static public final int KBD_f3=39;
        static public final int KBD_f4=40;
        static public final int KBD_f5=41;
        static public final int KBD_f6=42;
        static public final int KBD_f7=43;
        static public final int KBD_f8=44;
        static public final int KBD_f9=45;
        static public final int KBD_f10=46;
        static public final int KBD_f11=47;
        static public final int KBD_f12=48;

        /*Now the weirder keys */

        static public final int KBD_esc=49;
        static public final int KBD_tab=50;
        static public final int KBD_backspace=51;
        static public final int KBD_enter=52;
        static public final int KBD_space=53;
        static public final int KBD_leftalt=54;
        static public final int KBD_rightalt=55;
        static public final int KBD_leftctrl=56;
        static public final int KBD_rightctrl=57;
        static public final int KBD_leftshift=58;
        static public final int KBD_rightshift=59;
        static public final int KBD_capslock=60;
        static public final int KBD_scrolllock=61;
        static public final int KBD_numlock=62;

        static public final int KBD_grave=63;
        static public final int KBD_minus=64;
        static public final int KBD_equals=65;
        static public final int KBD_backslash=66;
        static public final int KBD_leftbracket=67;
        static public final int KBD_rightbracket=68;
        static public final int KBD_semicolon=69;
        static public final int KBD_quote=70;
        static public final int KBD_period=71;
        static public final int KBD_comma=72;
        static public final int KBD_slash=73;
        static public final int KBD_extra_lt_gt=74;

        static public final int KBD_printscreen=75;
        static public final int KBD_pause=76;
        static public final int KBD_insert=77;
        static public final int KBD_home=78;
        static public final int KBD_pageup=79;
        static public final int KBD_delete=80;
        static public final int KBD_end=81;
        static public final int KBD_pagedown=82;
        static public final int KBD_left=83;
        static public final int KBD_up=84;
        static public final int KBD_down=85;
        static public final int KBD_right=86;

        static public final int KBD_kp1=87;
        static public final int KBD_kp2=88;
        static public final int KBD_kp3=89;
        static public final int KBD_kp4=90;
        static public final int KBD_kp5=91;
        static public final int KBD_kp6=92;
        static public final int KBD_kp7=93;
        static public final int KBD_kp8=94;
        static public final int KBD_kp9=95;
        static public final int KBD_kp0=96;
        static public final int KBD_kpdivide=97;
        static public final int KBD_kpmultiply=98;
        static public final int KBD_kpminus=99;
        static public final int KBD_kpplus=100;
        static public final int KBD_kpenter=101;
        static public final int KBD_kpperiod=102;


        static public final int KBD_LAST=103;
    }
    static private final int KEYBUFSIZE = 32;
    static private final float KEYDELAY = 0.300f;			//Considering 20-30 khz serial clock and 11 bits/char

    static final private class KeyCommands {
        static public final int CMD_NONE=0;
        static public final int CMD_SETLEDS=1;
        static public final int CMD_SETTYPERATE=2;
        static public final int CMD_SETOUTPORT=3;
    }

    private static class Keyb {
        /*Bit8u*/byte[] buffer=new byte[KEYBUFSIZE];
        /*Bitu*/int used;
        /*Bitu*/int pos;
        static public class Repeat {
            int key;
            /*Bitu*/int wait;
            /*Bitu*/int pause,rate;
        }
        Repeat repeat = new Repeat();
        int command;
        /*Bit8u*/short p60data;
        boolean p60changed;
        boolean active;
        boolean scanning;
        boolean scheduled;
    }

    private static Keyb keyb = new Keyb();
    private static void KEYBOARD_SetPort60(/*Bit8u*/short val) {
        keyb.p60changed=true;
        keyb.p60data=val;
        if (Dosbox.machine== MachineType.MCH_PCJR) Pic.PIC_ActivateIRQ(6);
        else Pic.PIC_ActivateIRQ(1);
    }

    private static Pic.PIC_EventHandler KEYBOARD_TransferBuffer = new Pic.PIC_EventHandler() {
        public void call(/*Bitu*/int val) {
            keyb.scheduled=false;
            if (keyb.used==0) {
                Log.log(LogTypes.LOG_KEYBOARD, LogSeverities.LOG_NORMAL,"Transfer started with empty buffer");
                return;
            }
            KEYBOARD_SetPort60((short)(keyb.buffer[keyb.pos] & 0xFF));
            if (++keyb.pos>=KEYBUFSIZE) keyb.pos-=KEYBUFSIZE;
            keyb.used--;
        }
        public String toString() {
            return "KEYBOARD_TransferBuffer";
        }
    };


    public static void KEYBOARD_ClrBuffer() {
        keyb.used=0;
        keyb.pos=0;
        Pic.PIC_RemoveEvents(KEYBOARD_TransferBuffer);
        keyb.scheduled=false;
    }

    private static void KEYBOARD_AddBuffer(/*Bit8u*/int data) {
        if (keyb.used>=KEYBUFSIZE) {
            Log.log(LogTypes.LOG_KEYBOARD, LogSeverities.LOG_NORMAL,"Buffer full, dropping code");
            return;
        }
        /*Bitu*/int start=keyb.pos+keyb.used;
        if (start>=KEYBUFSIZE) start-=KEYBUFSIZE;
        keyb.buffer[start]=(byte)data;
        keyb.used++;
        /* Start up an event to start the first IRQ */
        if (!keyb.scheduled && !keyb.p60changed) {
            keyb.scheduled=true;
            Pic.PIC_AddEvent(KEYBOARD_TransferBuffer,KEYDELAY);
        }
    }

    private static IoHandler.IO_ReadHandler read_p60 = new IoHandler.IO_ReadHandler() {
        public /*Bitu*/int call(/*Bitu*/int port, /*Bitu*/int iolen) {
            keyb.p60changed=false;
            if (!keyb.scheduled && keyb.used!=0) {
                keyb.scheduled=true;
                Pic.PIC_AddEvent(KEYBOARD_TransferBuffer,KEYDELAY);
            }
            return keyb.p60data;
        }
    };

    private static IoHandler.IO_WriteHandler write_p60 = new IoHandler.IO_WriteHandler() {
        public void call(/*Bitu*/int port, /*Bitu*/int val, /*Bitu*/int iolen) {
            switch (keyb.command) {
            case KeyCommands.CMD_NONE:	/* None */
                /* No active command this would normally get sent to the keyboard then */
                KEYBOARD_ClrBuffer();
                switch (val) {
                case 0xed:	/* Set Leds */
                    keyb.command=KeyCommands.CMD_SETLEDS;
                    KEYBOARD_AddBuffer(0xfa);	/* Acknowledge */
                    break;
                case 0xee:	/* Echo */
                    KEYBOARD_AddBuffer(0xfa);	/* Acknowledge */
                    break;
                case 0xf2:	/* Identify keyboard */
                    /* AT's just send acknowledge */
                    KEYBOARD_AddBuffer(0xfa);	/* Acknowledge */
                    break;
                case 0xf3: /* Typematic rate programming */
                    keyb.command=KeyCommands.CMD_SETTYPERATE;
                    KEYBOARD_AddBuffer(0xfa);	/* Acknowledge */
                    break;
                case 0xf4:	/* Enable keyboard,clear buffer, start scanning */
                    Log.log(LogTypes.LOG_KEYBOARD, LogSeverities.LOG_NORMAL,"Clear buffer,enable Scaning");
                    KEYBOARD_AddBuffer(0xfa);	/* Acknowledge */
                    keyb.scanning=true;
                    break;
                case 0xf5:	 /* Reset keyboard and disable scanning */
                    Log.log(LogTypes.LOG_KEYBOARD, LogSeverities.LOG_NORMAL,"Reset, disable scanning");
                    keyb.scanning=false;
                    KEYBOARD_AddBuffer(0xfa);	/* Acknowledge */
                    break;
                case 0xf6:	/* Reset keyboard and enable scanning */
                    Log.log(LogTypes.LOG_KEYBOARD, LogSeverities.LOG_NORMAL,"Reset, enable scanning");
                    KEYBOARD_AddBuffer(0xfa);	/* Acknowledge */
                    keyb.scanning=false;
                    break;
                default:
                    /* Just always acknowledge strange commands */
                    if (Log.level<=LogSeverities.LOG_ERROR) Log.log(LogTypes.LOG_KEYBOARD, LogSeverities.LOG_ERROR,"60:Unhandled command "+Integer.toString(val,16));
                    KEYBOARD_AddBuffer(0xfa);	/* Acknowledge */
                }
                return;
            case KeyCommands.CMD_SETOUTPORT:
                Memory.MEM_A20_Enable((val & 2)>0);
                keyb.command = KeyCommands.CMD_NONE;
                break;
            case KeyCommands.CMD_SETTYPERATE:
                {
                    final int delay[] = { 250, 500, 750, 1000 };
                    final int repeat[] =
                        { 33,37,42,46,50,54,58,63,67,75,83,92,100,
                          109,118,125,133,149,167,182,200,217,233,
                          250,270,303,333,370,400,435,476,500 };
                    keyb.repeat.pause = delay[(val>>5)&3];
                    keyb.repeat.rate = repeat[val&0x1f];
                    keyb.command=KeyCommands.CMD_NONE;
                }
                /* Fallthrough! as setleds does what we want */
            case KeyCommands.CMD_SETLEDS:
                keyb.command=KeyCommands.CMD_NONE;
                KEYBOARD_ClrBuffer();
                KEYBOARD_AddBuffer(0xfa);	/* Acknowledge */
                break;
            }
        }
    };

    static /*Bit8u*/short port_61_data = 0;
    private static IoHandler.IO_ReadHandler read_p61 = new IoHandler.IO_ReadHandler() {
        public /*Bitu*/int call(/*Bitu*/int port, /*Bitu*/int iolen) {
            port_61_data^=0x20;
            port_61_data^=0x10;
            return port_61_data;
        }
    };

    private static IoHandler.IO_WriteHandler write_p61 = new IoHandler.IO_WriteHandler() {
        public void call(/*Bitu*/int port, /*Bitu*/int val, /*Bitu*/int iolen) {
            if (((port_61_data ^ val) & 3)!=0) {
                if(((port_61_data ^ val) & 1)!=0) Timer.TIMER_SetGate2((val&0x1)!=0);
                PCSpeaker.PCSPEAKER_SetType(val & 3);
            }
            port_61_data = (short)val;
        }
    };

    private static IoHandler.IO_WriteHandler write_p64 = new IoHandler.IO_WriteHandler() {
        public void call(/*Bitu*/int port, /*Bitu*/int val, /*Bitu*/int iolen) {
            switch (val) {
            case 0xae:		/* Activate keyboard */
                keyb.active=true;
                if (keyb.used!=0 && !keyb.scheduled && !keyb.p60changed) {
                    keyb.scheduled=true;
                    Pic.PIC_AddEvent(KEYBOARD_TransferBuffer,KEYDELAY);
                }
                Log.log(LogTypes.LOG_KEYBOARD, LogSeverities.LOG_NORMAL,"Activated");
                break;
            case 0xad:		/* Deactivate keyboard */
                keyb.active=false;
                Log.log(LogTypes.LOG_KEYBOARD, LogSeverities.LOG_NORMAL,"De-Activated");
                break;
            case 0xd0:		/* Outport on buffer */
                KEYBOARD_SetPort60((short)(Memory.MEM_A20_Enabled() ? 0x02 : 0));
                break;
            case 0xd1:		/* Write to outport */
                keyb.command=KeyCommands.CMD_SETOUTPORT;
                break;
            default:
                if (Log.level<=LogSeverities.LOG_ERROR) Log.log(LogTypes.LOG_KEYBOARD, LogSeverities.LOG_ERROR,"Port 64 write with val "+val);
                break;
            }
        }
    };

    private static IoHandler.IO_ReadHandler read_p64 = new IoHandler.IO_ReadHandler() {
        public /*Bitu*/int call(/*Bitu*/int port, /*Bitu*/int iolen) {
            /*Bit8u*/int status= 0x1c | (keyb.p60changed? 0x1 : 0x0);
            return status;
        }
    };

    static public void KEYBOARD_AddKey(int keytype,boolean pressed) {
        /*Bit8u*/short ret=0;boolean extend=false;
        switch (keytype) {
        case KBD_KEYS.KBD_esc:ret=1;break;
        case KBD_KEYS.KBD_1:ret=2;break;
        case KBD_KEYS.KBD_2:ret=3;break;
        case KBD_KEYS.KBD_3:ret=4;break;
        case KBD_KEYS.KBD_4:ret=5;break;
        case KBD_KEYS.KBD_5:ret=6;break;
        case KBD_KEYS.KBD_6:ret=7;break;
        case KBD_KEYS.KBD_7:ret=8;break;
        case KBD_KEYS.KBD_8:ret=9;break;
        case KBD_KEYS.KBD_9:ret=10;break;
        case KBD_KEYS.KBD_0:ret=11;break;

        case KBD_KEYS.KBD_minus:ret=12;break;
        case KBD_KEYS.KBD_equals:ret=13;break;
        case KBD_KEYS.KBD_backspace:ret=14;break;
        case KBD_KEYS.KBD_tab:ret=15;break;

        case KBD_KEYS.KBD_q:ret=16;break;
        case KBD_KEYS.KBD_w:ret=17;break;
        case KBD_KEYS.KBD_e:ret=18;break;
        case KBD_KEYS.KBD_r:ret=19;break;
        case KBD_KEYS.KBD_t:ret=20;break;
        case KBD_KEYS.KBD_y:ret=21;break;
        case KBD_KEYS.KBD_u:ret=22;break;
        case KBD_KEYS.KBD_i:ret=23;break;
        case KBD_KEYS.KBD_o:ret=24;break;
        case KBD_KEYS.KBD_p:ret=25;break;

        case KBD_KEYS.KBD_leftbracket:ret=26;break;
        case KBD_KEYS.KBD_rightbracket:ret=27;break;
        case KBD_KEYS.KBD_enter:ret=28;break;
        case KBD_KEYS.KBD_leftctrl:ret=29;break;

        case KBD_KEYS.KBD_a:ret=30;break;
        case KBD_KEYS.KBD_s:ret=31;break;
        case KBD_KEYS.KBD_d:ret=32;break;
        case KBD_KEYS.KBD_f:ret=33;break;
        case KBD_KEYS.KBD_g:ret=34;break;
        case KBD_KEYS.KBD_h:ret=35;break;
        case KBD_KEYS.KBD_j:ret=36;break;
        case KBD_KEYS.KBD_k:ret=37;break;
        case KBD_KEYS.KBD_l:ret=38;break;

        case KBD_KEYS.KBD_semicolon:ret=39;break;
        case KBD_KEYS.KBD_quote:ret=40;break;
        case KBD_KEYS.KBD_grave:ret=41;break;
        case KBD_KEYS.KBD_leftshift:ret=42;break;
        case KBD_KEYS.KBD_backslash:ret=43;break;
        case KBD_KEYS.KBD_z:ret=44;break;
        case KBD_KEYS.KBD_x:ret=45;break;
        case KBD_KEYS.KBD_c:ret=46;break;
        case KBD_KEYS.KBD_v:ret=47;break;
        case KBD_KEYS.KBD_b:ret=48;break;
        case KBD_KEYS.KBD_n:ret=49;break;
        case KBD_KEYS.KBD_m:ret=50;break;

        case KBD_KEYS.KBD_comma:ret=51;break;
        case KBD_KEYS.KBD_period:ret=52;break;
        case KBD_KEYS.KBD_slash:ret=53;break;
        case KBD_KEYS.KBD_rightshift:ret=54;break;
        case KBD_KEYS.KBD_kpmultiply:ret=55;break;
        case KBD_KEYS.KBD_leftalt:ret=56;break;
        case KBD_KEYS.KBD_space:ret=57;break;
        case KBD_KEYS.KBD_capslock:ret=58;break;

        case KBD_KEYS.KBD_f1:ret=59;break;
        case KBD_KEYS.KBD_f2:ret=60;break;
        case KBD_KEYS.KBD_f3:ret=61;break;
        case KBD_KEYS.KBD_f4:ret=62;break;
        case KBD_KEYS.KBD_f5:ret=63;break;
        case KBD_KEYS.KBD_f6:ret=64;break;
        case KBD_KEYS.KBD_f7:ret=65;break;
        case KBD_KEYS.KBD_f8:ret=66;break;
        case KBD_KEYS.KBD_f9:ret=67;break;
        case KBD_KEYS.KBD_f10:ret=68;break;

        case KBD_KEYS.KBD_numlock:ret=69;break;
        case KBD_KEYS.KBD_scrolllock:ret=70;break;

        case KBD_KEYS.KBD_kp7:ret=71;break;
        case KBD_KEYS.KBD_kp8:ret=72;break;
        case KBD_KEYS.KBD_kp9:ret=73;break;
        case KBD_KEYS.KBD_kpminus:ret=74;break;
        case KBD_KEYS.KBD_kp4:ret=75;break;
        case KBD_KEYS.KBD_kp5:ret=76;break;
        case KBD_KEYS.KBD_kp6:ret=77;break;
        case KBD_KEYS.KBD_kpplus:ret=78;break;
        case KBD_KEYS.KBD_kp1:ret=79;break;
        case KBD_KEYS.KBD_kp2:ret=80;break;
        case KBD_KEYS.KBD_kp3:ret=81;break;
        case KBD_KEYS.KBD_kp0:ret=82;break;
        case KBD_KEYS.KBD_kpperiod:ret=83;break;

        case KBD_KEYS.KBD_extra_lt_gt:ret=86;break;
        case KBD_KEYS.KBD_f11:ret=87;break;
        case KBD_KEYS.KBD_f12:ret=88;break;

        //The Extended keys

        case KBD_KEYS.KBD_kpenter:extend=true;ret=28;break;
        case KBD_KEYS.KBD_rightctrl:extend=true;ret=29;break;
        case KBD_KEYS.KBD_kpdivide:extend=true;ret=53;break;
        case KBD_KEYS.KBD_rightalt:extend=true;ret=56;break;
        case KBD_KEYS.KBD_home:extend=true;ret=71;break;
        case KBD_KEYS.KBD_up:extend=true;ret=72;break;
        case KBD_KEYS.KBD_pageup:extend=true;ret=73;break;
        case KBD_KEYS.KBD_left:extend=true;ret=75;break;
        case KBD_KEYS.KBD_right:extend=true;ret=77;break;
        case KBD_KEYS.KBD_end:extend=true;ret=79;break;
        case KBD_KEYS.KBD_down:extend=true;ret=80;break;
        case KBD_KEYS.KBD_pagedown:extend=true;ret=81;break;
        case KBD_KEYS.KBD_insert:extend=true;ret=82;break;
        case KBD_KEYS.KBD_delete:extend=true;ret=83;break;
        case KBD_KEYS.KBD_pause:
            KEYBOARD_AddBuffer(0xe1);
            KEYBOARD_AddBuffer(29|(pressed?0:0x80));
            KEYBOARD_AddBuffer(69|(pressed?0:0x80));
            return;
        case KBD_KEYS.KBD_printscreen:
            /* Not handled yet. But usuable in mapper for special events */
            return;
        default:
            Log.exit("Unsupported key press");
            break;
        }
        /* Add the actual key in the keyboard queue */
        if (pressed) {
            if (keyb.repeat.key==keytype) keyb.repeat.wait=keyb.repeat.rate;
            else keyb.repeat.wait=keyb.repeat.pause;
            keyb.repeat.key=keytype;
        } else {
            keyb.repeat.key=KBD_KEYS.KBD_NONE;
            keyb.repeat.wait=0;
            ret+=128;
        }
        if (extend) KEYBOARD_AddBuffer(0xe0);
        KEYBOARD_AddBuffer(ret);
    }

    static private Timer.TIMER_TickHandler KEYBOARD_TickHandler = new Timer.TIMER_TickHandler() {
        public void call() {
            if (keyb.repeat.wait!=0) {
                keyb.repeat.wait--;
                if (keyb.repeat.wait==0) KEYBOARD_AddKey(keyb.repeat.key,true);
            }
        }
    };

    public static Section.SectionFunction KEYBOARD_ShutDown = new Section.SectionFunction() {
        public void call(Section section) {
            keyb = null;
        }
    };

    public static Section.SectionFunction KEYBOARD_Init = new Section.SectionFunction() {
        public void call(Section section) {
            keyb = new Keyb();
            IoHandler.IO_RegisterWriteHandler(0x60,write_p60,IoHandler.IO_MB);
            IoHandler.IO_RegisterReadHandler(0x60,read_p60,IoHandler.IO_MB);
            IoHandler.IO_RegisterWriteHandler(0x61,write_p61,IoHandler.IO_MB);
            IoHandler.IO_RegisterReadHandler(0x61,read_p61,IoHandler.IO_MB);
            IoHandler.IO_RegisterWriteHandler(0x64,write_p64,IoHandler.IO_MB);
            IoHandler.IO_RegisterReadHandler(0x64,read_p64,IoHandler.IO_MB);
            Timer.TIMER_AddTickHandler(KEYBOARD_TickHandler);
            write_p61.call(0,0,0);
            /* Init the keyb struct */
            keyb.active=true;
            keyb.scanning=true;
            keyb.command=KeyCommands.CMD_NONE;
            keyb.p60changed=false;
            keyb.repeat.key=KBD_KEYS.KBD_NONE;
            keyb.repeat.pause=500;
            keyb.repeat.rate=33;
            keyb.repeat.wait=0;
            KEYBOARD_ClrBuffer();
            section.AddDestroyFunction(KEYBOARD_ShutDown,false);
        }
    };
}