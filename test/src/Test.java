// Lots of imports :(
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// ACCOUNT TESTS

class AccountTest {

    @Test
    void constructorValidInitialisesFields() {
        Account acc = new Account("alice", "secret", 500L);
        assertEquals("alice", acc.getUsername());
        assertEquals(500L, acc.getBalance());
        assertTrue(acc.checkPassword("secret"));
    }

    @Test
    void constructorRejectsNullOrBlankUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account(null, "pwd", 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Account("", "pwd", 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Account("   ", "pwd", 0L));
    }

    @Test
    void constructorRejectsNullOrBlankPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account("bob", null, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Account("bob", "", 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Account("bob", "   ", 0L));
    }

    @Test
    void constructorRejectsNegativeInitialBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account("bob", "pwd", -1L));
    }

    @Test
    void checkPasswordMatchesAndHandlesNull() {
        Account acc = new Account("alice", "secret", 0L);
        assertTrue(acc.checkPassword("secret"));
        assertFalse(acc.checkPassword("wrong"));
        assertFalse(acc.checkPassword(null));
    }

    @Test
    void setPasswordChangesPasswordAndValidates() {
        Account acc = new Account("alice", "old", 0L);
        acc.setPassword("newpass");
        assertTrue(acc.checkPassword("newpass"));
        assertFalse(acc.checkPassword("old"));
        assertThrows(IllegalArgumentException.class,
                () -> acc.setPassword(null));
        assertThrows(IllegalArgumentException.class,
                () -> acc.setPassword(""));
        assertThrows(IllegalArgumentException.class,
                () -> acc.setPassword("   "));
    }

    @Test
    void depositIncreasesBalanceAndRejectsNonPositive() {
        Account acc = new Account("alice", "pwd", 100L);
        acc.deposit(50L);
        assertEquals(150L, acc.getBalance());

        assertThrows(IllegalArgumentException.class, () -> acc.deposit(0L));
        assertThrows(IllegalArgumentException.class, () -> acc.deposit(-10L));
    }

    @Test
    void withdrawReducesBalanceAndReturnsTrueIfEnough() {
        Account acc = new Account("alice", "pwd", 100L);

        boolean ok = acc.withdraw(40L);
        assertTrue(ok);
        assertEquals(60L, acc.getBalance());
    }

    @Test
    void withdrawReturnsFalseIfInsufficientFunds() {
        Account acc = new Account("alice", "pwd", 50L);
        boolean ok = acc.withdraw(100L);

        assertFalse(ok);
        assertEquals(50L, acc.getBalance()); // unchanged
    }

    @Test
    void withdrawRejectsNonPositiveAmount() {
        Account acc = new Account("alice", "pwd", 100L);

        assertThrows(IllegalArgumentException.class, () -> acc.withdraw(0L));
        assertThrows(IllegalArgumentException.class, () -> acc.withdraw(-10L));
    }

}

// AccountStore Test

class AccountStoreTest {

    @Test
    void newStoreHasNoAccounts() {

        AccountStore store = new AccountStore();

        assertFalse(store.accountExists("alice"));
        assertNull(store.getAccount("alice"));
        assertTrue(store.allAccounts().isEmpty());
    }

    @Test
    void createAccountAddsAccountWithDefaultBalance() {

        AccountStore store = new AccountStore();
        Account acc = store.createAccount("alice", "pwd");

        assertNotNull(acc);
        assertTrue(store.accountExists("alice"));
        assertEquals(acc, store.getAccount("alice"));
        assertEquals(1000L, acc.getBalance());
    }

    @Test
    void createAccountWithExistingUsernameReturnsNullAndDoesNotReplace() {

        AccountStore store = new AccountStore();
        Account first = store.createAccount("alice", "pwd1");
        Account second = store.createAccount("alice", "pwd2");

        assertNotNull(first);
        assertNull(second); // this username already exists
        assertEquals("pwd1", store.getAccount("alice").getPasswordForPersistence());
    }

    @Test
    void saveToAndLoadFromRoundTrip() throws IOException {

        AccountStore store = new AccountStore();

        store.createAccount("alice", "pwd1");
        store.createAccount("bob", "pwd2");

        Path tempFile = Files.createTempFile("accountStoreTest", ".txt");

        try {
            store.saveTo(tempFile);
            AccountStore loaded = new AccountStore();
            loaded.loadFrom(tempFile);

            Collection<Account> accounts = loaded.allAccounts();

            assertEquals(2, accounts.size());
            assertNotNull(loaded.getAccount("alice"));
            assertNotNull(loaded.getAccount("bob"));
            assertEquals(1000L, loaded.getAccount("alice").getBalance());

        } finally {
            Files.deleteIfExists(tempFile);
        }

    }

}

