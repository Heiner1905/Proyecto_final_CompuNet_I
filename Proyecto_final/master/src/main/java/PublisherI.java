import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.zeroc.Ice.Current;
import Demo.SubscriberPrx;

public class PublisherI implements Demo.Publisher {

    private final HashMap<Integer, SubscriberPrx> subscribers;
    private int nextId = 0;
    private int workersEsperados = 0;

    public PublisherI() {
        subscribers = new HashMap<>();
    }

    @Override
    public synchronized int addSubscriber(SubscriberPrx subscriber, Current current) {
        int assignedId = nextId++;
        subscribers.put(assignedId, subscriber);
        System.out.println("Nuevo subscriber conectado. ID asignado: " + assignedId +
                " (" + subscribers.size() + " / " + workersEsperados + ")");
        notifyAll(); // Para desbloquear startJob si estaba esperando
        return assignedId;
    }

    @Override
    public synchronized void removeSubscriber(int id, Current current) {
        if (!subscribers.containsKey(id)) {
            throw new IllegalArgumentException("No existe subscriber con ID: " + id);
        }
        subscribers.remove(id);
        System.out.println("Se ha eliminado el subscriber: " + id);
    }


    public CompletableFuture<int[]> startJob(int numWorkers, int min, int max) {
        workersEsperados = numWorkers;
        System.out.println("Esperando a que se conecten los " + numWorkers + " workers...");

        synchronized (this) {
            while (subscribers.size() < numWorkers) {
                try {
                    wait();
                    System.out.println("Workers conectados: " + subscribers.size() + " / " + numWorkers);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("startJob interrumpido: " + e.getMessage());
                    return CompletableFuture.failedFuture(e);
                }
            }
        }

        int delta = (max - min) / subscribers.size();
        int currentMin = min;

        List<CompletableFuture<int[]>> futures = new ArrayList<>();
        List<SubscriberPrx> list = new ArrayList<>(subscribers.values());

        for (int i = 0; i < list.size(); i++) {
            SubscriberPrx proxy = list.get(i);
            int currentMax = (i == list.size() - 1) ? max : currentMin + delta;

            int finalMin = currentMin;
            int finalMax = currentMax;

            CompletableFuture<int[]> future;
            try {
                future = proxy.calculatePerfectNumAsync(finalMin, finalMax);
            } catch (Exception e) {
                System.err.println("Error con worker al llamar calculatePerfectNumAsync: " + e.getMessage());
                future = CompletableFuture.failedFuture(e);
            }

            futures.add(future);
            currentMin = currentMax;
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<Integer> allResults = new ArrayList<>();
                    for (CompletableFuture<int[]> f : futures) {
                        try {
                            int[] result = f.join();
                            for (int n : result) {
                                allResults.add(n);
                            }
                        } catch (Exception e) {
                            System.err.println("Error recolectando resultado individual: " + e.getMessage());
                        }
                    }
                    return allResults.stream().mapToInt(i -> i).toArray();
                });
    }





    public int getSubscribersNum(Current current) {
        return subscribers.size();
    }
}