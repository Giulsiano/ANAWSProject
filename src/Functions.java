
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.util.Pair;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.common.LoggerFactory;
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
	public List<String[]> routerDescription;
	public File[] fileList;
	static final String USER = "cisco";
	static final String PASSWORD = "cisco";
	static final String[] VTYCOMMAND = {"/usr/bin/vtysh", "-d", "ospfd", "-c", "show ip ospf database"};
	static final String CLASSDIR = "../classes/";
	private Set<String> localAddress;
	private String topology;
	private Runtime rt;
	private Map<String, Integer> dscpValues;
	
    public Functions() {
    	rt = Runtime.getRuntime();
		localAddress = new HashSet<String>();
		dscpValues = new HashMap<>();
		dscpValues.put("Best effort", 0);
		dscpValues.put("AF11", 10);
		dscpValues.put("AF12", 12);
		dscpValues.put("AF13", 14);
		dscpValues.put("AF21", 18);
		dscpValues.put("AF22", 20);
		dscpValues.put("AF23", 22);
		dscpValues.put("AF31", 26);
		dscpValues.put("AF32", 28);
		dscpValues.put("AF33", 30);
		dscpValues.put("AF41", 34);
		dscpValues.put("AF42", 36);
		dscpValues.put("AF43", 38);
		dscpValues.put("CS1", 8);
		dscpValues.put("CS2", 16);
		dscpValues.put("CS3", 24);
		dscpValues.put("CS4", 32);
		dscpValues.put("CS5", 40);
		dscpValues.put("CS6", 48);
		dscpValues.put("CS7", 56);
		dscpValues.put("EF", 46);
		
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
			System.err.printf("Error caught initializing Functions:\n%s\n%s", e.getClass().getName(), e.getMessage());
			System.exit(1);
		}
	}