//Transaction Test

class TransactionTest {

    @Test
    void constructorValidatesArguments() {

        Instant now = Instant.now();

        assertThrows(IllegalArgumentException.class,
                () -> new Transaction(null, "a", "b", 10L));
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction(Transaction.Type.DEPOSIT, "a", "b", 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction(Transaction.Type.DEPOSIT, "a", "b", -5L));
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction("id", Transaction.Type.TRANSFER, null, "a", "b", 10L));
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction("id", Transaction.Type.TRANSFER, now, "a", "b", 0L));

    }

    @Test
    void constructorStoresFields() {

        Instant now = Instant.now();

        Transaction tx = new Transaction("id123",

                Transaction.Type.TRANSFER, now, "alice", "bob", 50L);

        assertEquals("id123", tx.getId());
        assertEquals(Transaction.Type.TRANSFER, tx.getType());
        assertEquals(now, tx.getTime());
        assertEquals("alice", tx.getFrom());
        assertEquals("bob", tx.getTo());
        assertEquals(50L, tx.getAmount());
    }

    @Test
    void factoriesCreateExpectedTypesAndFields() {

        Transaction dep = Transaction.deposit("alice", 100L);

        assertEquals(Transaction.Type.DEPOSIT, dep.getType());
        assertNull(dep.getFrom());
        assertEquals("alice", dep.getTo());
        assertEquals(100L, dep.getAmount());

        Transaction wd = Transaction.withdrawal("alice", 50L);

        assertEquals(Transaction.Type.WITHDRAWAL, wd.getType());
        assertEquals("alice", wd.getFrom());
        assertNull(wd.getTo());
        assertEquals(50L, wd.getAmount());

        Transaction tr = Transaction.transfer("alice", "bob", 30L);

        assertEquals(Transaction.Type.TRANSFER, tr.getType());
        assertEquals("alice", tr.getFrom());
        assertEquals("bob", tr.getTo());
        assertEquals(30L, tr.getAmount());

        Transaction interest = Transaction.interest("alice", 10L);

        assertEquals(Transaction.Type.INTEREST, interest.getType());
        assertNull(interest.getFrom());
        assertEquals("alice", interest.getTo());
        assertEquals(10L, interest.getAmount());

    }
}

// Ledger

class LedgerTest {

    @Test
    void appendRejectsNull() {

        Ledger ledger = new Ledger();

        assertThrows(IllegalArgumentException.class, () -> ledger.append(null));
    }

    @Test
    void appendAddsTransactionToAll() {

        Ledger ledger = new Ledger();
        Transaction t1 = Transaction.deposit("alice", 100L);
        ledger.append(t1);

        List<Transaction> all = ledger.all();
        assertEquals(1, all.size());
        assertEquals(t1, all.get(0));
    }

