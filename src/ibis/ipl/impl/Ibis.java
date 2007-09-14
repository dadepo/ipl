/* $Id$ */

package ibis.ipl.impl;

import ibis.io.IbisIOException;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisConfigurationException;
import ibis.ipl.IbisProperties;
import ibis.ipl.MessageUpcall;
import ibis.ipl.PortType;
import ibis.ipl.ReceivePortConnectUpcall;
import ibis.ipl.RegistryEventHandler;
import ibis.ipl.SendPortDisconnectUpcall;
import ibis.util.TypedProperties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * This implementation of the {@link ibis.ipl.Ibis} interface 
 * is a base class, to be extended by specific Ibis implementations.
 */
public abstract class Ibis extends Managable implements ibis.ipl.Ibis {

    /** Debugging output. */
    private static final Logger logger = Logger.getLogger("ibis.ipl.impl.Ibis");
  
    /** The IbisCapabilities as specified by the user. */
    public final IbisCapabilities capabilities;

    /** List of port types given by the user */
    public final PortType[] portTypes;
    
    /**
     * Properties, as given to
     * {@link ibis.ipl.IbisFactory#createIbis(IbisCapabilities,
     * Properties, boolean, RegistryEventHandler, PortType...)}.
     */
    protected TypedProperties properties;

    /** The Ibis registry. */
    private final Registry registry;

    /** Identifies this Ibis instance in the registry. */
    public final IbisIdentifier ident;

    /** Set when {@link #end()} is called. */
    private boolean ended = false;

    /** The receiveports running on this Ibis instance. */
    private HashMap<String, ReceivePort> receivePorts;

    /** The sendports running on this Ibis instance. */
    private HashMap<String, SendPort> sendPorts;
    
    /** Counter for allocating names for anonymous sendports. */
    private static int send_counter = 0;

    /** Counter for allocating names for anonymous receiveports. */
    private static int receive_counter = 0;

    /**
     * Constructs an <code>Ibis</code> instance with the specified parameters.
     * @param registryHandler the registryHandler.
     * @param capabilities the capabilities.
     * @param portTypes the port types requested for this ibis implementation.
     * @param userProperties the properties as provided by the Ibis factory.
     * @param base the underlying Ibis or <code>null</code>. Used for stacking
     * ibisses.
     */
    protected Ibis(RegistryEventHandler registryHandler,
            IbisCapabilities capabilities, PortType[] portTypes,
            Properties userProperties, Ibis base) {

        this.capabilities = capabilities;
        this.portTypes = portTypes;

        addValidKey("statistics");
        
        this.properties = new TypedProperties();
        
        //bottom up add properties, starting with hard coded ones
        properties.addProperties(IbisProperties.getHardcodedProperties());
        properties.addProperties(userProperties);
        
        if (logger.isDebugEnabled()) {
            logger.debug("Ibis constructor: properties = " + properties);
        }
        
        receivePorts = new HashMap<String, ReceivePort>();
        sendPorts = new HashMap<String, SendPort>();
    
        registry = initializeRegistry(registryHandler, capabilities, base);
        ident = registry.getIbisIdentifier();
    }

    protected Registry initializeRegistry(RegistryEventHandler handler, 
            IbisCapabilities caps, Ibis base) {
        
        try {
            return Registry.createRegistry(caps, handler, properties, 
                    getData());
        } catch (IbisConfigurationException e) {
            throw e;
        } catch(Throwable e) {
            throw new IbisConfigurationException("Could not create registry",
                    e);
        }
    }

    public Registry registry() {
        return registry;
    }

    public ibis.ipl.IbisIdentifier identifier() {
        return ident;
    }

    public Properties properties() {
        return new Properties(properties);
    }

    public void printStatistics() {
        // default is empty.
    }

    /**
     * Returns the current Ibis version.
     * @return the ibis version.
     */
    public String getVersion() {
        InputStream in
            = ClassLoader.getSystemClassLoader().getResourceAsStream("VERSION");
        String version = "Unknown Ibis Version ID";
        if (in != null) {
            BufferedReader bIn = new BufferedReader(new InputStreamReader(in));
            try {
                version = "Ibis Version ID " + bIn.readLine();
                bIn.close();
            } catch (Exception e) {
                // Ignored
            }
        }
        return version + ", implementation = " + this.getClass().getName();
    }

    public void end() throws IOException {
        synchronized (this) {
            if (ended) {
                return;
            }
            ended = true;
        }
        try {
            registry.leave();
        } catch (Throwable e) {
            throw new IbisIOException("Registry: leave failed ", e);
        }
        quit();
        try {
            if (getDynamicProperty("statistics") != null) {
                printStatistics();
            }
        } catch(Throwable e) {
            // ignored
        }
    }

    public void poll() {
        // Default has empty implementation.
    }

    synchronized void register(ReceivePort p) throws IOException {
        if (receivePorts.get(p.name) != null) {
            throw new IOException("Multiple instances of receiveport named "
                    + p.name);
        }
        receivePorts.put(p.name, p);
    }

    synchronized void deRegister(ReceivePort p) {
        if (receivePorts.remove(p.name) == null) {
            // ignore!
            // throw new Error("Trying to remove unknown receiveport");
        }
    }

    synchronized void register(SendPort p) throws IOException {
        if (sendPorts.get(p.name) != null) {
            throw new IOException("Multiple instances of sendport named " + p.name);
        }
        sendPorts.put(p.name, p);
    }

    synchronized void deRegister(SendPort p) {
        if (sendPorts.remove(p.name) == null) {
            // ignore!
            // throw new Error("Trying to remove unknown sendport");
        }
    }

    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // Public methods, may called by Ibis implementations.
    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

