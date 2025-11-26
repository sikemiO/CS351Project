import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class Ledger implements Serializable {

    private static final long serialVersionUID = 1L;

    // Single shared list of all transactions in the system
    private final List<Transaction> transactions = new ArrayList<>();

    public Ledger() {
    }

    // new transaction to ledger
    public synchronized void append(Transaction t) {
        if (t == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        transactions.add(t);
    }
    public synchronized void saveTo(Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (Transaction t : transactions) {
                writer.write(format(t));
                writer.newLine();
            }
        }
    }

    public synchronized void loadFrom(Path path) throws IOException {
        transactions.clear();
        if (!Files.exists(path)) return;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Transaction t = parse(line);
                if (t != null) {
                    transactions.add(t);
                }
            }
        }
    }

    private String format(Transaction t) {
        long epoch = t.getTime().toEpochMilli();
        String from = t.getFrom() == null ? "" : t.getFrom();
        String to = t.getTo() == null ? "" : t.getTo();
        return t.getId() + ";" +
                t.getType().name() + ";" +
                epoch + ";" +
                from + ";" +
                to + ";" +
                t.getAmount();
    }

    private Transaction parse(String line) {
        String[] parts = line.split(";", -1);
        if (parts.length != 6) return null;
        try {
            String id = parts[0];
            Transaction.Type type = Transaction.Type.valueOf(parts[1]);
            long epoch = Long.parseLong(parts[2]);
            String from = parts[3].isEmpty() ? null : parts[3];
            String to = parts[4].isEmpty() ? null : parts[4];
            long amount = Long.parseLong(parts[5]);
            return new Transaction(id, type, Instant.ofEpochMilli(epoch), from, to, amount);
        } catch (Exception e) {
            return null;
        }
    }

    // user transactions
    public synchronized List<Transaction> findUser(String username) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : transactions) {
            if (username.equals(t.getFrom()) || username.equals(t.getTo())) {
                result.add(t);
            }
        }
        return Collections.unmodifiableList(result);
    }

    // all transactions
    public synchronized List<Transaction> all() {
        return Collections.unmodifiableList(new ArrayList<>(transactions));
    }
}
