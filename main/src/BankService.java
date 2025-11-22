import java.util.ArrayList;
import java.util.List;

public class BankService {

    private final AccountStore accounts;
    private final Ledger ledger;

    public BankService(AccountStore accounts, Ledger ledger) {
        if (accounts == null || ledger == null) {
            throw new IllegalArgumentException("AccountStore and Ledger must not be null");
        }
        this.accounts = accounts;
        this.ledger = ledger;
    }

    public Account login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        Account account = accounts.getAccount(username);
        if (account == null) {
            return null;
        }
        if (!account.checkPassword(password)) {
            return null;
        }

        return account;
    }

    public long getBalance(String username) {
        Account account = accounts.getAccount(username);
        if (account == null) {
            throw new IllegalArgumentException("Unknown user: " + username);
        }
        return account.getBalance();
    }

    public void deposit(String user, long amount) {
        Account account = accounts.getAccount(user);
        if (account == null) {
            throw new IllegalArgumentException("Unknown user: " + user);
        }
        account.deposit(amount);
        ledger.append(Transaction.deposit(user, amount));
    }

    public boolean withdraw(String user, long amount) {
        Account account = accounts.getAccount(user);
        if (account == null) {
            throw new IllegalArgumentException("Unknown user: " + user);
        }
        boolean ok = account.withdraw(amount);
        if (ok) {
            ledger.append(Transaction.withdrawal(user, amount));
        }
        return ok;
    }


     //transfer funds between two accounts using a consistent lock ordering to avoid deadlock.
    public boolean transfer(String fromUser, String toUser, long amount) {
        if (fromUser.equals(toUser)) {
            return false;
        }

        Account from = accounts.getAccount(fromUser);
        Account to = accounts.getAccount(toUser);

        if (from == null || to == null) {
            throw new IllegalArgumentException("Unknown account in transfer");
        }

        // Ensure consistent lock order to prevent deadlocks
        Account first, second;
        if (fromUser.compareTo(toUser) < 0) {
            first = from;
            second = to;
        } else {
            first = to;
            second = from;
        }

        Object firstLock = first.getLock();
        Object secondLock = second.getLock();

        synchronized (firstLock) {
            synchronized (secondLock) {
                // Now both accounts are locked.
                if (!from.withdraw(amount)) {
                    return false; // insufficient funds
                }
                to.deposit(amount);
                ledger.append(Transaction.transfer(fromUser, toUser, amount));
                return true;
            }
        }
    }

    public void applyInterest(String user, double interestRate) {
        if (interestRate <= 0) {
            throw new IllegalArgumentException("Interest rate must be > 0");
        }
        Account account = accounts.getAccount(user);
        if (account == null) {
            throw new IllegalArgumentException("Unknown user: " + user);
        }

        long balance = account.getBalance();
        long interestAmount = (long) Math.floor(balance * interestRate);

        if (interestAmount <= 0) {
            return; // nothing to apply for small balances/rates
        }

        account.deposit(interestAmount);
        ledger.append(Transaction.interest(user, interestAmount));
    }


     // Helper for the interest background thread to apply interest to all accounts.

    public void applyInterestToAll(double interestRate) {
        List<Account> snapshot = new ArrayList<>(accounts.allAccounts());
        for (Account a : snapshot) {
            applyInterest(a.getUsername(), interestRate);
        }
    }

    public List<Transaction> getTransactions(String user) {
        return ledger.findUser(user);
    }

    public List<Transaction> allTransactions() {
        return ledger.all();
    }

    public Account createAccount(String username, String password) {
        return accounts.createAccount(username, password);
    }

    public boolean accountExists(String username) {
        return accounts.accountExists(username);
    }

    public AccountStore getAccountStore() {
        return accounts;
    }

    public Ledger getLedger() {
        return ledger;
    }
}