    /**
     * Returns the receiveport with the specified name, or <code>null</code>
     * if not present.
     * @param name the name of the receiveport.
     * @return the receiveport.
     */
    public synchronized ReceivePort findReceivePort(String name) {
        return receivePorts.get(name);
    }

    /**
     * Returns the sendport with the specified name, or <code>null</code>
     * if not present.
     * @param name the name of the sendport.
     * @return the sendport.
     */
    public synchronized SendPort findSendPort(String name) {
        return sendPorts.get(name);
    }

    public ReceivePortIdentifier createReceivePortIdentifier(String name,
            IbisIdentifier id) {
        return new ReceivePortIdentifier(name, id);
    }

    public SendPortIdentifier createSendPortIdentifier(String name,
            IbisIdentifier id) {
        return new SendPortIdentifier(name, id);
    }

    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // Protected methods, to be implemented by Ibis implementations.
    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

    /**
     * Implementation-dependent part of the {@link #end()} implementation.
     */
    protected abstract void quit();

    /**
     * This method should provide the implementation-dependent data of
     * the Ibis identifier for this Ibis instance. This method gets called
     * from the Ibis constructor.
     * @exception IOException may be thrown in case of trouble.
     * @return the implementation-dependent data, as a byte array.
     */
    protected abstract byte[] getData() throws IOException;

    public ibis.ipl.SendPort createSendPort(PortType tp)
            throws IOException {
        return createSendPort(tp, null, null, null);
    }

    public ibis.ipl.SendPort createSendPort(PortType tp, String name)
            throws IOException {
        return createSendPort(tp, name, null, null);
    }
 
    private void matchPortType(PortType tp) {
        boolean matched = false;
        for (PortType p : portTypes) {
            if (tp.equals(p)) {
                matched = true;
            }
        }
        if (! matched) {
            throw new IbisConfigurationException("PortType " + tp
                    + " not specified when creating this Ibis instance");
        }
    }
    
    public ibis.ipl.SendPort createSendPort(PortType tp, String name,
            SendPortDisconnectUpcall cU, Properties properties) throws IOException {
        if (cU != null) {
            if (! tp.hasCapability(PortType.CONNECTION_UPCALLS)) {
                throw new IbisConfigurationException(
                        "no connection upcalls requested for this port type");
            }
        }
        if (name == null) {
            synchronized(this.getClass()) {
                name = "anonymous send port " + send_counter++;
            }
        }

        matchPortType(tp);

        return doCreateSendPort(tp, name, cU, properties);
    }

    /**
     * Creates a {@link ibis.ipl.SendPort} of the specified port type.
     *
     * @param tp the port type.
     * @param name the name of this sendport.
     * @param cU object implementing the
     * {@link SendPortDisconnectUpcall#lostConnection(ibis.ipl.SendPort,
     * ReceivePortIdentifier, Throwable)} method.
     * @param properties the port properties.
     * @return the new sendport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     */
    protected abstract ibis.ipl.SendPort doCreateSendPort(PortType tp,
            String name, SendPortDisconnectUpcall cU, Properties properties) throws IOException;

    public ibis.ipl.ReceivePort createReceivePort(PortType tp, String name)
            throws IOException {
        return createReceivePort(tp, name, null, null, null);
    }

    public ibis.ipl.ReceivePort createReceivePort(PortType tp, String name,
            MessageUpcall u) throws IOException {
        return createReceivePort(tp, name, u, null, null);
    }

    public ibis.ipl.ReceivePort createReceivePort(PortType tp, String name,
            ReceivePortConnectUpcall cU) throws IOException {
        return createReceivePort(tp, name, null, cU, null);
    }

    public ibis.ipl.ReceivePort createReceivePort(PortType tp, String name,
            MessageUpcall u, ReceivePortConnectUpcall cU, Properties properties)
            throws IOException {
        if (cU != null) {
            if (!tp.hasCapability(PortType.CONNECTION_UPCALLS)) {
                throw new IbisConfigurationException(
                        "no connection upcalls requested for this port type");
            }
        }
        if (u != null) {
            if (! tp.hasCapability(PortType.RECEIVE_AUTO_UPCALLS)
                   && !tp.hasCapability(PortType.RECEIVE_POLL_UPCALLS)) {
                throw new IbisConfigurationException(
                        "no message upcalls requested for this port type");
            }
        } else {
            if (!tp.hasCapability(PortType.RECEIVE_EXPLICIT)) {
                throw new IbisConfigurationException(
                        "no explicit receive requested for this port type");
            }
        }
        if (name == null) {
            synchronized(this.getClass()) {
                name = "anonymous receive port " + receive_counter++;
            }
        }
        matchPortType(tp);
        return doCreateReceivePort(tp, name, u, cU, properties);
    }

    /** 
     * Creates a named {@link ibis.ipl.ReceivePort} of the specified port type,
     * with upcall based communication.
     * New connections will not be accepted until
     * {@link ibis.ipl.ReceivePort#enableConnections()} is invoked.
     * This is done to avoid upcalls during initialization.
     * When a new connection request arrives, or when a connection is lost,
     * a ConnectUpcall is performed.
     *
     * @param tp the port type.
     * @param name the name of this receiveport.
     * @param u the upcall handler.
     * @param cU object implementing <code>gotConnection</code>() and
     * <code>lostConnection</code>() upcalls.
     * @param properties the port properties.
     * @return the new receiveport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     */
    protected abstract ibis.ipl.ReceivePort doCreateReceivePort(
            PortType tp, String name, MessageUpcall u,
            ReceivePortConnectUpcall cU, Properties properties) throws IOException;
}
