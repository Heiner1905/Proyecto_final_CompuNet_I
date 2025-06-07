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
        subscribers.remove(id);
        System.out.println("Se ha eliminado el subscriber: " + id);
    }

    @Override
    public void reportResult(int jobId, int[] results, Current current) {

    }

    @Override
    public void startJob(int numWorkers, int min, int max, Current current) {
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
                    return;
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
                future = new CompletableFuture<>();
            }

            futures.add(future);
            currentMin = currentMax;
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
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

                    int[] resultArray = allResults.stream().mapToInt(i -> i).toArray();
                    receiveResults(resultArray, current);
                })
                .exceptionally(ex -> {
                    System.err.println("Error al recolectar resultados: " + ex.getMessage());
                    return null;
                });
    }

    @Override
    public void receiveResults(int[] results, Current current) {
        if (results.length == 0) {
            System.out.println("No se encontraron números perfectos.");
        } else {
            System.out.println("Números perfectos encontrados: " + Arrays.toString(results));
        }
    }

    public void notifySubscriber(int id, String msg) {
        SubscriberPrx subscriber = subscribers.get(id);
        if (subscriber != null) {
            System.out.println("pollo");
        } else {
            System.out.println("Subscriber con ID " + id + " no existe.");
        }
    }

    public int getSubscribersNum(Current current) {
        return subscribers.size();
    }
}