    @Test
    void findUserReturnsTransactionsWhereUserIsFromOrTo() {

        Ledger ledger = new Ledger();

        Transaction t1 = Transaction.deposit("alice", 100L);
        Transaction t2 = Transaction.transfer("alice", "bob", 50L);
        Transaction t3 = Transaction.withdrawal("bob", 20L);

        ledger.append(t1);
        ledger.append(t2);
        ledger.append(t3);

        List<Transaction> aliceTx = ledger.findUser("alice");
        assertTrue(aliceTx.contains(t1));
        assertTrue(aliceTx.contains(t2));
        assertEquals(2, aliceTx.size());

        List<Transaction> bobTx = ledger.findUser("bob");
        assertTrue(bobTx.contains(t2));
        assertTrue(bobTx.contains(t3));
        assertEquals(2, bobTx.size());

    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {

        Ledger ledger = new Ledger();
        ledger.append(Transaction.deposit("alice", 100L));
        ledger.append(Transaction.transfer("alice", "bob", 50L));

        Path tempFile = Files.createTempFile("ledgerTest", ".txt");
        try {

            ledger.saveTo(tempFile);

            Ledger loaded = new Ledger();

            loaded.loadFrom(tempFile);

            List<Transaction> loadedAll = loaded.all();

            assertEquals(2, loadedAll.size());
            // my sanity check: types and amounts are preserved

            assertEquals(Transaction.Type.DEPOSIT, loadedAll.get(0).getType());
            assertEquals(100L, loadedAll.get(0).getAmount());

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}

// BankService (functional)

class BankServiceTest {
    private AccountStore store;
    private Ledger ledger;
    private BankService service;

    @BeforeEach
    void setUp() {
        store = new AccountStore();
        ledger = new Ledger();
        service = new BankService(store, ledger);

        store.createAccount("alice", "pwd");
        store.createAccount("bob", "pwd");
    }

    @Test
    void constructorRejectsNullArgs() {

        assertThrows(IllegalArgumentException.class, () -> new BankService(null, new Ledger()));
        assertThrows(IllegalArgumentException.class, () -> new BankService(new AccountStore(), null));
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {

        Account acc = service.login("alice", "pwd");

        assertNotNull(acc);
        assertEquals("alice", acc.getUsername());

    }

    @Test
    void loginFailsWithWrongOrNullCredentials() {

        assertNull(service.login("alice", "wrong"));
        assertNull(service.login("unknown", "pwd"));
        assertNull(service.login(null, "pwd"));
        assertNull(service.login("alice", null));

    }

    @Test
    void createAccountValidatesInputsAndDuplicates() {

        Account newAcc = service.createAccount("charlie", "abc");
        assertNotNull(newAcc);
        assertEquals(1000L, newAcc.getBalance());
        assertNull(service.createAccount("charlie", "another")); // duplicate
        assertNull(service.createAccount(null, "x"));
        assertNull(service.createAccount("", "x"));
        assertNull(service.createAccount("user", null));
        assertNull(service.createAccount("user", ""));

    }

    @Test
    void getBalanceThrowsForUnknownUser() {
        assertThrows(IllegalArgumentException.class, () -> service.getBalance("nope"));
    }

    @Test
    void depositUpdatesBalanceCreatesLedgerEntryAndNotifiesListener() {

        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Long> lastBalance = new AtomicReference<>();


        service.registerListener("alice", (user, newBalance, msg) -> {
            calls.incrementAndGet();
            lastBalance.set(newBalance);

            assertTrue(msg.contains("Deposit of 200"));
            //the mesage should start with timestamp in square brackets
            assertTrue(msg.startsWith("["), "Notification should contain a timestamp prefix");
        });

        long newBalance = service.deposit("alice", 200L);

        assertEquals(1200L, newBalance); // initially 1000
        assertEquals(1, calls.get());
        assertEquals(1200L, lastBalance.get());

        List<Transaction> all = ledger.all();
        assertEquals(1, all.size());
        assertEquals(Transaction.Type.DEPOSIT, all.get(0).getType());
        assertEquals(200L, all.get(0).getAmount());
    }

    @Test
    void depositValidatesAmountAndUser() {

        assertThrows(IllegalArgumentException.class, () -> service.deposit("alice", 0L));
        assertThrows(IllegalArgumentException.class, () -> service.deposit("alice", -10L));
        assertThrows(IllegalArgumentException.class, () -> service.deposit("unknown", 100L));
    }

    @Test
    void withdrawUpdatesBalanceAndLedger() {

        long newBalance = service.withdraw("alice", 300L);

        assertEquals(700L, newBalance);
        List<Transaction> all = ledger.all();
        assertEquals(1, all.size());
        assertEquals(Transaction.Type.WITHDRAWAL, all.get(0).getType());
        assertEquals(300L, all.get(0).getAmount());
    }

    @Test
    void withdrawRejectsInvalidAmountUnknownUserAndInsufficientFunds() {

        assertThrows(IllegalArgumentException.class, () -> service.withdraw("alice", 0L));
        assertThrows(IllegalArgumentException.class, () -> service.withdraw("alice", -10L));
        assertThrows(IllegalArgumentException.class, () -> service.withdraw("unknown", 100L));
        assertThrows(IllegalStateException.class, () -> service.withdraw("alice", 5000L));
    }

    @Test
    void transferMovesFundsCreatesLedgerAndNotifiesBothUsers() {

        AtomicInteger aliceCalls = new AtomicInteger();
        AtomicInteger bobCalls = new AtomicInteger();


        service.registerListener("alice", (user, newBalance, msg) -> aliceCalls.incrementAndGet());
        service.registerListener("bob", (user, newBalance, msg) -> bobCalls.incrementAndGet());

        boolean ok = service.transfer("alice", "bob", 200L);

        assertTrue(ok);
        assertEquals(800L, service.getBalance("alice"));
        assertEquals(1200L, service.getBalance("bob"));
        List<Transaction> all = ledger.all();

        assertEquals(1, all.size());
        assertEquals(Transaction.Type.TRANSFER, all.get(0).getType());
        assertEquals(200L, all.get(0).getAmount());
        assertEquals(1, aliceCalls.get());
        assertEquals(1, bobCalls.get());
    }

    @Test
    void transferValidationAndFailureCases() {

        assertFalse(service.transfer(null, "bob", 100L));
        assertFalse(service.transfer("alice", null, 100L));
        assertFalse(service.transfer("alice", "alice", 100L));
        assertFalse(service.transfer("alice", "unknown", 100L));
        assertFalse(service.transfer("unknown", "bob", 100L));


        assertThrows(IllegalArgumentException.class,
                () -> service.transfer("alice", "bob", 0L));
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer("alice", "bob", -10L));

        // insufficient funds

        boolean ok = service.transfer("alice", "bob", 10_000L);
        assertFalse(ok);
    }

    @Test
    void applyInterestAppliesToPositiveBalancesOnly() {

        // alice: 1000, bob: 1000

        store.createAccount("charlie", "pwd");
        service.withdraw("charlie", 1000L); // now charlie is 0

        int before = ledger.all().size();

        service.applyInterest(0.10); // 10%

        assertEquals(1100L, service.getBalance("alice"));
        assertEquals(1100L, service.getBalance("bob"));
        assertEquals(0L, service.getBalance("charlie"));

        int after = ledger.all().size();
        // interest transactions for alice and bob only :)
        assertEquals(before + 2, after);

    }

    @Test
    void applyInterestRejectsNonPositiveRate() {

        assertThrows(IllegalArgumentException.class, () -> service.applyInterest(0.0));
        assertThrows(IllegalArgumentException.class, () -> service.applyInterest(-0.1));

    }

    @Test
    void unregisterListenerStopsNotifications() {

        AtomicInteger calls = new AtomicInteger();

        BankService.BalanceListener listener =

                (u, b, m) -> calls.incrementAndGet();

        service.registerListener("alice", listener);
        service.deposit("alice", 50L);

        assertEquals(1, calls.get());


        service.unregisterListener("alice", listener);
        service.deposit("alice", 50L);
        assertEquals(1, calls.get()); // no extra call
    }
}

// BankService (concurrency)
class BankServiceConcurrencyTest {

    /**
     * Stress-test concurrent transfers in both directions between two users.
     * <p>
     * Demonstrates no deadlock and total balance preserved.
     */
    @Test
    void concurrentTransfersDoNotDeadlockAndPreserveTotalBalance() throws Exception {

        AccountStore store = new AccountStore();
        Ledger ledger = new Ledger();
        BankService service = new BankService(store, ledger);

        store.createAccount("alice", "pwd"); // 1000
        store.createAccount("bob", "pwd");   // 1000


        final int threads = 10;
        final int iterationsPerThread = 100;
        final long transferAmount = 1;

        Thread[] workers = new Thread[threads];
        CyclicBarrier barrier = new CyclicBarrier(threads);


        for (int i = 0; i < threads; i++) {
            final boolean aliceToBob = (i % 2 == 0); // half go A->B, half B->A
            workers[i] = new Thread(() -> {
                try {
                    // ensure all threads start at roughly the same time
                    barrier.await();
                    for (int j = 0; j < iterationsPerThread; j++) {

                        if (aliceToBob) {
                            service.transfer("alice", "bob", transferAmount);
                        } else {
                            service.transfer("bob", "alice", transferAmount);
                        }

                    }
                } catch (Exception ignored) {
                }
            });
            workers[i].start();
        }

        // wait for all threads to finish :)

        for (Thread t : workers) {
            t.join(5000);
        }

        // If the code got here, no deadlock should have occurred.
        long total = service.getBalance("alice") + service.getBalance("bob");
        assertEquals(2000L, total, "Total balance must be preserved across transfers");
    }

    //concurrent deposits on the same account should not lose ANY updates.

    @Test
    void concurrentDepositsAreThreadSafe() throws Exception {

        AccountStore store = new AccountStore();
        Ledger ledger = new Ledger();
        BankService service = new BankService(store, ledger);

        store.createAccount("alice", "pwd"); // 1000 initial

        final int threads = 20;
        final int depositsPerThread = 50;
        final long depositAmount = 10;

        Thread[] workers = new Thread[threads];
        CyclicBarrier barrier = new CyclicBarrier(threads);

        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try {
                    barrier.await();
                    for (int j = 0; j < depositsPerThread; j++) {
                        service.deposit("alice", depositAmount);
                    }

                } catch (Exception ignored) {
                }
            });
            workers[i].start();
        }

        for (Thread t : workers) {
            t.join(5000);
        }

        long expected = 1000L + (long) threads * depositsPerThread * depositAmount;

        assertEquals(expected, service.getBalance("alice"));
    }
}

