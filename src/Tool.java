
public class Tool {
	
	static String input;
	
	public static void main(String[] args) {
		System.out.println("Welcome in ANAWSTool!");
		printMenu();
	}
	
	public static void printMenu() {
		
		System.out.println("-------------------------------------------------------------------------\n"
				+ "1. Connect to router\n"
				+ "2. Show topology\n"
				+ "3. Configure DiffServ\n"
				+ "4. Define new Class\n"
				+ "5. Show router configurations\n"
				+ "-------------------------------------------------------------------------\n");
		System.out.println("Digit .help for manual, .exit to close\n");
		waitForCommand();
	}
	
	public static void waitForCommand() {
		input = System.console().readLine();
		whatCommand(input);
	}
	
	public static void whatCommand(String in) {
		
		if(in.startsWith(".help")) {
			printManual();
		}
		
		if(in.startsWith(".exit"))
			System.exit(0);
		
	}
	
	public static void printManual() {
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

