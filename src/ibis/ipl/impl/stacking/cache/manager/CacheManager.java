package ibis.ipl.impl.stacking.cache.manager;

import ibis.ipl.impl.stacking.cache.util.CacheStatistics;
import ibis.ipl.*;
import ibis.ipl.impl.stacking.cache.CacheIbis;
import ibis.ipl.impl.stacking.cache.CacheSendPort;
import ibis.ipl.impl.stacking.cache.sidechannel.SideChannelMessageHandler;
import ibis.ipl.impl.stacking.cache.util.Loggers;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract class for different types of cache managers.
 */
public abstract class CacheManager {

    /*
     * TODO: move buffer capacity from here, it's ugly This is the maximum size of the buffer
     * used to stream data.
     */
    public static final int BUFFER_CAPACITY = 1 << 16;
    
    public static final int MAX_CONNS;
    public static final int MAX_CONNS_DEFAULT = 1000;
    
    public static final int MSG_MAX_ARRIVAL_TIME_MILLIS;
    public static final int MSG_MAX_ARRIVAL_TIME_MILLIS_DEFAULT = 30;
    
    /**
     * Port type used for the creation of hub-based ports for the side-channel
     * communication.
     */
    public static final PortType ultraLightPT = new PortType(
            PortType.CONNECTION_ULTRALIGHT,
            PortType.RECEIVE_AUTO_UPCALLS,
            PortType.SERIALIZATION_OBJECT,
            PortType.CONNECTION_MANY_TO_ONE);
    /**
     * These side channel sendport and receiveport names need to be unique. So
     * that the user won't create some other ports with these names.
     */
    public static final String sideChnSPName = "sideChannelSendport_uniqueStrainOfThePox";
    public static final String sideChnRPName = "sideChannelRecvport_uniqueStrainOfThePox";
    
    /**
     * The send and receive ports for the side channel should be static, i.e. 1
     * SP and 1 RP per CacheManager = Ibis instance. but in order to instantiate
     * them I need the ibis reference from the constructor.
     */
    public final ReceivePort sideChannelReceivePort;
    public final SendPort sideChannelSendPort;
    /**
     * This field handles the upcalls and provides a sendProtocol method.
     */
    public final SideChannelMessageHandler sideChannelHandler;
    
    public final Lock lock;
    public final Condition allClosedCondition;
    public final Condition reservationsCondition;
    public final Condition reserveAcksCond;
    public final Condition gotSpaceCondition;
    public final Condition sleepCondition;

    static {
        MAX_CONNS = Integer.parseInt(
                System.getProperty("ipl.cache.maxConns", Integer.toString(MAX_CONNS_DEFAULT)));
        
        MSG_MAX_ARRIVAL_TIME_MILLIS = Integer.parseInt(
                System.getProperty("ipl.cache.msgMaxArrivalTime", 
                Integer.toString(MSG_MAX_ARRIVAL_TIME_MILLIS_DEFAULT)));
    }

    CacheManager(CacheIbis ibis) {
        try {
            sideChannelHandler = new SideChannelMessageHandler(this);
            sideChannelReceivePort = ibis.baseIbis.createReceivePort(
                    ultraLightPT, sideChnRPName,
                    sideChannelHandler);
            sideChannelReceivePort.enableConnections();
            sideChannelReceivePort.enableMessageUpcalls();

            sideChannelSendPort = ibis.baseIbis.createSendPort(
                    ultraLightPT, sideChnSPName);
            
            lock = new ReentrantLock(true);
            allClosedCondition = lock.newCondition();
            reservationsCondition = lock.newCondition();
            reserveAcksCond = lock.newCondition();
            gotSpaceCondition = lock.newCondition();
            sleepCondition = lock.newCondition();
        
            Loggers.cacheLog.log(Level.INFO, "Cache manager instantiated on {0}."
                    + "\n\tCacheManager class: {2}"
                    + "\n\tmaxConns = {1}", new Object[] {
                        ibis.identifier().name(), MAX_CONNS, this.getClass()
                    });
        } catch (IOException ex) {
            Loggers.cacheLog.log(Level.SEVERE, "Failed to properly instantiate the Cache Manager.", ex);
            throw new RuntimeException(ex);
        }        
    }

