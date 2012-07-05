package ibis.ipl.impl.stacking.cache;

import ibis.ipl.*;
import java.io.IOException;
import java.util.logging.Level;

public class SideChannelMessageHandler implements MessageUpcall, SideChannelProtocol {
    
    final CacheManager cacheManager;
    public static final Object ackLock = new Object();
    public static boolean ackReceived = false;

    SideChannelMessageHandler(CacheManager cache) {
        this.cacheManager = cache;
    }

    @Override
    public void upcall(ReadMessage msg) throws IOException, ClassNotFoundException {
        byte opcode = msg.readByte();
        SendPortIdentifier spi = (SendPortIdentifier) msg.readObject();
        ReceivePortIdentifier rpi = (ReceivePortIdentifier) msg.readObject();

        switch (opcode) {
            /*
             * The sender machine wants a free port at this machine.
             *
             * useless: with connection_upcalls, when a sendport connects, the
             * receiveport will get a gotConnection upcall.
             */
//            case RESERVE_RP:
//                cache.reserve(msg.origin().ibisIdentifier());
//                break;
            /*
             * At SendPortSide:
             * This upcall comes when the sending machine wants to cache a
             * connection from this sendport to its receiveport.
             */
            case CACHE_FROM_RP_AT_SP:
                synchronized (cacheManager) {
                    boolean heKnows = true;
                    CacheSendPort.map.get(spi).cache(rpi, heKnows);
                    cacheManager.removeConnection(spi, rpi);
                }
                break;

            /*
             * At ReceivePortSide:
             * This upcall comes when the sendport cached the connection. The
             * actual disconnection will take place at the lostConnection()
             * upcall. Here we merely want to mark that the disconnect call to
             * come is caching and not a true disconnect call.
             */
            case CACHE_FROM_SP:
                CacheReceivePort.map.get(rpi).toBeCachedSet.add(spi);

                /*
                 * Now send ack back.
                 */
                sendProtocol(spi, rpi, SideChannelProtocol.ACK);

                /*
                 * Done.
                 */
                break;

                /*
                 * At SendPortSide:
                 * Ack received from the above scenario.
                 */
            case ACK:
                synchronized (ackLock) {
                    ackReceived = true;
                    ackLock.notifyAll();
                }
                break;

                /*
                 * At ReceivePortSide:
                 * This protocol is sent through the side channel because
                 * the real connection has been cached.
                 * - no point in turning it up again only to have it closed, right?
                 */
            case DISCONNECT:
                CacheReceivePort rp = CacheReceivePort.map.get(rpi);
                rp.connectUpcall.lostConnection(null, spi, null);
                break;
        }
    }
    
    public void sendProtocol(SendPortIdentifier spi,
            ReceivePortIdentifier rpi, byte opcode) {
        /*
         * Synchronize on the sideChannelSendPort so as not to send
         * to side messages at the same time.
         */
        synchronized (cacheManager.sideChannelSendPort) {
            try {
                ReceivePortIdentifier sideRpi = cacheManager.sideChannelSendPort.connect(
                        spi.ibisIdentifier(), CacheManager.sideChnRPName);
                WriteMessage msg = cacheManager.sideChannelSendPort.newMessage();
                msg.writeByte(opcode);
                msg.writeObject(spi);
                msg.writeObject(rpi);
                msg.finish();
                cacheManager.sideChannelSendPort.disconnect(sideRpi);
            } catch (Exception ex) {
                CacheManager.log.log(Level.SEVERE, 
                        "Error at side channel:\n{0}",ex.getMessage());
            }
        }
    }
}
