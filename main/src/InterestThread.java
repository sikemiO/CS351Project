import java.time.Instant;

public class InterestThread implements Runnable {

    private final BankService bankService;

    // Volatile so updates from admin menu are visible to the running thread
    private volatile double rate;
    private volatile long periodMillis;
    private volatile boolean running = true;

    public InterestThread(BankService bankService, double initialRate, long initialPeriodMillis) {
        if (bankService == null) {
            throw new IllegalArgumentException("BankService must not be null");
        }
        if (initialRate <= 0) {
            throw new IllegalArgumentException("Interest rate must be > 0");
        }
        if (initialPeriodMillis <= 0) {
            throw new IllegalArgumentException("Interest period must be > 0");
        }

        this.bankService = bankService;
        this.rate = initialRate;
        this.periodMillis = initialPeriodMillis;
    }

    // change interest
    public void setRate(double newRate) {
        if (newRate <= 0) {
            throw new IllegalArgumentException("Interest rate must be > 0");
        }
        this.rate = newRate;
        System.out.println("[INTEREST] Rate changed to " + newRate);
    }


    //Change how often interest is applied
    public void setPeriod(long newPeriodMillis) {
        if (newPeriodMillis <= 0) {
            throw new IllegalArgumentException("Interest period must be > 0");
        }
        this.periodMillis = newPeriodMillis;
        System.out.println("[INTEREST] Period changed to " + newPeriodMillis + " ms");
    }


    //Request the thread to stop after the current sleep/iteration.
    public void stopRunning() {
        this.running = false;
    }

    @Override
    public void run() {
        System.out.println("[INTEREST] Background interest thread started.");

        while (running) {
            try {
                long sleepTime = periodMillis;
                Thread.sleep(sleepTime);

                if (!running) {
                    break;
                }

                double currentRate = rate;
                System.out.println("[INTEREST] Applying interest at rate "
                        + currentRate + " at " + Instant.now());

                bankService.applyInterest(currentRate);

            } catch (InterruptedException e) {
                // If interrupted, exit
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException ex) {
                // Don't kill the thread on a single failure, just log and continue
                System.err.println("[INTEREST] Error applying interest: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        System.out.println("[INTEREST] Background interest thread stopping.");
    }
}
