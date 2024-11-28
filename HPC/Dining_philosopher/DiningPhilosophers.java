import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class DiningPhilosophers {

    private static final int NUM_TABLES = 5;
    private static final int PHILOSOPHERS_PER_TABLE = 5;
    private static final int FORKS_PER_TABLE = 5;

    private static Lock[][] forks = new ReentrantLock[NUM_TABLES + 1][FORKS_PER_TABLE]; // 6th table for deadlock moves
    private static Lock sixthTableLock = new ReentrantLock(); // Lock to manage movement to the 6th table
    private static int philosophersAtSixthTable = 0; // Count of philosophers at the 6th table
    private static volatile boolean deadlockReached = false; // Flag to indicate when deadlock is reached
    private static int lastPhilosopherToMove = -1; // To track the last philosopher who moved to the sixth table
    private static boolean[] leftForkHeld = new boolean[NUM_TABLES * PHILOSOPHERS_PER_TABLE]; // Track left fork ownership

    private static final Random random = new Random();
    private static final int TIMEOUT = 2000; // Timeout for trying to pick up forks

    public static void main(String[] args) {
        // Initialize locks for each fork on all tables
        for (int i = 0; i < NUM_TABLES + 1; i++) {
            for (int j = 0; j < FORKS_PER_TABLE; j++) {
                forks[i][j] = new ReentrantLock();
            }
        }

        // Create and start philosopher threads for each table
        Thread[] philosopherThreads = new Thread[NUM_TABLES * PHILOSOPHERS_PER_TABLE];
        for (int table = 0; table < NUM_TABLES; table++) {
            for (int i = 0; i < PHILOSOPHERS_PER_TABLE; i++) {
                int philosopherId = table * PHILOSOPHERS_PER_TABLE + i;
                philosopherThreads[philosopherId] = new Thread(new Philosopher(philosopherId, table));
                philosopherThreads[philosopherId].start();
            }
        }

        // Wait for all threads to finish
        for (Thread philosopherThread : philosopherThreads) {
            try {
                philosopherThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    static class Philosopher implements Runnable {

        private int id;
        private int table;

        public Philosopher(int id, int table) {
            this.id = id;
            this.table = table;
        }

        @Override
        public void run() {
            try {
                while (!deadlockReached) {  // Stop if deadlock is reached
                    think();
                    if (deadlockReached) break;
                    if (!pickUpForks()) { // Attempt to pick up forks and check for deadlock
                        return; // If moving to sixth table or if deadlock occurs, exit
                    }
                    eat();
                    if (deadlockReached) break;
                    putDownForks();
                    if (deadlockReached) break;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void think() throws InterruptedException {
            if (deadlockReached) return;
            int thinkingTime = random.nextInt(10); // Random thinking time between 0 and 10 seconds
            System.out.println("Philosopher " + (char) ('A' + id) + " is thinking for " + thinkingTime + " seconds.");
            TimeUnit.SECONDS.sleep(thinkingTime);
        }

        private boolean pickUpForks() throws InterruptedException {
            if (deadlockReached) return false;

            Lock leftFork = forks[table][id % FORKS_PER_TABLE];
            Lock rightFork = forks[table][(id + 1) % FORKS_PER_TABLE];

            // Try to pick up left fork
            leftFork.lock();
            System.out.println("Philosopher " + (char) ('A' + id) + " picked up left fork.");
            leftForkHeld[id] = true; // Mark that this philosopher is holding the left fork

            // Simulate delay before trying to pick up right fork
            TimeUnit.SECONDS.sleep(4);

            // Indefinite loop to try to pick up right fork
            while (true) {
                // Try to pick up right fork
                if (rightFork.tryLock()) {
                    System.out.println("Philosopher " + (char) ('A' + id) + " picked up right fork.");
                    return true; // Successfully picked up both forks
                } else {
                    System.out.println("Philosopher " + (char) ('A' + id) + " couldn't pick up right fork. Checking for deadlock...");

                    // Check for deadlock condition
                    if (isDeadlock()) {
                        System.out.println("Philosopher " + (char) ('A' + id) + " is leaving to the sixth table.");
                        moveToSixthTable();
                        leftForkHeld[id] = false; // Release left fork before leaving
                        leftFork.unlock(); // Put down left fork
                        return false; // Exit pickUpForks to prevent further actions
                    }

                    // Give other philosophers a chance to act
                    TimeUnit.MILLISECONDS.sleep(TIMEOUT);
                }
            }
        }

        private boolean isDeadlock() {
            // Check if all philosophers at this table are holding their left forks
            for (int i = 0; i < PHILOSOPHERS_PER_TABLE; i++) {
                if (!leftForkHeld[table * PHILOSOPHERS_PER_TABLE + i]) {
                    return false; // If at least one philosopher is not holding the left fork, it's not deadlock
                }
            }
            return true; // All are holding their left forks, so deadlock
        }

        private void eat() throws InterruptedException {
            if (deadlockReached) return;
            int eatingTime = random.nextInt(5); // Random eating time between 0 and 5 seconds
            System.out.println("Philosopher " + (char) ('A' + id) + " is eating for " + eatingTime + " seconds.");
            TimeUnit.SECONDS.sleep(eatingTime);
        }

        private void putDownForks() {
            Lock leftFork = forks[table][id % FORKS_PER_TABLE];
            Lock rightFork = forks[table][(id + 1) % FORKS_PER_TABLE];

            // Put down right fork
            rightFork.unlock();
            System.out.println("Philosopher " + (char) ('A' + id) + " put down right fork.");

            // Put down left fork
            leftFork.unlock();
            System.out.println("Philosopher " + (char) ('A' + id) + " put down left fork.");
            leftForkHeld[id] = false; // Mark that this philosopher has put down the left fork
        }

        private void moveToSixthTable() throws InterruptedException {
            sixthTableLock.lock();
            try {
                if (philosophersAtSixthTable < PHILOSOPHERS_PER_TABLE && !deadlockReached) {
                    philosophersAtSixthTable++;
                    System.out.println("Philosopher " + (char) ('A' + id) + " moved to the sixth table.");
                    lastPhilosopherToMove = id; // Track the last philosopher to move
                    table = 5;  // Update the philosopher's table to the sixth table

                    // Attempt to eat at the sixth table
                    while (!deadlockReached) {
                        if (!pickUpForks()) {
                            return; // Exit if moving to sixth table or if deadlock occurs
                        }
                        eat();
                        putDownForks();
                    }

                    // Check if the sixth table has reached deadlock
                    if (philosophersAtSixthTable == PHILOSOPHERS_PER_TABLE) {
                        System.out.println("Sixth table has entered deadlock. System is deadlocked.");
                        System.out.println("Last philosopher to enter the sixth table: " + (char) ('A' + lastPhilosopherToMove));
                        deadlockReached = true;  // Set the flag when deadlock is reached
                    }
                }
            } finally {
                sixthTableLock.unlock();
            }
        }
    }
}
