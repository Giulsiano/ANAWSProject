import java.io.IOException;
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.util.Pair;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Shell;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.sf.expectit.Expect;
import net.sf.expectit.ExpectBuilder;

import static net.sf.expectit.filter.Filters.removeColors;
import static net.sf.expectit.filter.Filters.removeNonPrintable;
import static net.sf.expectit.filter.Filters.replaceInString;
import static net.sf.expectit.matcher.Matchers.contains;
import static net.sf.expectit.matcher.Matchers.regexp;
import static net.sf.expectit.matcher.Matchers.exact;
import static net.sf.expectit.matcher.Matchers.eof;

/**
 * This Class models the interaction to and from the Router the user wants to connect to. For now it
 * supports only IPv4 connections.
 * 
 * @author user
 *
 */
public class OSPFRouter {
	private String ip;
	private String hostname;
	private StringBuilder buffer;
	private SSHClient ssh;
	private Session session;
	private Shell shell;
	private Expect exp;


	private boolean isConnected;
	private boolean isRoot;
	private boolean isConfT;
	public boolean isRoot() {
		return isRoot;
	}

	public static final String ROOTPROMPT = "#";
	public static final String PROMPT = ">";
	public static final String PASSWORDPROMPT = "Password: ";
	private static final String PASSWORD = "cisco";
	private int capacity = 10*1024; // 10 KB of buffer
	
	/**
	 * Create new instance of OSPFRouter. It allocates the ssh client instance and add a promiscuous verifier
	 * to avoid ssh checking.
	 */
	private void routerInit() {
		ssh = new SSHClient();
		ssh.addHostKeyVerifier(new PromiscuousVerifier());
		this.buffer = new StringBuilder(this.capacity);
		this.isConnected = this.isRoot = false;
		this.isConfT = false;
	}
	
	public OSPFRouter(String ip) {
		routerInit();
		this.ip = ip;
	}
	
	public OSPFRouter(String ip, int capacity) {
		this.capacity = capacity;
		routerInit();
		this.ip = ip;
	}
	
	/**
	 * Create a ssh session to this router. It also open the default shell to make expect to interact with
	 * the router.
	 * @param username The username to connect with
	 * @param password The password of the user. Only password authentication is allowed for now
	 * @throws IOException If something goes wrong during the connection setup
	 */
	public void connect(String username, String password) throws IOException {
		if (username == null || password == null) {
			throw new IllegalArgumentException("Username or password can't be null");
		}
		if (this.ip == null) {
			throw new NullPointerException("IP for this router is not set");
		}
		ssh.connect(this.ip);
		ssh.authPassword(username, password);
		session = ssh.startSession();
		session.allocateDefaultPTY();
		shell = session.startShell();
		exp =  new ExpectBuilder()
				.withOutput(shell.getOutputStream())
				.withInputs(shell.getInputStream(), shell.getErrorStream())
				.withEchoInput(System.out)
				.withEchoOutput(System.err)
				.withInputFilters(removeColors(), 
						removeNonPrintable(), 
						replaceInString("^!(.*)$", ""))
				.withExceptionOnFailure()
				.build();
		hostname = exp.expect(regexp(PROMPT)).getBefore().trim();
		// make the router to send all the command output without --more-- option
		exp.sendLine("terminal length 0");
		isConnected = true;
	}

	public void sendCommand(String command) throws IOException {
		// clear the buffer to store only the output of the last command
		exp.sendLine(command);
	}
	
	public String getHostname() throws IOException {
		return hostname;
	}
	
	/**
	 * @return true if someone has called connect(), false otherwise.
	 */
	public boolean isConnected() {
		return isConnected;
	}
	
	public Shell getShell() {
		return shell;
	}
	
	/**
	 * @return The Expect object for interacting with the router
	 */
	public Expect getExpectIt() {
		return exp;
	}

	public void disconnect() throws IOException {
		if (!ssh.isConnected()) {
			return;
		}
		if (session.isOpen()) {
			if (exp != null) {
				exp.close();
			}
			session.close();
		}
		ssh.disconnect();
	}
	
	/**
	 * Ask the router to return descriptions of interfaces, that is the name of the interface and
	 * the network address of each interface. This consider only IPv4 addresses to return to the user.
	 * @return A Map which keys are the name of the interfaces and the value is the address of the network
	 * the interface is attached to.
	 * @throws IOException In case of sending command error
	 */
	public List<Pair<String, String>> getInterfaceNetwork() throws IOException{
		if (!this.isConnected()) {
			throw new ConnectionException("Router is not connected");
		}
		enableRootPrompt();
		String out = exp.sendLine("show ip interface brief")
								  .expect(contains(hostname + ROOTPROMPT))
								  .getBefore();
		String ifaceDesc = out.substring(out.indexOf("Interface"), out.length() - 1);
		Matcher ifNetMatch = Pattern.compile("^(.*\\d/\\d)\\s+((\\d+\\.){3}(\\d+))").matcher(ifaceDesc);
		List<Pair<String, String>> ifaceList =  new LinkedList<>();
		while(ifNetMatch.find()) {
			Pair<String, String> pair = new Pair<String, String>(ifNetMatch.group(1), ifNetMatch.group(2));
			ifaceList.add(pair);
		}
		disableRootPrompt();
		return ifaceList;
	}
	
	/**
	 * Enable the root prompt on the router.
	 * @throws IOException
	 */
	public void enableRootPrompt() throws IOException {
		if (!this.isConnected()) {
			throw new ConnectionException("Router is not connected");
		}
		if (this.isConfT) {
			exp.sendLine("end").expect(contains(ROOTPROMPT));
			this.isConfT = false;
		}
		if (this.isRoot()) return;
		exp.sendLine("enable").expect(contains(PASSWORDPROMPT));
		exp.sendLine(PASSWORD).expect(contains(hostname + ROOTPROMPT));
		this.isRoot = true;
	}
	
	public void disableRootPrompt() throws IOException {
		if (!this.isConnected()) {
			throw new ConnectionException("Router is not connected");
		}
		if (this.isConfT) {
			exp.sendLine("end").expect(contains(hostname + ROOTPROMPT));
			this.isConfT = false;
		}
		exp.sendLine("disable").expect(contains(hostname + PROMPT));
		this.isRoot = false;
	}
	
	public void setConfigureTerminal() throws IOException {
		enableRootPrompt();
		exp.sendLine("conf t").expect(contains("(config)"));
		this.isConfT = true;
	}
	
	public void unsetConfigureTerminal() throws IOException{
		if (!this.isConfT) {
			return;
		}
		exp.sendLine("end").expect(contains(hostname + ROOTPROMPT));
		this.isConfT = false;
	}
	
	/**
	 * Send the list commands to the router
	 * @param commands
	 * @throws IOException 
	 */
	public void configureRouter (List<String> commands) throws IOException {
		if (commands.isEmpty()) {
			return;
		}
		if (!this.isConfT) {
			setConfigureTerminal();
		}
		for (String cmd : commands) {
			exp.sendLine(cmd).expect(contains("config"));
		}
		unsetConfigureTerminal();
	}
	
	public String getRunningConfig () throws IOException{
		if (!this.isConnected()) {
			throw new ConnectionException("Router is not connected");
		}
		enableRootPrompt();
		String conf = exp.sendLine("show running-config")
					     .expect(regexp(hostname + ROOTPROMPT))
					     .getBefore();
		disableRootPrompt();
		return conf;
	}
}