//  ThreadPool

class ThreadPoolTest {

    @Test
    void constructorRejectsNonPositiveThreadCount() {

        assertThrows(IllegalArgumentException.class, () -> new ThreadPool(0));
        assertThrows(IllegalArgumentException.class, () -> new ThreadPool(-1));
    }

    @Test
    void executeRejectsNullTask() {

        ThreadPool pool = new ThreadPool(2);

        assertThrows(IllegalArgumentException.class, () -> pool.execute(null));

        pool.shutdown();
    }

    @Test
    void executeRejectsWhenShutdown() {

        ThreadPool pool = new ThreadPool(1);

        pool.shutdown();

        assertThrows(IllegalStateException.class,
                () -> pool.execute(() -> {
                }));
    }

    @Test
    void tasksAreExecutedByWorkerThreads() throws Exception {

        ThreadPool pool = new ThreadPool(2);
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger counter = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            pool.execute(() -> {
                counter.incrementAndGet();
                latch.countDown();

            });
        }

        boolean finished = latch.await(2, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(finished, "Tasks did not finish in time");
        assertEquals(3, counter.get());

    }

    // another stress test

    @Test
    void manyTasksAreAllExecuted() throws Exception {
        ThreadPool pool = new ThreadPool(4);

        int taskCount = 100;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger counter = new AtomicInteger();

        for (int i = 0; i < taskCount; i++) {
            pool.execute(() -> {

                counter.incrementAndGet();
                latch.countDown();
            });
        }

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(finished, "Not all tasks finished in time");
        assertEquals(taskCount, counter.get());
    }
}

