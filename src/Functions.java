import java.io.IOException;
import java.util.concurrent.TimeUnit;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;

public class Functions {

	public static void connectToRouter() throws IOException {
	        final SSHClient ssh = new SSHClient();
	        ssh.loadKnownHosts();
	        ssh.connect("localhost");
	        try {
	        	String[] command = {"/usr/bin/vtysh", "-d", "ospfd", "-c", "show ip ospf database"};
	            ssh.authPassword("user", "user");
	            Runtime rt = Runtime.getRuntime();
	            Process vtysh = rt.exec(command, null, null);
	            System.out.println(IOUtils.readFully(vtysh.getInputStream()).toString());
	            final Session session = ssh.startSession();
	            try {	                
	            	final Command cmd = session.exec("ping -c 1 8.8.8.8");
	                System.out.println(IOUtils.readFully(cmd.getInputStream()).toString());
	                cmd.join(5, TimeUnit.SECONDS);
	                System.out.println("\n** exit status: " + cmd.getExitStatus());
	            } finally {
	                session.close();
	            }
	        } finally {
	            ssh.disconnect();
	        }
	        System.exit(0);
		
	}
	
	public static void shwTopology() {
		
	}
	
	public static void configureDF() {
		
	}
	
	public static void defineNewClass() {
		
	}
	
	public static void showRunningConf() {
		
	}
}

