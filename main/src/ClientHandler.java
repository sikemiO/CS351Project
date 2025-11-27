import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final BankService bankService;
    private final Set<String> activeUsers;

    private String currentUser = null;
    private BankService.BalanceListener balanceListener = null;

    public ClientHandler(Socket socket, BankService bankService, Set<String> activeUsers) {
        this.socket = socket;
        this.bankService = bankService;
        this.activeUsers = activeUsers;
    }

    @Override
    public void run() {
        System.out.println("Client connected from " + socket.getRemoteSocketAddress());
        try (Socket s = this.socket;
             Scanner in = new Scanner(s.getInputStream());
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {

            out.println("Connected to bank.");

            boolean running = true;
            while (running) {
                if (currentUser == null) {
                    boolean loggedIn = loginMenu(in, out);
                    if (!loggedIn) {
                        running = false;
                    }
                } else {
                    boolean stayConnected = userMenu(currentUser, in, out);
                    if (!stayConnected) {
                        running = false;
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("I/O error with client " + e.getMessage());
        } finally {
            cleanup();
            System.out.println("Client disconnected from " + socket.getRemoteSocketAddress());
        }
    }

    // login / signup menu

    private boolean loginMenu(Scanner in, PrintWriter out) {
        while (currentUser == null) {

            out.println();
            out.println("=== Login Menu ===");
            out.println("1. Login");
            out.println("2. Signup");
            out.println("3. Exit");
            out.println("Pick an option: ");
            out.flush();

            if (!in.hasNextLine()) {
                return false;
            }

            String line = in.nextLine().trim();
            int choice;
            try {
                choice = Integer.parseInt(line);
            } catch (NumberFormatException e) {
                out.println("Invalid input, please enter a number.");
                continue;
            }

            switch (choice) {
                case 1:
                    handleLogin(in, out);
                    break;
                case 2:
                    handleCreateAccount(in, out);
                    break;
                case 3:
                    out.println("Goodbye.");
                    return false;
                default:
                    out.println("Unknown option, please try again.");
            }
        }
        return true;
    }

    private void handleLogin(Scanner in, PrintWriter out) {
        out.println("Username: ");
        out.flush();
        if (!in.hasNextLine()) return;
        String username = in.nextLine().trim();

        out.println("Password: ");
        out.flush();
        if (!in.hasNextLine()) return;
        String password = in.nextLine().trim();


        synchronized (activeUsers) {
            if (activeUsers.contains(username)) {
                out.println("This user is already logged in from another client.");
                return;
            }
        }

        Account acc = bankService.login(username, password);
        if (acc == null) {
            out.println("Login failed: invalid username or password.");
            return;
        }

        setCurrentUser(username, out);
        out.println("Login successful. Welcome, " + username + "!");
    }

    private void handleCreateAccount(Scanner in, PrintWriter out) {
        out.println("Choose a username: ");
        out.flush();
        if (!in.hasNextLine()) return;
        String username = in.nextLine().trim();

        out.println("Choose a password: ");
        out.flush();
        if (!in.hasNextLine()) return;
        String password = in.nextLine().trim();

        synchronized (activeUsers) {
            if (activeUsers.contains(username)) {
                out.println("That username is currently logged in. Please choose another.");
                return;
            }
        }

        Account acc = bankService.createAccount(username, password);
        if (acc == null) {
            out.println("Account creation failed: username may already exist or be invalid.");
            return;
        }

        setCurrentUser(username, out);
        out.println("Account created and logged in as " + username + ".");
    }

    private void setCurrentUser(String username, PrintWriter out) {
        this.currentUser = username;
        synchronized (activeUsers) {
            activeUsers.add(username);
        }

        this.balanceListener = (user, newBalance, message) -> {
            if (!username.equals(this.currentUser)) {
                return;
            }
            synchronized (out) {
                out.println();
                out.println("[NOTIFICATION] " + message);
                out.print("> ");
                out.flush();
            }
        };
        bankService.registerListener(username, this.balanceListener);
    }

    // user menu when logged in
    private boolean userMenu(String currentUser, Scanner in, PrintWriter out) {
        while (true) {

            out.println();
            out.println("=== User Menu (" + currentUser + ") ===");
            out.println("1. Get balance");
            out.println("2. Deposit");
            out.println("3. Withdraw");
            out.println("4. Transfer");
            out.println("5. Get transactions");
            out.println("9. Logout");
            out.print("Pick an option: ");
            out.flush();

            if (!in.hasNextLine()) {
                return false; // client disconnected
            }

            String line = in.nextLine().trim();
            int choice;
            try {
                choice = Integer.parseInt(line);
            } catch (NumberFormatException e) {
                out.println("Invalid input, please enter a number.");
                continue;
            }

            switch (choice) {
                case 1:
                    handleGetBalance(currentUser, out);
                    break;
                case 2:
                    handleDeposit(currentUser, in, out);
                    break;
                case 3:
                    handleWithdraw(currentUser, in, out);
                    break;
                case 4:
                    handleTransfer(currentUser, in, out);
                    break;
                case 5:
                    handleViewTransactions(currentUser, out);
                    break;
                case 9:
                    out.println("Logged out, goodbye.");
                    logout();
                    return false;
                default:
                    out.println("Invalid option.");
                    break;
            }
        }
    }

    private void handleGetBalance(String username, PrintWriter out) {
        try {
            long balance = bankService.getBalance(username);
            out.println("Current balance: " + balance);
        } catch (IllegalArgumentException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private void handleDeposit(String username, Scanner in, PrintWriter out) {
        out.println("Enter amount to deposit: ");
        out.flush();
        if (!in.hasNextLine()) return;
        String line = in.nextLine().trim();

        long amount;
        try {
            amount = Long.parseLong(line);
        } catch (NumberFormatException e) {
            out.println("Invalid amount. Please enter a whole number.");
            return;
        }

        try {
            long newBalance = bankService.deposit(username, amount);
            out.println("Deposit successful. New balance: " + newBalance);
        } catch (IllegalArgumentException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private void handleWithdraw(String username, Scanner in, PrintWriter out) {
        out.println("Enter amount to withdraw: ");
        out.flush();
        if (!in.hasNextLine()) return;
        String line = in.nextLine().trim();

        long amount;
        try {
            amount = Long.parseLong(line);
        } catch (NumberFormatException e) {
            out.println("Invalid amount. Please enter a whole number.");
            return;
        }

        try {
            long newBalance = bankService.withdraw(username, amount);
            out.println("Withdrawal successful. New balance: " + newBalance);
        } catch (IllegalArgumentException | IllegalStateException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private void handleTransfer(String username, Scanner in, PrintWriter out) {
        out.println("Enter target username: ");
        out.flush();
        if (!in.hasNextLine()) return;
        String target = in.nextLine().trim();

        out.print("Enter amount to transfer: \n");
        out.flush();
        if (!in.hasNextLine()) return;
        String line = in.nextLine().trim();

        long amount;
        try {
            amount = Long.parseLong(line);
        } catch (NumberFormatException e) {
            out.println("Invalid amount. Please enter a whole number.");
            return;
        }

        try {
            boolean ok = bankService.transfer(username, target, amount);
            if (ok) {
                out.println("Transfer successful.");
            } else {
                out.println("Transfer failed (insufficient funds or invalid account).");
            }
        } catch (IllegalArgumentException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private void handleViewTransactions(String username, PrintWriter out) {
        List<Transaction> txs = bankService.getUserTransactions(username);
        if (txs.isEmpty()) {
            out.println("No transactions found.");
            return;
        }
        out.println("Your transactions:");
        for (Transaction t : txs) {
            out.println(" - " + t);
        }
    }

    //cleanup
    private void logout() {
        if (currentUser != null) {
            if (balanceListener != null) {
                bankService.unregisterListener(currentUser, balanceListener);
                balanceListener = null;
            }
            synchronized (activeUsers) {
                activeUsers.remove(currentUser);
            }
            currentUser = null;
        }
    }

    private void cleanup() {
        logout();
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
        }
    }
}
