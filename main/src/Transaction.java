import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER,
        INTEREST
    }

    private final String id;
    private final Type type;
    private final Instant time;
    private final String from;
    private final String to;
    private final long amount;

    public Transaction(Type type, String from, String to, long amount) {
        this(UUID.randomUUID().toString(), type, Instant.now(), from, to, amount);
    }

    public Transaction(String id, Type type, Instant time,
                       String from, String to, long amount) {
        if (type == null) throw new IllegalArgumentException("Type cannot be null");
        if (time == null) throw new IllegalArgumentException("Time cannot be null");
        if (amount <= 0) throw new IllegalArgumentException("Amount must be > 0");

        this.id = Objects.requireNonNullElse(id, UUID.randomUUID().toString());
        this.type = type;
        this.time = time;
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public Instant getTime() {
        return time;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public long getAmount() {
        return amount;
    }

    // Convenience factories

    public static Transaction deposit(String user, long amount) {
        return new Transaction(Type.DEPOSIT, null, user, amount);
    }

    public static Transaction withdrawal(String user, long amount) {
        return new Transaction(Type.WITHDRAWAL, user, null, amount);
    }

    public static Transaction transfer(String from, String to, long amount) {
        return new Transaction(Type.TRANSFER, from, to, amount);
    }

    public static Transaction interest(String user, long amount) {
        return new Transaction(Type.INTEREST, null, user, amount);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", time=" + time +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", amount=" + amount +
                '}';
    }
}
