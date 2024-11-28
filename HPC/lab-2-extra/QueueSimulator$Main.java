import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

// Customer class
class Customer {
    private int arrivalTime;
    private int serviceTime;
    private boolean served;

    public Customer(int arrivalTime, int serviceTime) {
        this.arrivalTime = arrivalTime;
        this.serviceTime = serviceTime;
        this.served = false;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public int getServiceTime() {
        return serviceTime;
    }

    public void setServiceTime(int serviceTime) {
        this.serviceTime = serviceTime;
    }

    public boolean isServed() {
        return served;
    }

    public void setServed(boolean served) {
        this.served = served;
    }
}

// Updated BankQueue class (single queue for all tellers)
class BankQueue {
    private Queue<Customer> queue;
    private int maxLength;
    private final Lock bankQueueLock = new ReentrantLock(); // Unique lock for BankQueue

    public BankQueue(int maxLength) {
        this.maxLength = maxLength;
        this.queue = new LinkedList<>(); // Single queue for all tellers
    }

    public boolean addCustomer(Customer customer) {
        bankQueueLock.lock();
        try {
            if (queue.size() >= maxLength) {
                return false; // Queue is full, customer leaves immediately
            }
            queue.add(customer);
            return true;
        } finally {
            bankQueueLock.unlock();
        }
    }

    public Customer serveCustomer() {
        bankQueueLock.lock();
        try {
            return queue.poll(); // Serve the next customer from the single queue
        } finally {
            bankQueueLock.unlock();
        }
    }

    public int getQueueSize() {
        bankQueueLock.lock();
        try {
            return queue.size();
        } finally {
            bankQueueLock.unlock();
        }
    }
}

// GroceryQueues class (multiple queues for cashiers)
class GroceryQueues {
    private List<Queue<Customer>> queues;
    private List<Integer> queueServiceTimes; // Add a list to store service times for each queue
    private int maxQueueLength;
    private final Object groceryQueueLock = new Object(); // Unique lock for GroceryQueues

    public GroceryQueues(int numQueues, int maxQueueLength) {
        this.queues = new ArrayList<>(numQueues);
        this.queueServiceTimes = new ArrayList<>(numQueues); // Initialize the service times list
        this.maxQueueLength = maxQueueLength;
        for (int i = 0; i < numQueues; i++) {
            queues.add(new LinkedList<>());
            // Initialize service times with random values or specific logic
            queueServiceTimes.add((int) (Math.random() * 41) + 20); // Service time between 20 and 60 seconds
        }
    }

    public boolean addCustomer(Customer customer) {
        synchronized (groceryQueueLock) {
            Queue<Customer> shortestQueue = findShortestQueue();
            if (shortestQueue.size() >= maxQueueLength) {
                try {
                    groceryQueueLock.wait(5000); // Wait for 5 seconds (5,000 milliseconds)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    return false;
                }
                shortestQueue = findShortestQueue(); // Check again after waiting
                if (shortestQueue.size() >= maxQueueLength) {
                    return false; // Still no space, customer leaves
                }
            }
            shortestQueue.add(customer);
            groceryQueueLock.notifyAll(); // Notify all waiting threads
            return true;
        }
    }

    public Customer serveCustomer(int queueIndex) {
        synchronized (groceryQueueLock) {
            return queues.get(queueIndex).poll();
        }
    }

    public Queue<Customer> findShortestQueue() {
        return queues.stream().min(Comparator.comparingInt(Queue::size)).orElse(queues.get(0));
    }

    public int getQueueSize(int queueIndex) {
        synchronized (groceryQueueLock) {
            return queues.get(queueIndex).size();
        }
    }

    public int getQueueIndex(Queue<Customer> queue) {
        return queues.indexOf(queue);
    }

    public int getServiceTime(int queueIndex) {
        return queueServiceTimes.get(queueIndex);
    }
}

// QueueSimulator class (manages simulation and events)
class QueueSimulator {
    private int totalCustomers;
    private int customersServed;
    private int customersLeft;
    private long totalWaitTime; // Change to long for more precision
    private long totalServiceTime; // Added to track total service time

    public QueueSimulator() {
        this.totalCustomers = 0;
        this.customersServed = 0;
        this.customersLeft = 0;
        this.totalWaitTime = 0;
        this.totalServiceTime = 0; // Initialize total service time
    }

