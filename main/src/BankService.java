import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Instant;

public class BankService {

    private final AccountStore accountStore;
    private final Ledger ledger;

     // Listener interface for balance changes.

    public interface BalanceListener {
        void onBalanceChanged(String username, long newBalance, String message);
    }

    private final Map<String, CopyOnWriteArrayList<BalanceListener>> listeners =
            new ConcurrentHashMap<>();

    public BankService(AccountStore accountStore, Ledger ledger) {
        if (accountStore == null || ledger == null) {
            throw new IllegalArgumentException("AccountStore and Ledger must not be null");
        }
        this.accountStore = accountStore;
        this.ledger = ledger;
    }

    // Authentication and account management

    public Account login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        Account acc = accountStore.getAccount(username);
        if (acc == null) return null;
        return acc.checkPassword(password) ? acc : null;
    }

    // if username doesn't exist, sets up new account with balance
    public Account createAccount(String username, String password) {
        if (username == null || username.isBlank()) return null;
        if (password == null || password.isBlank()) return null;
        return accountStore.createAccount(username, password);
    }

    // account operations
    public long getBalance(String username) {
        Account acc = accountStore.getAccount(username);
        if (acc == null) {
            throw new IllegalArgumentException("No such account: " + username);
        }
        return acc.getBalance();
    }

    // deposit, return new balance
    public long deposit(String username, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be > 0");
        }
        Account acc = accountStore.getAccount(username);
        if (acc == null) {
            throw new IllegalArgumentException("No such account: " + username);
        }

        acc.deposit(amount);
        ledger.append(Transaction.deposit(username, amount));
        long newBalance = acc.getBalance();
        notifyListeners(username, newBalance,
                "Deposit of " + amount + " applied. New balance: " + newBalance);
        return newBalance;
    }

    // withdraw, return balance if successful, throw exception if not
    public long withdraw(String username, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be > 0");
        }
        Account acc = accountStore.getAccount(username);
        if (acc == null) {
            throw new IllegalArgumentException("No such account: " + username);
        }

        boolean ok = acc.withdraw(amount);
        if (!ok) {
            // not allowed to go below zero
            throw new IllegalStateException("Insufficient funds for withdrawal");
        }

        ledger.append(Transaction.withdrawal(username, amount));
        long newBalance = acc.getBalance();
        notifyListeners(username, newBalance,
                "Withdrawal of " + amount + " applied. New balance: " + newBalance);
        return newBalance;
    }

    //transfer funds returns true/false, avoids deadlocks by getting account locks, ordered by account
    public boolean transfer(String fromUser, String toUser, long amount) {
        if (fromUser == null || toUser == null) return false;
        if (fromUser.equals(toUser)) return false;
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be > 0");
        }

        Account from = accountStore.getAccount(fromUser);
        Account to = accountStore.getAccount(toUser);
        if (from == null || to == null) {
            return false; // missing accounts
        }

        // stable lock order based on username to avoid deadlocks
        Account first, second;
        if (fromUser.compareTo(toUser) < 0) {
            first = from;
            second = to;
        } else {
            first = to;
            second = from;
        }

        synchronized (first.getLock()) {
            synchronized (second.getLock()) {
                // both accounts locked
                // Withdraw from the from account if possible
                if (!from.withdraw(amount)) {
                    return false; // to show insufficient funds
                }
                to.deposit(amount);
            }
        }

        // Record transaction and notify listeners after releasing locks.
        ledger.append(Transaction.transfer(fromUser, toUser, amount));

        long fromBal = from.getBalance();
        long toBal = to.getBalance();
        notifyListeners(fromUser, fromBal,
                "Transfer of " + amount + " sent to " + toUser + ". New balance: " + fromBal);
        notifyListeners(toUser, toBal,
                "Transfer of " + amount + " received from " + fromUser + ". New balance: " + toBal);

        return true;
    }

    // Interest operations used by the InterestThread


     //Applies interest to every account at the given rate.
     //safe to call from a background thread.

    public void applyInterest(double rate) {
        if (rate <= 0) {
            throw new IllegalArgumentException("Interest rate must be > 0");
        }

        Collection<Account> all = accountStore.allAccounts();
        for (Account acc : all) {
            String user = acc.getUsername();
            long balance = acc.getBalance();
            if (balance <= 0) continue;

            long interest = Math.round(balance * rate);
            if (interest <= 0) continue;

            acc.deposit(interest);
            ledger.append(Transaction.interest(user, interest));
            long newBalance = acc.getBalance();
            notifyListeners(user, newBalance,
                    "Interest of " + interest + " applied. New balance: " + newBalance);
        }
    }

    // Ledger queries

    public List<Transaction> getUserTransactions(String username) {
        return ledger.findUser(username);
    }

    public List<Transaction> getAllTransactions() {
        return ledger.all();
    }

    // Listener registration – for online user notifications

    public void registerListener(String username, BalanceListener listener) {
        if (username == null || listener == null) return;
        listeners
                .computeIfAbsent(username, u -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    public void unregisterListener(String username, BalanceListener listener) {
        if (username == null || listener == null) return;
        CopyOnWriteArrayList<BalanceListener> list = listeners.get(username);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(username);
            }
        }
    }

    private void notifyListeners(String username, long newBalance, String message) {
        CopyOnWriteArrayList<BalanceListener> list = listeners.get(username);
        if (list == null || list.isEmpty()) {
            return;
        }
        String withTimestamp = "[" + Instant.now() + "] " + message;

        for (BalanceListener l : list) {
            try {
                l.onBalanceChanged(username, newBalance, withTimestamp);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }
}
