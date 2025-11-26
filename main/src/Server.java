import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Server {

    // Files for persistence
    private static final Path ACCOUNTS_FILE = Path.of("accounts.txt");
    private static final Path LEDGER_FILE = Path.of("ledger.txt");

    private final AccountStore accountStore = new AccountStore();
    private final Ledger ledger = new Ledger();
    private final BankService bankService = new BankService(accountStore, ledger);

    private final Set<String> activeUsers = new HashSet<>();
    private final ThreadPool threadPool;

    private InterestThread interestThread;

    private volatile boolean running = true;

    public Server(int port, int poolSize, double interestRate, long interestPeriodMillis) {
        threadPool = new ThreadPool(poolSize);
        loadData();
        startInterestThread(interestRate, interestPeriodMillis);
        startNetworkListener(port);
        adminMenu();
        shutdown();
    }

    // Load accounts & ledger from file
    private void loadData() {
        try {
            accountStore.loadFrom(ACCOUNTS_FILE);
            System.out.println("[SERVER] Accounts loaded.");
        } catch (IOException e) {
            System.out.println("[SERVER] No accounts file found.");
        }

        try {
            ledger.loadFrom(LEDGER_FILE);
            System.out.println("[SERVER] Ledger loaded.");
        } catch (IOException e) {
            System.out.println("[SERVER] No ledger file found.");
        }
    }

    // Start background interest thread
    private void startInterestThread(double rate, long periodMillis) {
        interestThread = new InterestThread(bankService, rate, periodMillis);
        Thread t = new Thread(interestThread);
        t.setDaemon(true); // won't block shutdown
        t.start();
        System.out.println("[SERVER] Interest thread started.");
    }

    // Start accepting client connections asynchronously
    private void startNetworkListener(int port) {
        Thread listener = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("[SERVER] Listening on port " + port);

                while (running) {
                    Socket client = serverSocket.accept();
                    System.out.println("[SERVER] Client connected: " + client.getRemoteSocketAddress());
                    threadPool.execute(new ClientHandler(client, bankService, activeUsers));
                }

            } catch (IOException e) {
                if (running) {
                    System.err.println("[SERVER] Error: " + e.getMessage());
                }
            }
        });

        listener.setDaemon(true);
        listener.start();
    }

    // Admin menu runs in the server terminal
    private void adminMenu() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            System.out.println("\n=== ADMIN MENU ===");
            System.out.println("1. View all active users");
            System.out.println("2. View all transactions");
            System.out.println("3. Credit a user");
            System.out.println("4. Debit a user");
            System.out.println("5. Transfer funds (admin)");
            System.out.println("6. Change interest rate");
            System.out.println("7. Change interest period");
            System.out.println("9. Shutdown server");
            System.out.print("Choice: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    showActiveUsers();
                    break;
                case "2":
                    showAllTransactions();
                    break;
                case "3":
                    adminCredit(scanner);
                    break;
                case "4":
                    adminDebit(scanner);
                    break;
                case "5":
                    adminTransfer(scanner);
                    break;
                case "6":
                    changeInterestRate(scanner);
                    break;
                case "7":
                    changeInterestPeriod(scanner);
                    break;
                case "9":
                    running = false;
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    // Admin actions

    private void showActiveUsers() {
        synchronized (activeUsers) {
            System.out.println("Active users: " + activeUsers);
        }
    }

    private void showAllTransactions() {
        System.out.println("=== ALL TRANSACTIONS ===");
        for (Transaction t : bankService.getAllTransactions()) {
            System.out.println(t);
        }
    }

    private void adminCredit(Scanner sc) {
        System.out.print("User: ");
        String user = sc.nextLine().trim();
        System.out.print("Amount to add: ");
        long amt = Long.parseLong(sc.nextLine().trim());

        try {
            bankService.deposit(user, amt);
            System.out.println("Credited " + amt + " to " + user);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void adminDebit(Scanner sc) {
        System.out.print("User: ");
        String user = sc.nextLine().trim();
        System.out.print("Amount to remove: ");
        long amt = Long.parseLong(sc.nextLine().trim());

        try {
            bankService.withdraw(user, amt);
            System.out.println("Debited " + amt + " from " + user);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void adminTransfer(Scanner sc) {
        System.out.print("From user: ");
        String from = sc.nextLine().trim();
        System.out.print("To user: ");
        String to = sc.nextLine().trim();
        System.out.print("Amount: ");
        long amt = Long.parseLong(sc.nextLine().trim());

        if (bankService.transfer(from, to, amt))
            System.out.println("Transfer complete.");
        else
            System.out.println("Transfer failed.");
    }

    private void changeInterestRate(Scanner sc) {
        System.out.print("New rate (e.g. 0.025): ");
        double rate = Double.parseDouble(sc.nextLine().trim());
        interestThread.setRate(rate);
        System.out.println("Interest rate updated.");
    }

    private void changeInterestPeriod(Scanner sc) {
        System.out.print("New period in milliseconds: ");
        long p = Long.parseLong(sc.nextLine().trim());
        interestThread.setPeriod(p);
        System.out.println("Interest period updated.");
    }

    // Shutdown logic
    private void shutdown() {
        System.out.println("[SERVER] Shutting down...");

        interestThread.stopRunning();
        threadPool.shutdown();

        try {
            accountStore.saveTo(ACCOUNTS_FILE);
            System.out.println("[SERVER] Accounts saved.");
        } catch (IOException e) {
            System.err.println("[SERVER] Failed to save accounts: " + e.getMessage());
        }

        try {
            ledger.saveTo(LEDGER_FILE);
            System.out.println("[SERVER] Ledger saved.");
        } catch (IOException e) {
            System.err.println("[SERVER] Failed to save ledger: " + e.getMessage());
        }

        System.out.println("[SERVER] Shutdown complete.");
    }

    // main
    public static void main(String[] args) {
        int port = 5000;
        int poolSize = 10;
        double interestRate = 0.025;
        long interestPeriod = 60_000;

        new Server(port, poolSize, interestRate, interestPeriod);
    }
}
