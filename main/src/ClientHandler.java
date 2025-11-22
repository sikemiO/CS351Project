import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.Scanner;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final BankService bankService;
    private final Set<String> activeUsers;

    public ClientHandler(Socket socket, BankService bankService, Set<String> activeUsers) {
        this.socket = socket;
        this.bankService = bankService;
        this.activeUsers = activeUsers;
    }


    @Override
    public void run() {
        String currentUser = null;

        try (Scanner in = new Scanner(socket.getInputStream());
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("Connected to bank.");

            Account acc = login(in, out);
            if (acc == null) {
                out.println("Account not found.");
                return;
            }

            currentUser = acc.getUsername();
            activeUsers.add(currentUser);
            userMenu(currentUser, in, out);


        } catch (IOException e) {

        } finally {
            if (currentUser != null) {
                activeUsers.remove(currentUser);
            } try {
                socket.close();
            } catch (IOException e) {}
        }

        }


    private Account login(Scanner in, PrintWriter out) {
        while (true) {
            out.println("1. Login");
            out.println("2. Signup");
            out.println("3. Exit");
            out.print("Pick an option: ");

            if (in.hasNextLine()) {
                return null;
            }

            String option = in.nextLine();
            switch (option) {
                case "1":
                    out.println("Enter your username: ");
                    if (!in.hasNextLine()) {
                        return null;
                    }
                    String username = in.nextLine();

                    out.println("Enter your password: ");
                    if (!in.hasNextLine()) {
                        return null;
                    }
                    String password = in.nextLine();

                    Account account = bankService.login(username,password);
                    if (account != null) {
                        out.println("Login successful.");
                        return account;
                    } else {
                        out.println("Login failed.");
                    }
                    break;

                case "2":
                    out.print("Create a username: ");
                    if (!in.hasNextLine()) {
                        return null;
                    }
                    username = in.nextLine();
                    out.print("Create a password: ");
                    if (!in.hasNextLine()) {
                        return null;
                    }
                    password = in.nextLine();

                    account = bankService.createAccount(username,password);
                    if (account != null) {
                        out.println("Account created successfully.");
                        return account;
                    } else {
                        out.println("Account creation failed.");
                    }
                    break;

                    case "3":
                    return null;

                    default:
                    out.println("Invalid option.");
                    break;
            }
        }
    }

    private void userMenu(String currentUser, Scanner in, PrintWriter out) {
        while (true) {
            out.println("Welcome " + currentUser);
            out.println("1. Get balance");
            out.println("2. Deposit");
            out.println("3. Withdraw");
            out.println("4. Transfer");
            out.println("7. Get transactions");
            out.println("9. Logout");
        }
    }

}
