
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
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
import net.schmizz.sshj.common.SSHException;
import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Shell;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.sf.expectit.Expect;
import net.sf.expectit.ExpectBuilder;

public class Functions{
	
	public String userInput = null;
	public List<String> routers;
	public File[] fileList;
	static final String USER = "cisco";
	static final String PASSWORD = "cisco";
	static final String[] VTYCOMMAND = {"/usr/bin/vtysh", "-d", "ospfd", "-c", "show ip ospf database"};
	static final String CLASSDIR = "../classes/";
	private Set<String> localAddress;
	private String topology;
	private Runtime rt;
	private Map<String, Integer> dscpValues;
	/**
	 * Enter the root terminal of the Cisco device
	 * @param s The sshj session object of a previously connected sshj connection
	 * @return the Expect object where the command enable was sent 
	 * @throws SSHException
	 * @throws IOException
	 */
	private Expect enableRoot(Session s) throws SSHException, IOException {
		s.allocateDefaultPTY();
		Shell sh = s.startShell();
		Expect expect = new ExpectBuilder()
                .withOutput(sh.getOutputStream())
                .withInputs(sh.getInputStream(), sh.getErrorStream())
                .build();
		expect.send("enable");
		expect.sendLine();
		expect.send("cisco");
		expect.sendLine();
		return expect;
	}
	
    public Functions() {
    	rt = Runtime.getRuntime();
		localAddress = new HashSet<String>();
		dscpValues = new HashMap<>();
		dscpValues.put("Best effort", new Integer(0));
		dscpValues.put("AF11", new Integer(10));
		dscpValues.put("AF12", new Integer(12));
		dscpValues.put("AF13", new Integer(14));
		dscpValues.put("AF21", new Integer(18));
		dscpValues.put("AF22", new Integer(20));
		dscpValues.put("AF23", new Integer(22));
		dscpValues.put("AF31", new Integer(26));
		dscpValues.put("AF32", new Integer(28));
		dscpValues.put("AF33", new Integer(30));
		dscpValues.put("AF41", new Integer(34));
		dscpValues.put("AF42", new Integer(36));
		dscpValues.put("AF43", new Integer(38));
		dscpValues.put("CS1", new Integer(8));
		dscpValues.put("CS2", new Integer(16));
		dscpValues.put("CS3", new Integer(24));
		dscpValues.put("CS4", new Integer(32));
		dscpValues.put("CS5", new Integer(40));
		dscpValues.put("CS6", new Integer(48));
		dscpValues.put("CS7", new Integer(56));
		dscpValues.put("EF", new Integer(46));
		
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

//*******************************************************************************************************************************************    
    
	public void connectToRouter() throws IOException {
				
		printRouterList();
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
            redirect(shell, session, false);
        } finally {
            ssh.disconnect();
        }
	}
	
//******************************************************************************************************************************************* 
	
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
	
//******************************************************************************************************************************************* 
	
	public void configureDF() throws IOException {
		String input1 = null;
		String input2 = null;
		String addr = null;
		String filepos = null;
		int pos = 0;
		System.out.println("Starting DiffServ Configuratin wizard...\n");
		printAllClasses();
		while(input1 == null) {
			System.out.print("Do you want to use standard (std) or new classes(new)? ");
			input1 = System.console().readLine();
			if(!input1.equals("std") && !input1.equals("new"))
				input1 = null;
		}
		while(input2 == null) {
			System.out.println("Type:\n"
					+ "<all> to configure DiffServ on every routers in the net\n"
					+ "<one> to configure DiffServ on a single router\n");
			input2 = System.console().readLine();
			if(!input2.equals("one") && !input2.equals("all"))
				input2 = null;
		}
		
       //Checking input1 and input2 content
		
		if(input1.equals("std") && input2.equals("one")) {
			printRouterList();
			while(addr == null) {
				System.out.print("Choose the router: ");
				addr = System.console().readLine();
				if(!routers.contains(addr)) 
					addr = null;
			}
			confStdDF(addr);
		}
		else if (input1.equals("std") && input2.equals("all")) {
			System.out.println("\n" + "Getting router list");
			routers = getRouterList();
			if (routers == null) {
				System.err.println("Error retrieving list of router");
				return;
			}
			
			for(String ip: routers) {
				confStdDF(ip);
			}
			
		}
		else if (input1.equals("new") && input2.equals("one")) {
			printRouterList();
			while(addr == null) {
				System.out.print("Choose the router: ");
				addr = System.console().readLine();
				if(!routers.contains(addr)) 
					addr = null;
			}
		
			printNewClasses();
			while(filepos == null) {
				System.out.print("Waiting for a choise: ");
				filepos = System.console().readLine();
				pos = Integer.parseInt(filepos);
				if(pos > fileList.length-1)
					filepos = null;
			}
			if(filepos.equals("new")) 
				defineNewClass(true, addr);
			else 
				applyNewClass(pos, addr);
			
		}
		else if (input1.equals("new") && input2.equals("all")) {
			printNewClasses();
			while(filepos == null) {
				System.out.print("Waiting for a choise: ");
				filepos = System.console().readLine();
				pos = Integer.parseInt(filepos);
				if(pos > fileList.length-1)
					filepos = null;
			}
			if(filepos.equals("new")) 
				defineNewClass(true, null);
			else {
				System.out.println("\n" + "Getting router list");
				routers = getRouterList();
				if (routers == null) {
					System.err.println("Error retrieving list of router");
					return;
				}

				for(String ip: routers) 
					applyNewClass(pos, ip);
			}
			
			
		}
	}
	
//******************************************************************************************************************************************* 	
			

