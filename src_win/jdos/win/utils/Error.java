package jdos.win.utils;

public class Error {
    public static final int ERROR_SUCCESS = 0;
    public static final int ERROR_FILE_NOT_FOUND = 2;
    public static final int ERROR_PATH_NOT_FOUND = 3;
    public static final int ERROR_ACCESS_DENIED = 5;
    public static final int ERROR_INVALID_HANDLE = 6;
    public static final int ERROR_FILE_EXISTS = 80;
    public static final int ERROR_INVALID_PARAMETER = 87;
    public static final int ERROR_INSUFFICIENT_BUFFER = 122;
    public static final int ERROR_MOD_NOT_FOUND = 126;
    public static final int ERROR_ALREADY_EXISTS = 183;
    public static final int ERROR_MR_MID_NOT_FOUND = 317;
    public static final int ERROR_INVALID_FLAGS = 1004;
    public static final int ERROR_INVALID_WINDOW_HANDLE = 1400;
    public static final int ERROR_CANNOT_FIND_WND_CLASS = 1407;
    public static final int ERROR_CLASS_ALREADY_EXISTS = 1410;

    public static String getError(int e) {
        switch (e) {
            case ERROR_SUCCESS:
                return "The operation completed successfully.";
            case ERROR_FILE_NOT_FOUND:
                return "The system cannot find the file specified.";
            case ERROR_PATH_NOT_FOUND:
                return "The system cannot find the path specified.";
            case ERROR_ACCESS_DENIED:
                return "Access is denied.";
            case ERROR_INVALID_HANDLE:
                return "The handle is invalid.";
            case ERROR_FILE_EXISTS:
                return "The file exists.";
            case ERROR_INVALID_PARAMETER:
                return "The parameter is incorrect.";
            case ERROR_INSUFFICIENT_BUFFER:
                return "The data area passed to a system call is too small.";
            case ERROR_MOD_NOT_FOUND:
                return "The specified module could not be found.";
            case ERROR_ALREADY_EXISTS:
                return "Cannot create a file when that file already exists.";
            case ERROR_MR_MID_NOT_FOUND:
                return "The system cannot find message text for message number 0x%1 in the message file for %2.";
            case ERROR_INVALID_FLAGS:
                return "Invalid flags.";
        }
        return null;
    }
}
