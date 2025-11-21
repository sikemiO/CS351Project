import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadPool {

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final List<Thread> workerThreads = new ArrayList<>();
    private volatile boolean running = true;

    public ThreadPool(int numThreads) {
        if (numThreads <=0) {
            throw new IllegalArgumentException("number of threads must be > 0");
        }
        for (int i = 0; i < numThreads; i++) {
            Thread t = new Thread(new Worker());
            t.start();
            workerThreads.add(t);
        }
    }

    public void execute(Runnable task) {
        if (!running) {
            throw new IllegalStateException("ThreadPool is not running");
        } else if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        } else {
            queue.offer(task);
        }
    }

    public void shutdown() {
        running = false;
        for (Thread t : workerThreads) {
            t.interrupt();
        }
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    Runnable task = queue.take();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
