import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

public class Bank {
    String accountNumber;
    private Lock lock = new ReentrantLock();
    OperationsQueue operationsQueue;
    int balance = 0;

    public Bank(String accountNumber, OperationsQueue operationsQueue) {
        this.accountNumber = accountNumber;
        this.operationsQueue = operationsQueue;
    }

    public void deposit() {
        while (true) {
            lock.lock();
            try {
                Operation operation = operationsQueue.getNextItem();
                if (operation.amount == -9999) {
                    operationsQueue.add(operation);
                    break;
                }
                if (operation.amount > 0) {
                    balance = balance + operation.amount;
                    System.out.println("Deposited: " + operation.amount + " Balance: " + balance);
                } else {
                    if (!operationsQueue.contains(operation.amount)) {
                        operationsQueue.add(operation);
                        System.out.println("Operation added back: " + operation);
                    }
                }
                System.out.println("Queue state after deposit iteration: " + operationsQueue.getQueueState());
                try {
                    Thread.sleep((int) (Math.random() * 80));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public void withdraw() {
        while (true) {
            lock.lock();
            try {
                Operation operation = operationsQueue.getNextItem();
                if (operation.amount == -9999) {
                    operationsQueue.add(operation);
                    break;
                }
                if (balance + operation.amount < 0) {
                    System.out.println("Not enough balance to withdraw: " + operation.amount);
                    continue;
                }
                if (operation.amount < 0) {
                    balance = balance + operation.amount;
                    System.out.println("Withdrawn: " + operation.amount + " Balance: " + balance);
                } else {
                    if (!operationsQueue.contains(operation.amount)) {
                        operationsQueue.add(operation);
                        System.out.println("Operation added back: " + operation);
                    }
                }
                System.out.println("Queue state after withdraw iteration: " + operationsQueue.getQueueState());
                try {
                    Thread.sleep((int) (Math.random() * 80));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