	public String defineNewClass(boolean calledByFunction, String ip) {
		System.out.println("entering defineNewClass\n");
		if (calledByFunction == true && (ip == null || ip.isEmpty())){
			System.out.println("IP of the router is not known. Exiting.");
			System.exit(1);
		}
		System.out.printf("Enter name of the new class: ");
		String filename = System.console().readLine();
		try {
			PrintWriter classFile = new PrintWriter(new File(CLASSDIR + filename));
			classFile.println("class " + filename);
			System.out.println("Do you prefer to be guided through the class definition?\n" 
								 + "yes - the wizard will allow you to define basic features such as shaping,\n"
								 + "      policing and enabling RED\n"
								 + "no - you can write your own class line by line\n");
			String userResp = null;
			do{
				System.out.println("Response (yes/no): ");
				userResp = System.console().readLine();
			}while(!(userResp.equals("yes") || userResp.equals("no")));
			try {
				// User wants them to be guided into the configuration
				if (userResp.equals("yes")) {
					do {
						System.out.print("\n\nWrite bandwidth limit (0 = no limit, max 100): ");
						userResp = System.console().readLine();						
					} while (!isInsideBound(userResp, 0, 100));
					if (!userResp.equals("0")) {
						classFile.println("bandwidth percent " + userResp);
					}
					do {
						System.out.print("\n\nWrite traffic shaping on average speed of (bps, 0 = no shaping): ");
						userResp = System.console().readLine();						
					} while (!isInsideBound(userResp, 0, Integer.MAX_VALUE));
					if (!userResp.equals("0")) {
						classFile.println("shape average  " + userResp);
					}
					do {
						System.out.print("\n\nApply random early detection to class? (yes/no) ");
						userResp = System.console().readLine();						
					} while (!(userResp.equals("yes") || userResp.equals("no")));
					if(userResp.equals("yes")) {
						classFile.println("random-detect");
						classFile.println("no random-detect precedence-based");
					}
					do {
						System.out.print("\n\nSet a DSCP value for this class (literal or numerical, 0 is default): ");
						userResp = System.console().readLine();						
					} while (!this.dscpValues.containsKey(userResp.toUpperCase()));
					if(!(userResp.equals("0") || this.dscpValues.get(userResp.toUpperCase()) == 0)) {
						classFile.println("set ip dscp " + userResp.toUpperCase());
					}
				}
				else {
					System.out.println("Commands written here must be valid cisco IOS commands.\n"
							+ "Example to define a new classification mapping:\n\n"
							+ "! define new classification, all hosts matching 192.168.1.* are classified\n"
							+ "access-list 1 permit 192.168.1.0 0.0.0.255\n"
							+ "class map match-all VOIP\n"
							+ " match access-group 1\n\n"
							+ "Write line by line your class definition below. End with .exit\n\n"
							+ "class " + filename
							);
					String input = null;
					while (true){
						input = System.console().readLine();
						if (input.equals(".exit") == true) break;
						classFile.println(input);
					}
				}				
			}
			finally {
				classFile.close();
			}
		} catch (FileNotFoundException e) {
			System.err.println("Cannot create class file on the filesystem");
			System.exit(1);
		}
		return filename;
	}

	private boolean isInsideBound(String s, int lowerBound, int upperBound) {
		if (upperBound < lowerBound) {
			throw new IllegalArgumentException("Lower bound can't be less than upper bound");
		}
		int num;
		try {
			num = Integer.parseInt(s);
			if (num > upperBound || num < lowerBound) return false;
			else return true;
		}
		catch (NumberFormatException e) {
			return false;
		}
	}
	