//  InterestThread

class InterestThreadTest {
    @Test
    void constructorValidatesArguments() {
        BankService service = new BankService(new AccountStore(), new Ledger());

        assertThrows(IllegalArgumentException.class,
                () -> new InterestThread(null, 0.01, 1000));
        assertThrows(IllegalArgumentException.class,
                () -> new InterestThread(service, 0.0, 1000));
        assertThrows(IllegalArgumentException.class,
                () -> new InterestThread(service, -0.1, 1000));
        assertThrows(IllegalArgumentException.class,
                () -> new InterestThread(service, 0.01, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InterestThread(service, 0.01, -100));

    }

    @Test
    void settersValidateAndChangeValues() {
        BankService service = new BankService(new AccountStore(), new Ledger());
        InterestThread it = new InterestThread(service, 0.01, 1000);

        assertThrows(IllegalArgumentException.class, () -> it.setRate(0.0));
        assertThrows(IllegalArgumentException.class, () -> it.setRate(-0.1));
        assertThrows(IllegalArgumentException.class, () -> it.setPeriod(0L));
        assertThrows(IllegalArgumentException.class, () -> it.setPeriod(-1L));

        // valid calls so should not throw
        it.setRate(0.02);
        it.setPeriod(500L);

    }

    @Test
    void runAppliesInterestPeriodicallyUntilStopped() throws Exception {
        AccountStore store = new AccountStore();
        Ledger ledger = new Ledger();
        BankService service = new BankService(store, ledger);

        store.createAccount("alice", "pwd"); // balance 1000

        InterestThread it = new InterestThread(service, 0.10, 50L); // 10% every 50ms
        Thread t = new Thread(it);
        t.start();

        // let it run for a bit
        TimeUnit.MILLISECONDS.sleep(200);
        it.stopRunning();
        t.join(1000);

        // should have at least one interest transaction
        boolean hasInterest = ledger.all().stream()
                .anyMatch(tx -> tx.getType() == Transaction.Type.INTEREST);
        assertTrue(hasInterest);

    }

    //stress testing rate/period while the thread is running to look for any possible race issues
    // aka make sure it doesnt crash and still applies interest

    @Test
    void interestThreadHandlesConcurrentConfigChanges() throws Exception {

        AccountStore store = new AccountStore();
        Ledger ledger = new Ledger();
        BankService service = new BankService(store, ledger);

        store.createAccount("alice", "pwd");

        InterestThread it = new InterestThread(service, 0.05, 100);
        Thread t = new Thread(it);
        t.start();

        // change our thread config a few times while it's running

        for (int i = 0; i < 5; i++) {
            it.setRate(0.01 + i * 0.01);
            it.setPeriod(50L + i * 10L);
            TimeUnit.MILLISECONDS.sleep(60);

        }
        it.stopRunning();
        t.join(2000);

        // interest should have been applied so check
        long balance = service.getBalance("alice");
        assertTrue(balance > 1000L);
    }

}
