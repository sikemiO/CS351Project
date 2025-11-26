import java.io.Serializable;

public class Account implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String username;
    private String password;
    private long balance;

    // Private lock object so we don't expose information
    private final Object lock = new Object();

    // password helper
    String getPasswordForPersistence() {
        synchronized (lock) {
            return password;
        }
    }

    public Account(String username, String password, long initialBalance) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        this.username = username;
        this.password = password;
        this.balance = initialBalance;
    }

    public String getUsername() {
        return username;
    }

    // Synchronised read to keep it consistent with setPassword
    public boolean checkPassword(String candidate) {
        if (candidate == null) return false;
        synchronized (lock) {
            return password.equals(candidate);
        }
    }

    public void setPassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        synchronized (lock) {
            this.password = newPassword;
        }
    }

    public long getBalance() {
        synchronized (lock) {
            return balance;
        }
    }

    // Deposit must be positive.
    public void deposit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be > 0");
        }
        synchronized (lock) {
            balance += amount;
        }
    }

    public boolean withdraw(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be > 0");
        }
        synchronized (lock) {
            if (balance < amount) {
                return false;
            }
            balance -= amount;
            return true;
        }
    }

    //expose lock for other operations
    Object getLock() {
        return lock;
    }
}
