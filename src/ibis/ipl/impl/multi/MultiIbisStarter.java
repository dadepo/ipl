/* $Id: StackingIbisStarter.java 6500 2007-10-02 18:28:50Z ceriel $ */

package ibis.ipl.impl.multi;

import ibis.ipl.CapabilitySet;
import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisStarter;
import ibis.ipl.PortType;
import ibis.ipl.RegistryEventHandler;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MultiIbisStarter extends IbisStarter {

    static final Logger logger
            = LoggerFactory.getLogger("ibis.ipl.impl.multi.MultiIbisStarter");

    static final IbisCapabilities ibisCapabilities = new IbisCapabilities(
        "nickname.multi"
    );

    private boolean matching;

    public MultiIbisStarter() {
    }

    public boolean matches(IbisCapabilities capabilities, PortType[] portTypes) {
        this.capabilities = capabilities;
        this.portTypes = portTypes.clone();
        // See if the nickname is specified. Otherwise this Ibis is not
        // selected.
        matching = ibisCapabilities.matchCapabilities(capabilities);
        return matching;
    }

    public boolean isSelectable() {
        return true;
    }

    public boolean isStacking() {
        return false;
    }

    public CapabilitySet unmatchedIbisCapabilities() {
        return capabilities.unmatchedCapabilities(ibisCapabilities);
    }

    public PortType[] unmatchedPortTypes() {
        return portTypes.clone();
    }

    public Ibis startIbis(RegistryEventHandler registryEventHandler,
            Properties userProperties, String version) {
            try {
                return new MultiIbis(registryEventHandler, userProperties, capabilities, portTypes);
            } catch (Throwable e) {
                throw new Error("Creation of MultiIbis Failed!", e);
            }
    }
}