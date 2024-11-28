import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

class Operation {
    int id;
    int amount;

    Operation(int id, int amount) {
        this.id = id;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "ID=" + id + ", Amount=" + amount;
    }
}

public class OperationsQueue {
    private final List<Operation> operations = new ArrayList<>();
    private final Set<Integer> operationSet = new HashSet<>();
    private int nextId = 0;

    public void addSimulation(int totalSimulation) {
        for (int i = 0; i < totalSimulation; i++) {
            int random= (int) (Math.random() * 200) - 100;
            if(random != 0){
                Operation operation = new Operation(nextId++, random);
                operations.add(operation);
                operationSet.add(operation.amount);
                System.out.println(i + ". New operation added: " + operation);
            }
            

            

            try {
                Thread.sleep((int) (Math.random() * 80));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Operation endOperation = new Operation(nextId++, -9999);
        operations.add(endOperation);
        operationSet.add(endOperation.amount);
    }

    public void add(Operation operation) {
        if (!operationSet.contains(operation.amount)) {
            operations.add(operation);
            operationSet.add(operation.amount);
        }
    }

    public synchronized Operation getNextItem() {
        while (operations.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Operation operation = operations.remove(0);
        operationSet.remove(operation.amount);
        return operation;
    }

    public boolean contains(int amount) {
        return operationSet.contains(amount);
    }

    public synchronized String getQueueState() {
        return operations.toString();
    }
}
