import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Ledger implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<Transaction> transactions = new ArrayList<>();

    public Ledger() {
    }

    public synchronized void append(Transaction t) {
        if (t == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        transactions.add(t);
    }


     //Returns a snapshot list of all transactions involving the given user.

    public synchronized List<Transaction> findUser(String username) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : transactions) {
            if (username.equals(t.getFrom()) || username.equals(t.getTo())) {
                result.add(t);
            }
        }
        return Collections.unmodifiableList(result);
    }

    ]
     // Returns a snapshot list of all transactions.
    ]
    public synchronized List<Transaction> all() {
        return Collections.unmodifiableList(new ArrayList<>(transactions));
    }
}
