import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.zeroc.Ice.Current;
import Demo.SubscriberPrx;
import Demo.PublisherPrx;
import com.zeroc.Ice.LocalException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class PublisherI implements Demo.Publisher {

    private HashMap<String, SubscriberPrx> subscribers;

    public PublisherI() {
        subscribers = new HashMap<>();
    }

    @Override
    public void addSuscriber(String name, SubscriberPrx o, Current current) {
        System.out.println("New Subscriber has been added ");
        subscribers.put(name, o);
    }

    @Override
    public void removeSuscriber(String name, Current current) {
        subscribers.remove(name);
        System.out.println("Remove Subscriber: " + name);
    }

    @Override
    public void reportResult(String jobId, int[] results, Current current) {

    }

    @Override
    public void startJob(int min, int max, Current current) {
        if (subscribers.isEmpty()) {
            System.out.println("No subscriber has been added");
            return;
        }

        int delta = (max - min) / subscribers.size();
        int currentMin = min;

        List<CompletableFuture<int[]>> futures = new ArrayList<>();
        List<Demo.SubscriberPrx> list = new ArrayList<>(subscribers.values());

        for (int i = 0; i < list.size(); i++) {
            Demo.SubscriberPrx proxy = list.get(i);
            int currentMax = (i == list.size() - 1) ? max : currentMin + delta;

            int finalMin = currentMin;
            int finalMax = currentMax;

            CompletableFuture<int[]> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return proxy.calculatePerfectNum(finalMin, finalMax); // llamada sincrónica, ejecutada asíncronamente
                } catch (Exception ex) {
                    System.err.println("Worker error (supplyAsync): " + ex.getMessage());
                    return new int[0];
                }
            });

            futures.add(future);
            currentMin = currentMax;
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    List<Integer> allResults = new ArrayList<>();
                    for (CompletableFuture<int[]> f : futures) {
                        try {
                            int[] result = f.join(); // bloquear hasta que esté listo
                            for (int n : result) {
                                allResults.add(n);
                            }
                        } catch (Exception e) {
                            System.err.println("Error recolectando resultado individual: " + e.getMessage());
                        }
                    }

                    int[] resultArray = allResults.stream().mapToInt(i -> i).toArray();

                    if (clientCallback != null) {
                        clientCallback.receiveResults(resultArray);
                    } else {
                        System.out.println("No clientCallback registrado");
                    }
                })
                .exceptionally(ex -> {
                    System.err.println("Error al recolectar resultados: " + ex.getMessage());
                    return null;
                });

    }


    public int getSubscribersNum() {
        return subscribers.size();
    }

    public void notifySuscriber(String name, int min, int max) {
        SubscriberPrx subscriber = subscribers.get(name);
        if (subscriber != null) {
            subscriber.calculatePerfectNum(min, max);
        }
    }
}


