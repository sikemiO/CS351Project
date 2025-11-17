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

    // not sure if we need a hash here considering its just a project
    public boolean checkPassword(String candidate) {
        return password.equals(candidate);
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

    /**
     * Attempt to withdraw. Returns true on success, false if there were
     * insufficient funds. Balance is never allowed to go below zero.
     */
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

    //Expose the lock for higher-level operations (e.g. transfer).
    //Only BankService should normally use this.

    Object getLock() {
        return lock;
    }
}
