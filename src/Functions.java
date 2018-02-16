
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.common.LoggerFactory;
import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.connection.channel.direct.Session.Shell;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

public class Functions{
	
	static final String USER = "cisco";
	static final String PASSWORD = "cisco";
	static final String[] VTYCOMMAND = {"/usr/bin/vtysh", "-d", "ospfd", "-c", "show ip ospf database"};
	private Set<String> localAddress;
	private String topology;
	private Runtime rt;
    public Functions() {
    	rt = Runtime.getRuntime();
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
	
	public void connectToRouter() throws IOException {
		String userInput = null;
		//System.out.println("Connect to router, This is a test\n");
		System.out.println("Getting router list");
		List<String> routers = getRouterList();
		if (routers == null) {
			System.err.println("Error retrieving list of router");
		}
		
		// Print router list and make the user to choose
		System.out.println("List of routers:");
		for(String ip: routers) {
			System.out.println(ip);
		}
		while(userInput == null) {
			System.out.println("Choose the router IP of the list to which you want to connect to:");
			userInput = System.console().readLine();
			if (!routers.contains(userInput)) {
				userInput = null;
			}
		}
		
		// Connect to the router
		final SSHClient ssh = new SSHClient();
        // we don't need to verify the host. This is a toy tool.
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(userInput);
        try {
            ssh.authPassword(USER, PASSWORD);
            final Session session = ssh.startSession();
            session.allocateDefaultPTY();
            final Shell shell = session.startShell();
            try {
            	// Redirect input and output stream in order to send user's command to the router
                new StreamCopier(shell.getInputStream(), System.out, LoggerFactory.DEFAULT)
                        .bufSize(shell.getLocalMaxPacketSize())
                        .spawn("stdout");

                new StreamCopier(shell.getErrorStream(), System.err, LoggerFactory.DEFAULT)
                        .bufSize(shell.getLocalMaxPacketSize())
                        .spawn("stderr");
                new StreamCopier(System.in, shell.getOutputStream(), LoggerFactory.DEFAULT)
        				.bufSize(shell.getRemoteMaxPacketSize())
        				.copy();
                shell.close();
            }
            catch (TransportException | ConnectionException e){
            	System.err.printf("Exception caught: %s\n%s\n", e.getClass().getName(), e.getMessage());
            } finally {
                session.close();
            }
        } finally {
            ssh.disconnect();
        }
	}
	
	/**
	 * Show the LSA Database retrieved from ospfd.
	 */
	public void showTopology() {
		System.out.println("Show Topology, This is a test\n");
		while(topology == null || topology.isEmpty()) {
			try {
				Process vtysh = this.rt.exec(VTYCOMMAND);
				if (vtysh == null) {
					System.err.println("Error in command string");
					System.exit(1);
				}
				else {
					topology = IOUtils.readFully(vtysh.getInputStream()).toString();
					System.out.println(topology);
				}
			} catch (IOException e) {
				System.err.printf("Exception caught: %s\n%s\n", e.getClass().getName(), e.getMessage());
				System.exit(1);
			}
		}	
	}
	
	public void configureDF() {
		System.out.println("Configure DiffServ, This is a test\n");
		
	}
	
	public void defineNewClass() {
		System.out.println("Define New Class, This is a test\n");
		
	}
	
	public void showRunningConf() {
		System.out.println("Show running configurations, This is a test\n");
		String command = "show running-config";
		Map<String, String> routerConfigs = new HashMap<String, String>();
		try {
			List<String> routers = getRouterIds(this.rt.exec(VTYCOMMAND).getInputStream());
			String config = null;
			for (String ip : routers) {
				SSHClient ssh = new SSHClient();
				ssh.addHostKeyVerifier(new PromiscuousVerifier());
				try {
					ssh.authPassword(USER, PASSWORD);
					ssh.connect(ip);
					Session session = ssh.startSession(); 
					try {
						Command cmd = session.exec(command);
						cmd.join();
					}
					finally {
						session.close();
					}
				}
				finally {
					ssh.disconnect();
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Get the list of the router id from the ospfd daemon
	 * @return List of router id. They are the IP ssh will connect to or null if the command fails.
	 */
	private List<String> getRouterList(){
		try {
			List<String> routers = null;
			Runtime rt = Runtime.getRuntime();
			while(routers == null) {
				Process vtysh = rt.exec(VTYCOMMAND);
				if (vtysh == null) {
					System.err.println("Error in command string");
					return null;
				}
				else {
					routers = getRouterIds(vtysh.getInputStream());					
				}
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
	private List<String> getRouterIds(InputStream s) throws IOException{
		String splitStartToken = "Router Link States";
		String splitEndToken = "Net Link States";
		Pattern pattern = Pattern.compile("^(\\d){1,3}\\.(\\d){1,3}\\.(\\d){1,3}\\.(\\d){1,3}", Pattern.MULTILINE);
		String out = IOUtils.readFully(s).toString();
		if (out.isEmpty() || out == null) {
			return null;
		}
		if (topology == null || !topology.equals(out)) {
			System.out.println("Updating topology");
			topology = out;
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

