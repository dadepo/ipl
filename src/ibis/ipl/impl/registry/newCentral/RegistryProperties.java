package ibis.ipl.impl.registry.newCentral;

import ibis.util.TypedProperties;

import java.util.LinkedHashMap;
import java.util.Map;

public class RegistryProperties {

	public static final String PREFIX = "ibis.registry.newCentral.";

	public static final String CHECKUP_INTERVAL = PREFIX + "checkup.interval";

	public static final String GOSSIP = PREFIX + "gossip";

	public static final String GOSSIP_INTERVAL = PREFIX + "gossip.interval";

	public static final String ADAPT_GOSSIP_INTERVAL = PREFIX
			+ "adapt.gossip.interval";

	public static final String TREE = PREFIX + "tree";

	public static final String CONNECT_TIMEOUT = PREFIX + "connect.timeout";

	public static final String SERVER_PREFIX = PREFIX + "server.";

	public static final String SERVER_STANDALONE = SERVER_PREFIX + "standalone";

	public static final String SERVER_ADDRESS = SERVER_PREFIX + "address";

	public static final String SERVER_PORT = SERVER_PREFIX + "port";

	public static final String SERVER_PRINT_EVENTS = SERVER_PREFIX
			+ "print.events";

	public static final String SERVER_PRINT_STATS = SERVER_PREFIX
			+ "print.stats";

	// list of decriptions and defaults
	private static final String[][] propertiesList = new String[][] {

			{ CHECKUP_INTERVAL, "60",
					"Int(seconds): how often do we check if a member "
							+ "of a pool is still alive, and"
							+ " send it any events it missed" },

			{ GOSSIP, "false",
					"Boolean: do we gossip, or send events centrally" },

			{ GOSSIP_INTERVAL, "1", "Int(seconds): how often do we gossip" },

			{ ADAPT_GOSSIP_INTERVAL, "false",
					"Boolean: if true, the server gossips more often if there are"
							+ "more nodes in a pool" },

			{ TREE, "false", "Boolean: use a broadcast tree instead of "
							+ "serial send or gossiping" },

			{ SERVER_STANDALONE, "false",
					"Boolean: if true, a stand-alone server is used/expected" },

			{ SERVER_ADDRESS, null,
					"Socket Address of standalone server to connect to" },

			{ SERVER_PORT, "7777",
					"Int: Port which the standalone server binds to" },

			{ CONNECT_TIMEOUT, "120",
					"Int(seconds): how long do we attempt to connect before giving up" },

			{ SERVER_PRINT_EVENTS, "false",
					"Boolean: if true, events are printed to standard out" },
			{ SERVER_PRINT_STATS, "false",
					"Boolean: if true, statistics are printed to standard out regularly" },

	};

	public static TypedProperties getHardcodedProperties() {
		TypedProperties properties = new TypedProperties();

		for (String[] element : propertiesList) {
			if (element[1] != null) {
				properties.setProperty(element[0], element[1]);
			}
		}

		return properties;
	}

	public static Map<String, String> getDescriptions() {
		Map<String, String> result = new LinkedHashMap<String, String>();

		for (String[] element : propertiesList) {
			result.put(element[0], element[2]);
		}

		return result;
	}

}