    public void simulateBankQueue(int simulationMinutes, BankQueue bankQueue) {
        ExecutorService executor = Executors.newCachedThreadPool();
        Random random = new Random();

        Runnable producer = () -> {
            int currentTime = 0;
            while (currentTime < simulationMinutes * 60) {
                int customerArrival = random.nextInt(16) + 15; // Customer arrives between 15 and 30 seconds
                int serviceTime = random.nextInt(91) + 20;     // Service time between 20 and 110 seconds
                currentTime += customerArrival;

                if (currentTime >= simulationMinutes * 60) break;

                Customer customer = new Customer(currentTime, serviceTime);
                totalCustomers++;
                if (bankQueue.addCustomer(customer)) {
                    Customer servedCustomer = bankQueue.serveCustomer();
                    if (servedCustomer != null) {
                        customersServed++;
                        totalServiceTime += servedCustomer.getServiceTime(); // Track service time
                        totalWaitTime += (currentTime - servedCustomer.getArrivalTime()) + servedCustomer.getServiceTime();
                        currentTime += servedCustomer.getServiceTime();  // Simulate the passing of service time
                    }
                } else {
                    customersLeft++;  // Customer leaves immediately due to a full queue
                }
            }
        };

        executor.execute(producer);
        executor.shutdown();
        try {
            executor.awaitTermination(simulationMinutes * 60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        printResults("BankQueue");
    }

    public void simulateGroceryQueues(int simulationMinutes, GroceryQueues groceryQueues) {
        ExecutorService executor = Executors.newCachedThreadPool();
        Random random = new Random();

        Runnable producer = () -> {
            int currentTime = 0;
            while (currentTime < simulationMinutes * 60) {
                int customerArrival = random.nextInt(16) + 15; // Customer arrives between 15 and 30 seconds
                currentTime += customerArrival;

                if (currentTime >= simulationMinutes * 60) break;

                Customer customer = new Customer(currentTime, 0); // Service time will be set later
                totalCustomers++;
                if (groceryQueues.addCustomer(customer)) {
                    Queue<Customer> shortestQueue = groceryQueues.findShortestQueue();
                    int queueIndex = groceryQueues.getQueueIndex(shortestQueue);
                    int serviceTime = groceryQueues.getServiceTime(queueIndex); // Get the specific service time for the queue
                    customer.setServiceTime(serviceTime); // Set the service time for the customer
                    Customer servedCustomer = groceryQueues.serveCustomer(queueIndex);
                    if (servedCustomer != null) {
                        customersServed++;
                        totalServiceTime += servedCustomer.getServiceTime(); // Track service time
                        totalWaitTime += (currentTime - servedCustomer.getArrivalTime()) + servedCustomer.getServiceTime();
                        currentTime += servedCustomer.getServiceTime();  // Simulate the passing of service time
                    }
                } else {
                    customersLeft++;  // Customer leaves after waiting for 5 seconds without finding space
                }
            }
        };

        executor.execute(producer);
        executor.shutdown();
        try {
            executor.awaitTermination(simulationMinutes * 60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        printResults("GroceryQueues");
    }

    private void printResults(String queueType) {
        System.out.println(queueType + " Simulation Results:");
        System.out.println("Total Customers Arrived: " + totalCustomers);
        System.out.println("Customers Served: " + customersServed);
        System.out.println("Customers Left: " + customersLeft);
        System.out.println("Average Wait Time: " + (customersServed > 0 ? (totalWaitTime / (double) customersServed) : 0) + " seconds");
        System.out.println("Average Service Time: " + (customersServed > 0 ? (totalServiceTime / (double) customersServed) : 0) + " seconds");
    }

    public class Main {
            public static void main(String[] args) {
            int simulationMinutes = 120; // Simulation time in minutes

            // BankQueue simulation
            int maxLength = 5;
            BankQueue bankQueue = new BankQueue(maxLength);
            QueueSimulator simulator = new QueueSimulator();
            simulator.simulateBankQueue(simulationMinutes, bankQueue);

            // GroceryQueues simulation
            int numQueues = 3;
            maxLength = 2;
            GroceryQueues groceryQueues = new GroceryQueues(numQueues, maxLength);
            simulator.simulateGroceryQueues(simulationMinutes, groceryQueues);
        }
    }
    
}
