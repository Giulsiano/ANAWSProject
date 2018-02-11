
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;

public class Functions {
	
	static final String USER = "cisco";
	static final String PASSWORD = "cisco";
	static final String[] VTYCOMMAND = {"/usr/bin/vtysh", "-d", "ospfd", "-c", "show ip ospf database"};
	private static Set<String> localAddress;
	
    static {
		localAddress = new HashSet<String>();
		// get address of all interfaces of this machine
		try {
			Enumeration<NetworkInterface> ifaces;
			ifaces = NetworkInterface.getNetworkInterfaces();
			while(ifaces.hasMoreElements()) {
				NetworkInterface currif = ifaces.nextElement();
				Enumeration<InetAddress> addresses = currif.getInetAddresses();
				while(addresses.hasMoreElements())
				{
					InetAddress addr = addresses.nextElement();
					if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
						System.out.printf("iface: %s,\t\taddress: %s\n", currif.getName(), addr.getHostAddress());
						localAddress.add(addr.getHostAddress());
					}
				}
			}
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			System.err.printf("Error caught initializing Functions:\n%s\n%s", e.getClass().getName(), e.getMessage());
			System.exit(1);
		}
	}
	
	public static void connectToRouter() throws IOException {
			System.out.println("Connect to router, This is a test\n");
			System.out.println("Getting router list");
			ArrayList<String> routers = (ArrayList<String>) getRouterList();
			if (routers == null) {
				System.err.println("Error retrieving list of router");
			}
	        final SSHClient ssh = new SSHClient();
	        ssh.loadKnownHosts();
	        ssh.connect("localhost");
	        try {
//	            ssh.authPassword(USER, PASSWORD);
//	            Runtime rt = Runtime.getRuntime();
//	            Process vtysh = rt.exec(command, null, null);
//	            System.out.println(IOUtils.readFully(vtysh.getInputStream()).toString());
//	            final Session session = ssh.startSession();
//	            try {	                
//	            	final Command cmd = session.exec("ping -c 1 8.8.8.8");
//	                System.out.println(IOUtils.readFully(cmd.getInputStream()).toString());
//	                cmd.join(5, TimeUnit.SECONDS);
//	                System.out.println("\n** exit status: " + cmd.getExitStatus());
//	            } finally {
//	                session.close();
//	            }
	        } finally {
	            ssh.disconnect();
	        }
	        System.exit(0);
	}
	
	public static void showTopology() {
		System.out.println("Show Topology, This is a test\n");
		
	}
	
	public static void configureDF() {
		System.out.println("Configure DiffServ, This is a test\n");
		
	}
	
	public static void defineNewClass() {
		System.out.println("Define New Class, This is a test\n");
		
	}
	
	public static void showRunningConf() {
		System.out.println("Show running configurations, This is a test\n");

	}
	
	/**
	 * Get the list of the router id from the ospfd daemon
	 * @return List of router id. They are the IP ssh will connect to or null if the command fails.
	 */
	private static List<String> getRouterList(){
		try {
			List<String> routers = null;
			Runtime rt = Runtime.getRuntime();
			while(routers == null) {
				Process vtysh = rt.exec(VTYCOMMAND);
				if (vtysh == null) {
					System.err.println("Error in command string");
					return null;
				}
				routers = getRouterIds(vtysh.getInputStream());
			}
			return routers;
		}
		catch (Exception e) {
			System.err.printf("Exception caught: %s\n%s\n", e.getClass().getName(), e.getMessage());
			return null;
		}
	}
	
	/** 
	 * Retrieve the list of the router ip from the input stream passed
	 * @param s InputStream from vtysh command
	 * @return the list of IPs that belong to the routers of the network
	 * @throws IOException
	 */
	private static List<String> getRouterIds(InputStream s) throws IOException{
		String splitStartToken = "Router Link States";
		String splitEndToken = "Net Link States";
		Pattern pattern = Pattern.compile("^(\\d){1,3}\\.(\\d){1,3}\\.(\\d){1,3}\\.(\\d){1,3}", Pattern.MULTILINE);
		String out = IOUtils.readFully(s).toString();
		if (out.isEmpty() || out == null) {
			return null;
		}
		String routerLinkTable = out.substring(out.indexOf(splitStartToken), out.indexOf(splitEndToken));
		Matcher matcher = pattern.matcher(routerLinkTable);
		List<String> routerIds = new ArrayList<String>();
		while (matcher.find()) {
			// Add address which doesn't belong to this machine
			if (!localAddress.contains(matcher.group())) {				
				routerIds.add(matcher.group());
			}
		}
		return routerIds;
	}
}

