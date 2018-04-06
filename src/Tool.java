import java.io.IOException;
import java.util.Map;

//main del tool
public class Tool {
	
	static String input;
	
	public static void main(String[] args) throws IOException {
		Tool anawsTool = new Tool();
		System.out.println("Welcome in ANAWSTool!");
		while(true) {
			anawsTool.printMenu();
		}
	}
	
	public void printMenu() throws IOException {
		System.out.println("-------------------------------------------------------------------------\n"
				+ "1. Connect to router\n"
				+ "2. Show topology\n"
				+ "3. Configure DiffServ\n"
				+ "4. Define new Class\n"
				+ "5. Show router configurations\n"
				+ "-------------------------------------------------------------------------\n");
		System.out.println("Type .help for manual, .exit to close\n");
		waitForCommand();
	}
	
	public void waitForCommand() throws IOException {
		try {
			input = System.console().readLine();
			whatCommand(input);			
		}catch (NumberFormatException e) {
		}
	}
	
	public void whatCommand(String in) throws IOException {
		Functions func = new Functions();
		if(in.startsWith(".help")) {
			printManual();
		}
		
		if(in.startsWith(".exit"))
			System.exit(0);
		
		
		int cmd = Integer.parseInt(in);
		
		switch(cmd) {
			case 1:
				func.connectToRouter();
				break;
		
			case 2:
				func.showTopology();
				break;
			
			case 3:
				func.configureDF();
				break;
			
			case 4:
				String fileName = func.defineNewClass(false, null);
				if(fileName != null) func.verifyNewClass(fileName);
				break;
			
			case 5:
				func.showRunningConf();
				break;
			
			case 6:
				func.confStdDF("192.168.0.254");
				break;
				
			default:
				System.out.println("This command does not exist\n");
				waitForCommand();
				break;
		}	
	}
	
	public void printManual() throws IOException {
		System.out.println("1. Connect to router: consente all’admin di connettersi via ssh a un router.\n" + 
				"2. Show topology: consente di visualizzare la topologia della rete sfruttando il demone ospfd.\n" + 
				"3. Configure DiffServ: consente di configurare DiffServ su uno o tutti i router della rete in\n" + 
				"maniera automatizzata; una volta selezionata questa voce verrà richiesta all’admin se\n" + 
				"utilizzare le classi standard (Video, Web e Voip) oppure definirne di nuove.\n" + 
				"4. Define new class: consente di definire nuove classi di servizio per i flussi.\n" + 
				"5. Show router configurations: consente di visualizzare l’attuale configurazione dei router con lo scopo di verifica.\n");
		waitForCommand();
	}
	
}

