package ibis.ipl.impl.stacking.cache;

import java.util.logging.*;

public class Loggers {

    public static final Logger cacheLog;
    public static final String cacheLogString;
    public static final Logger conLog;
//    public static final String conLogString;
    public static final Logger upcallLog;
//    public static final String upcallLogString;
    public static final Logger readMsgLog;
//    public static final String readMsgLogString;
    public static final Logger writeMsgLog;
//    public static final String writeMsgLogString;
    public static final Logger sideLog;
//    public static final String sideLogString;

    static {

        cacheLog = Logger.getLogger("cache");

        conLog = Logger.getLogger("con");

        upcallLog = Logger.getLogger("upcall");

        readMsgLog = Logger.getLogger("read");

        writeMsgLog = Logger.getLogger("write");

        sideLog = Logger.getLogger("side");

        cacheLogString = "cacheIbis.log";

        /*
         * Add for all file handler.
         */
        try {
            FileHandler fh = new FileHandler(cacheLogString);
            fh.setFormatter(new SimpleFormatter());
            cacheLog.addHandler(fh);
            conLog.addHandler(fh);
            upcallLog.addHandler(fh);
            readMsgLog.addHandler(fh);
            writeMsgLog.addHandler(fh);
            sideLog.addHandler(fh);
        } catch (Exception ex) {
            Logger.getLogger(CacheIbis.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
