import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AccountStore implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Account> accounts = new HashMap<>();

    public AccountStore() {
    }

    public synchronized boolean accountExists(String username) {
        return accounts.containsKey(username);
    }

    public synchronized Account getAccount(String username) {
        return accounts.get(username);
    }

    /**
     * Create a new account with starting balance £1000.
     * Returns the new account, or null if username already exists.
     */
    public synchronized Account createAccount(String username, String password) {
        if (accounts.containsKey(username)) {
            return null;
        }
        Account account = new Account(username, password, 1000L);
        accounts.put(username, account);
        return account;
    }

    public synchronized Collection<Account> allAccounts() {
        return accounts.values();
    }

    public synchronized void saveTo(Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (Account account : accounts.values()) {
                long balance = account.getBalance();
                writer.write(account.getUsername() + ";" +
                        getPasswordForSave(account) + ";" +
                        balance);
                writer.newLine();
            }
        }
    }

    private String getPasswordForSave(Account account) {
        return account.getPasswordForPersistence();
    }


    //password helper
    private String getPasswordForSave(Account account) {
        return account.getPasswordForPersistence();
    }

    public synchronized void loadFrom(Path path) throws IOException {
        accounts.clear();
        if (!Files.exists(path)) {
            return; // nothing to load yet
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length != 3) continue; // skip bad lines
                String username = parts[0];
                String password = parts[1];
                long balance;
                try {
                    balance = Long.parseLong(parts[2]);
                } catch (NumberFormatException e) {
                    continue;
                }
                Account account = new Account(username, password, balance);
                accounts.put(username, account);
            }
        }
    }
}