//*******************************************************************************************************************************************    
    
    public void connectToRouter() throws IOException {
		String userInput = null;
		printRouterList();
		boolean isValidInput = false;
		do {
			System.out.println("Choose the router IP of the list to which you want to connect to:");
			System.out.println("Enter .exit to return back");
			userInput = System.console().readLine();
			if(userInput.equals(".exit"))
				return;
			isValidInput = getRouterAddresses().contains(userInput);
		} while (isValidInput == false);
		OSPFRouter router = new OSPFRouter(userInput);
		router.connect(USER, PASSWORD);
		Expect exp = router.getExpectIt();
		try {
			do {
				userInput = System.console().readLine();
				exp.sendLine(userInput);
			}while (!"quit".equals(userInput) || !"exit".equals(userInput));
		}
		catch (Exception e) {
			
		}
		router.disconnect();	
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
		System.out.println("Starting DiffServ Configuration wizard...\n");
		printAllClasses();
		while(input1 == null) {
			System.out.println("Do you want to use standard (std) or new classes(new)? ");
			System.out.println("Enter .exit to return back");
			input1 = System.console().readLine();
			if(input1.equals(".exit"))
				return;
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
		List<String> routerAddrs = getRouterAddresses();
		if(input1.equals("std") && input2.equals("one")) {
			printRouterList();
			while(addr == null) {
				System.out.print("Choose the router: ");
				addr = System.console().readLine();
				if(!routerAddrs.contains(addr)) 
					addr = null;
			}
			confStdDF(addr);
		}
		else if (input1.equals("std") && input2.equals("all")) {
			System.out.println("\nGetting router list");
			routerDescription = getRouterDesc();
			if (routerDescription == null) {
				System.err.println("Error retrieving list of router");
				return;
			}
			for(String[] desc: routerDescription) {
				confStdDF(desc[1]);
			}
		}
		else if (input1.equals("new") && input2.equals("one")) {
			printRouterList();
			while(addr == null) {
				System.out.print("Choose the router: ");
				addr = System.console().readLine();
				if(!routerAddrs.contains(addr)) 
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
			/*if(filepos.equals("new")) 
				defineNewClass(true, addr);
			else */
				applyNewClass(nameFromPosition(pos), addr);
			
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
			/*if(filepos.equals("new")) 
				defineNewClass(true, addr);
			else {*/
				System.out.println("\n" + "Getting router list");
				routerDescription = getRouterDesc();
				if (routerDescription == null) {
					System.err.println("Error retrieving list of router");
					return;
				}

				for(String[] desc: routerDescription) 
					applyNewClass(nameFromPosition(pos), desc[1]);
			//}
			
			
		}
	}
	
//******************************************************************************************************************************************* 	
	
	public String defineNewClass(boolean calledByFunction, String ip) {
		if (calledByFunction == true && (ip == null || ip.isEmpty())){
			System.out.println("IP of the router is not known. Exiting.");
			System.exit(1);
		}
		System.out.printf("Enter name of the new class: \n");
		System.out.println("Enter .exit to return back");
		String filename = System.console().readLine();
		if(filename.equals(".exit"))
			return null;
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
					boolean checked = false;
					do {
						System.out.print("\n\nSet a DSCP value for this class (literal or numerical, 0 is default): ");
						userResp = System.console().readLine();
						try {
							int dscpIntValue = Integer.parseInt(userResp);
							checked = this.dscpValues.containsValue(dscpIntValue) ? true : false; 
						}
						catch (NumberFormatException e){
							checked = this.dscpValues.containsKey(userResp.toUpperCase()) ? true : false;
						}
					} while (!checked);
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
					do {
						input = System.console().readLine();
						if (input.equals(".exit") == true) break;
						classFile.println(input);
					} while (true);
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
	
//******************************************************************************************************************************************* 	
	
	public void showRunningConf() throws IOException {

		printRouterList();
		while(userInput == null) {
			System.out.println("Choose the router IP of the list to which you want the running-conf:");
			userInput = System.console().readLine();
			if (!getRouterAddresses().contains(userInput)) {
				userInput = null;
			}
		}
		OSPFRouter router = new OSPFRouter(userInput);
		router.connect(USER, PASSWORD);
		String conf = router.getRunningConfig();
		router.disconnect();
		System.out.println(conf);
	}
	
//******************************************************************************************************************************************* 
	
	/**
	 * Get the description of the router. It includes the hostname, the router ID and if the router is 
	 * an Area Border ROuter or not.
	 * @return List of router descriptions. Return null if the connection to all the router has failed.
	 */
	private List<String[]> getRouterDesc(){
		try {
			List<String[]> descriptions = new LinkedList<>();
			List<String> addresses = null;
			Runtime rt = Runtime.getRuntime();
			while(addresses == null) {
				Process vtysh = rt.exec(VTYCOMMAND);
				if (vtysh == null) {
					System.err.println("Error in command string");
					return null;
				}
				else {
					addresses = getRouterIds(vtysh.getInputStream());
					addresses.forEach(ip -> {
						String[] routerDesc = new String[3];	// hostname, ip and if ABR or not
						OSPFRouter router = new OSPFRouter(ip);
						try {
							router.connect(USER, PASSWORD);
							routerDesc[0] = router.getHostname();
							routerDesc[1] = ip;
							routerDesc[2] = router.isBorderRouter() ? "ABR" : "";
							router.disconnect();
							descriptions.add(routerDesc);
						} 
						catch (IOException e) {
							System.err.println("Error connecting to " + ip);
						}
					});
				}
			}
			return descriptions;
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
			String id = matcher.group();
			// Add address which doesn't belong to this machine
			if (!localAddress.contains(id)) {				
				routerIds.add(id);
			}
		}
		return routerIds;
	}
	
//******************************************************************************************************************************************* 	
	
	private void printRouterList() {
		System.out.println("Getting router list");
		routerDescription = getRouterDesc();
		if (routerDescription == null) {
			System.err.println("Error retrieving list of router");
			return;
		}
		// Print router list and make the user to choose
		System.out.println("List of routers:");
		for(String[] desc: routerDescription) {
			System.out.println(desc[0] + "\t" + desc[1] + "\t" + desc[2]);
		}
		System.out.println();
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
		//System.out.println("new: create new one\n");
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
	
	private void applyNewClass(String file, String ip) {
		
		System.out.println("Applying new class TEST");
		try {
			SSHClient ssh = new SSHClient();
			ssh.addHostKeyVerifier(new PromiscuousVerifier());
			try {
				ssh.connect(ip);
				ssh.authPassword(USER, PASSWORD);
				Session session = ssh.startSession(); 
				Shell shell = session.startShell();
				redirect(shell, session, true);;
				Expect expect = new ExpectBuilder()
		                .withOutput(shell.getOutputStream())
		                .withInputs(shell.getInputStream(), shell.getErrorStream())
		                .build();
			    try {	
			    	execute(expect, "enable");
			    	execute(expect, "cisco");
			    	execute(expect, "configure terminal");
					BufferedReader br = new BufferedReader(new FileReader(CLASSDIR + file));
					for(String line; (line = br.readLine()) != null; ) 
							execute(expect, line);
			    } finally {
			    	execute(expect, "disable");
			    	execute(expect, "exit");
			    	System.out.println("Class applied successfully!");
				    expect.close();
					session.close();
				} 
			}
			finally {
				ssh.disconnect();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private List<String> getRouterAddresses(){
		List<String> routerAddrs = new LinkedList<>();
		for (String[] desc : routerDescription) {
			routerAddrs.add(desc[1]);
		}
		return routerAddrs;
	}
	
	/**
	 * Return a list of all the integers contained in s
	 * @param s A String that contains some integers
	 * @return All integers inside the string s
	 */
	private List<Integer> getIntValues(String s) {	
		Matcher m = Pattern.compile("(\\d+)", Pattern.MULTILINE).matcher(s);
		List<Integer> valueList = new ArrayList<>();
		while(m.find()) {
			valueList.add(Integer.parseInt(m.group()));
		}
		return valueList;
	}
	
	public void verifyNewClass(String classFileName) throws IOException {
		Path classPath = Paths.get(CLASSDIR, classFileName);
		List<String> rows = Files.readAllLines(classPath);
		boolean saveFile = false;
		System.out.println("The content of the class " + classFileName + " is the following:\n");
		printClassContent(rows);
		System.out.println("Insert the line or the lines (comma separated list) you want them to be changed (type no if it is all right):");
		String userInput = System.console().readLine();
		if (isCommaSeparated(userInput)) {
			int maxRows = rows.size();
			for (Integer rowNum : getIntValues(userInput)) {
				try {
					String oldLine = rows.get(rowNum - 1); // We count from 0, user from 1
					System.out.println("Old row value: ");
					System.out.println(oldLine);
					System.out.println("Insert new line below :");
					String newLine = System.console().readLine();
					if (!oldLine.equals(newLine)) {
						saveFile = true;
						rows.set(rowNum, newLine);
					}
				}
				catch (NumberFormatException | IndexOutOfBoundsException e ) {
					System.err.println("You have inserted an invalid row number for this file: " + rowNum);
					System.err.println("Max number of rows: " + maxRows);
				}
			}
			if (saveFile == true) {
				Files.write(classPath, rows);
			}
		}
		else {
			System.err.println("You not have inserted a comma separated list");
		}
	}
	
	private boolean isCommaSeparated(String s) {
		Pattern pattern = Pattern.compile("^(\\d+)(,\\s*\\d+)*$|^no$");
		Matcher match = pattern.matcher(s);
		return match.matches();
	}
	
	private void execute(Expect expect, String cmd) throws IOException{
		expect.send(cmd);
		expect.sendLine();
		return;
	}
	
	private void printClassContent(List<String> fileContent) {
		int rowNum = 0;
		for (String row : fileContent) {
			rowNum ++; 
			System.out.printf("%d %s\n", rowNum, row);
		}
	}
		
//*******************************************************************************************************************************************
	/**
	 * Connects and makes the user to choose one or more standard classes to define on router <b>ip</b>
	 * @param ip	The IP address of the router on which to apply the standard class/classes 
	 * @throws IOException If a connection error occours
	 */
	private void confStdDF(String ip) throws IOException {
		List<String> selectedClasses = confStdMenu();
		
		// Connect to the router to retrieve only the interface network informations. This is needed because 
		// the configuration process can take longer time than the ssh disconnection timeout.
		OSPFRouter router = new OSPFRouter(ip);
		router.connect(USER, PASSWORD);
		List<Pair<String, String>> ifaceList = router.getInterfaceNetwork();
		router.disconnect();
		
		// Ask the user all the parameters needed for the conf serv to be set up.
		System.out.println("The following is the list of interfaces of " + ip);
		System.out.println("#  Interface\t\tAddress");
		int i = 1;
		for(Pair<String, String> iface : ifaceList) {
			System.out.println(i + "  " + iface.getKey() + "\t\t" + iface.getValue());
			i ++;
		}
		String input;
		int ifaceListSize = ifaceList.size();
		do {// At least there is an interface at this point, the one with ip as address
			System.out.printf("Choose the interface to apply the diffserv" +
							  (selectedClasses.size() > 1 ? " classes" : " class") +
							   " (1-%d): ", ifaceListSize);
			input = System.console().readLine();
		}while (!isInsideBound(input, 1, ifaceListSize));
		List<String> commands = getStdClassConfigureCommands(ifaceList.get(Integer.parseInt(input)),
															 selectedClasses); 
		router.connect(USER, PASSWORD);
		router.configureRouter(commands);
		router.disconnect();
	}
	
	/**
	 * Read the file of the standard classes and return the list of command from it
	 * @param className The name of the class file to be read
	 * @return	List of commands written into the file
	 * @throws IOException If reading the file makes some problems
	 */
	private List<String> readClassCommands(String className) throws IOException{
		return Files.readAllLines(Paths.get(CLASSDIR, className));
	}
	
	private List<String> getStdClassConfigureCommands(Pair<String, String> pair, List<String> classes) {
		List<String> commands = new LinkedList<>();
		int accessListNum = 101;
		
		// Define different access lists, one for each standard class
		for (String dsClass : classes) {
			switch (dsClass) {
				case "WEB":
					commands.add("access-list " + accessListNum + " permit tcp any any range www 81");
					break;
					
				case "VIDEO":
				case "VOIP":
					commands.add("access-list " + accessListNum + " permit udp any any");
					break;
					
				case "EXCESS":
					commands.add("access-list " + accessListNum + " permit any");
					break;
					
				default:
					throw new IllegalArgumentException("no standard class called " + dsClass);
			}
			commands.add("class-map match-all " + dsClass);
			commands.add("match access-group " + accessListNum);
			commands.add("exit");
			accessListNum ++;
		}
		
		// Redo the same loop for setting policy
		String ifaceName = pair.getKey();
		
		// Set the policy name, i.e. Et11 
		String policyName = (ifaceName.substring(0, 2) + 
							 ifaceName.substring(ifaceName.length() - 3, ifaceName.length()))
							.replaceAll("/","");
		commands.add("policy-map " + policyName);
		for (String dsClass : classes) {
			try {
				commands.addAll(readClassCommands(dsClass));
				commands.add("exit");
			}
			catch (IOException e) {
				System.err.println("Can't read file " + dsClass);
				return null;
			}
		}
		return commands;
	}
	

	public boolean testIsBorderRouter(String ip) throws IOException {
		OSPFRouter router = new OSPFRouter(ip);
		router.connect(USER, PASSWORD);
		boolean isRouterBR = router.isBorderRouter();
		router.disconnect();
		return isRouterBR;
	}
	
	
	private List<String> confStdMenu() { 
		System.out.println("Choose which class or classes to apply: \n"
				+ "1- VoIP\n"
				+ "2- Video\n"
				+ "3- Web\n"
				+ "4- Excess\n"
				+ "You can insert a comma separated list if you want to apply more than one class");
		String userInput;
		do {			
			userInput = System.console().readLine();
		} while (!isCommaSeparated(userInput));
		List<String> cl = new ArrayList<>();
		for (Integer value : getIntValues(userInput)) {
			switch (value.intValue()) {
				case 1:
					if (!cl.contains("VOIP")) {
						cl.add("VOIP");
					}
					break;
				case 2:
					if (!cl.contains("Video")) {
						cl.add("VIDEO");
					}
					break;
				case 3:
					if (!cl.contains("WEB")) {
						cl.add("WEB");
					}
					break;
				case 4:
					if (!cl.contains("EXCESS")) {
						cl.add("EXCESS");
					}
					break;
				default:
					break;
			}
		}
		return cl;
	}
	
//*******************************************************************************************************************************************
	//aux
	private String nameFromPosition(int position) {
	    String match = fileList[position].getName();
		return match;
	}
}