    public void end() {
        try {
            
            CacheStatistics.printStatistics(Loggers.statsLog);
            
            sideChannelSendPort.close();
            sideChannelReceivePort.close();
            Loggers.cacheLog.log(Level.INFO, "Closed the cache manager.");
        } catch (IOException ex) {
            Loggers.cacheLog.log(Level.SEVERE, "Failed to close the cache manager.");
            Logger.getLogger(CacheManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    abstract public void closeSendPort(SendPortIdentifier spi);

    abstract public void cacheConnection(SendPortIdentifier spi,
            ReceivePortIdentifier rpi, boolean heKnows);

    abstract public void removeConnection(SendPortIdentifier spi,
            ReceivePortIdentifier rpi);
    
    abstract public void lostConnection(SendPortIdentifier identifier, 
            ReceivePortIdentifier rpi);

    abstract public void cacheConnection(ReceivePortIdentifier rpi,
            SendPortIdentifier spi);

    abstract public void removeConnection(ReceivePortIdentifier rpi,
            SendPortIdentifier spi);

    abstract public void activateReservedConnection(ReceivePortIdentifier rpi,
            SendPortIdentifier spi);

    abstract public void restoreReservedConnection(ReceivePortIdentifier rpi,
            SendPortIdentifier spi);

    abstract public List<ReceivePortIdentifier> cachedRpisFrom(
            SendPortIdentifier identifier);

    abstract public boolean hasConnections(ReceivePortIdentifier rpi);

    abstract public boolean isConnAlive(SendPortIdentifier spi,
            ReceivePortIdentifier rpi);
    
    abstract public boolean isConnAlive(ReceivePortIdentifier rpi, 
            SendPortIdentifier spi);
    
    abstract public boolean isConnCached(ReceivePortIdentifier rpi,
            SendPortIdentifier spi);
    
    abstract public boolean isConnCached(SendPortIdentifier spi,
            ReceivePortIdentifier rpi);
    
    abstract public boolean containsReservedAlive(SendPortIdentifier spi);

    abstract public ReceivePortIdentifier[] allRpisFrom(
            SendPortIdentifier identifier);

    abstract public SendPortIdentifier[] allSpisFrom(
            ReceivePortIdentifier identifier);

    /**
     * Connect the send port identifier to some receive ports from the list
     * passed as a param. Returns the list of receive ports identifiers with
     * whom a connection has been established.
     *
     * This method guarantees at least one successful connection.
     *
     * If the send port is already connected to some of the receive ports, those
     * connections are guaranteed not to be closed, and sent as part of the
     * result.
     *
     * If timeout is zero, it will block until at least one connection has been
     * established.
     *
     * If timeout > 0, and no connection has been established until the
     * deadline, it will throw a ConnectionTimedOutException.
     *
     * for each rp, the connection (sp, rp) can be: 1. already alive 2. cached
     * 3. not even initiated
     *
     * IMPORTANT: these connections are in an untouchable state. They will not
     * be cached until the method doneWith(rpis) is called to take them out of
     * that state.
     *
     * @param spi
     * @param rpis
     * @param timeoutMillis
     * @return
     */
    abstract public Set<ReceivePortIdentifier> getSomeConnections(
            CacheSendPort port, Set<ReceivePortIdentifier> rpis,
            long timeoutMillis, boolean fillTimeout) throws
            ConnectionsFailedException, ibis.ipl.IbisIOException;

    abstract public void reserveConnection(SendPortIdentifier spi,
            ReceivePortIdentifier rpi);
    
    abstract public void reserveConnection(ReceivePortIdentifier rpi,
            SendPortIdentifier spi);
    
    abstract public void reserveLiveConnection(SendPortIdentifier spi,
            ReceivePortIdentifier rpi);
    
    abstract public void reserveLiveConnection(ReceivePortIdentifier rpi,
            SendPortIdentifier spi);
    
    abstract public void unReserveLiveConnection(SendPortIdentifier spi,
            ReceivePortIdentifier rpi);
    
    abstract public void unReserveLiveConnection(ReceivePortIdentifier identifier, 
            SendPortIdentifier spi);
    
    abstract public void unReserveLiveToCacheConnection(ReceivePortIdentifier rpi,
            SendPortIdentifier spi);
    
    abstract public void cancelReservation(SendPortIdentifier spi, 
            ReceivePortIdentifier rpi);

    abstract public void cancelReservation(ReceivePortIdentifier rpi, 
            SendPortIdentifier spi);
    
    abstract public boolean fullConns();
    
    abstract public boolean canCache();

    
}