	private boolean isInteger(String s) {
		try {
			Integer.parseInt(s);
		}
		catch (NumberFormatException e) {
			return false;
		}
		return true;
	}
//******************************************************************************************************************************************* 	
	
	public void showRunningConf() {

		printRouterList();
		while(userInput == null) {
			System.out.println("Choose the router IP of the list to which you want the running-conf:");
			userInput = System.console().readLine();
			if (!routers.contains(userInput)) {
				userInput = null;
			}
		}
		try {
			SSHClient ssh = new SSHClient();
			ssh.addHostKeyVerifier(new PromiscuousVerifier());
			try {
				ssh.connect(userInput);
				ssh.authPassword(USER, PASSWORD);
				Session session = ssh.startSession(); 
				Shell shell = session.startShell();
				redirect(shell, session, true);
				Expect expect = enableRoot(session);
			    try {
					//System.out.println("---> Executing the command...");	
					expect.send("enable");
					expect.sendLine();
					expect.send("cisco");
					expect.sendLine();
					expect.send("terminal length 0");
					expect.sendLine();
				    expect.send("show running-config");
				    expect.sendLine();
			    } finally {
				    expect.close();
					session.close();
				} 
			}
			finally {
				ssh.disconnect();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
//******************************************************************************************************************************************* 
	
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
	
//******************************************************************************************************************************************* 
	
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
	
//******************************************************************************************************************************************* 	
	
	private void printRouterList() {
		System.out.println("Getting router list");
		routers = getRouterList();
		if (routers == null) {
			System.err.println("Error retrieving list of router");
			return;
		}
		
		// Print router list and make the user to choose
		System.out.println("List of routers:");
		for(String ip: routers) {
			System.out.println(ip);
		}
		System.out.println("");
	}
	
//******************************************************************************************************************************************* 	
	
	/**
	 *  Redirect output stream in order to send user's command to the router
	 * @param sh
	 * @param ses
	 * @throws IOException
	 */
	private void redirect(Shell sh, Session ses, boolean onlyOutput) throws IOException {
		if(!onlyOutput) {
			try {
				new StreamCopier(sh.getInputStream(), System.out, LoggerFactory.DEFAULT)
	            .bufSize(sh.getLocalMaxPacketSize())
	            .spawn("stdout");
				new StreamCopier(sh.getErrorStream(), System.err, LoggerFactory.DEFAULT)
	            	.bufSize(sh.getLocalMaxPacketSize())
	            	.spawn("stderr");
				new StreamCopier(System.in, sh.getOutputStream(), LoggerFactory.DEFAULT)
					.bufSize(sh.getRemoteMaxPacketSize())
					.copy();
				System.out.println("\n");
	        }
	        catch (TransportException | ConnectionException e){
	        	System.err.printf("Exception caught: %s\n%s\n", e.getClass().getName(), e.getMessage());
	        }finally {
	            ses.close();
	        }
		}
		else {
			new StreamCopier(sh.getInputStream(), System.out, LoggerFactory.DEFAULT)
            .bufSize(sh.getLocalMaxPacketSize())
            .spawn("stdout");
		}
		
	}
	
//*******************************************************************************************************************************************
	
	public void printNewClasses() {
		System.out.println("\n" +"List of available classes:\n");
		File curDir = new File(CLASSDIR);
		fileList = curDir.listFiles();
		for(int i = 0; i<fileList.length; i++) {
			System.out.println(i + ": " + fileList[i].getName());
		}
		System.out.println("new: create new one\n");
	}
	
	public void printAllClasses() {
		System.out.println("STANDARD CLASSES:\n"
				+ "-VoIP\n"
				+ "-Video\n"
				+ "-Web");
		System.out.println("ADMIN DEFINED CLASSES:");
		File curDir = new File(CLASSDIR);
		fileList = curDir.listFiles();
		for(int i = 0; i<fileList.length; i++) {
			System.out.println("-"+fileList[i].getName());
		}
	}
	
//*******************************************************************************************************************************************	
	
	public void applyNewClass(int position, String ip) {
		System.out.println("Applying new class TEST");
	}
	
//*******************************************************************************************************************************************
	
	private void confStdDF(String ip) {
		
	}

//*******************************************************************************************************************************************

	
	private void ApplyNewClass(String ip, String filename) {
		
	}

	public void verifyNewClass(String fileName) {
		// TODO Auto-generated method stub
		
	}
}





