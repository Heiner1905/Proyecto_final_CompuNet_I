import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.Executors; // Para el ExecutorService
import java.util.concurrent.ExecutorService; // Para el ExecutorService
import java.util.concurrent.TimeUnit; // Para el ExecutorService

import Demo.ClientCallbackPrx;
import com.zeroc.Ice.Current;
import Demo.SubscriberPrx;

import Demo.ClientCallbackPrx; // <<< Importa el proxy del callback del cliente
//import Demo.IntSeq; // <<< Importa IntSeq para usarlo si es necesario (para métodos que devuelvan IntSeq)

public class PublisherI implements Demo.Publisher {

    private final HashMap<Integer, SubscriberPrx> subscribers;
    private int nextId = 0;
    private int workersEsperados = 0;


    // Puedes usar un ExecutorService para manejar las solicitudes de los clientes
    // para no bloquear el hilo de Ice principal.
    private final ExecutorService clientRequestExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

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

    public int[] startJobLocal(int numWorkers, int min, int max) {
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
                    return new int[0];
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

            try {
                futures.add(proxy.calculatePerfectNumAsync(finalMin, finalMax));
            } catch (Exception e) {
                System.err.println("Error con worker: " + e.getMessage());
            }

            currentMin = currentMax;
        }

        // Bloqueamos y recolectamos todos los resultados
        List<Integer> allResults = new ArrayList<>();
        for (CompletableFuture<int[]> f : futures) {
            try {
                int[] result = f.join();
                for (int n : result) {
                    allResults.add(n);
                }
            } catch (Exception e) {
                System.err.println("Error recolectando resultado: " + e.getMessage());
            }
        }

        return allResults.stream().mapToInt(i -> i).toArray();
    }

    @Override
    public int[] startJob(int numWorkers, int min, int max, Current current) {
        return startJobLocal(numWorkers, min, max);
    }


    // Método para iniciar el trabajo de forma local (llamado por startJob o requestPerfectNumbers)
    // Este método es síncrono para el Maestro, pero el Maestro lo ejecutaría en un hilo separado
    // para no bloquear la solicitud entrante.
    private int[] executePerfectNumberSearch(int numWorkers, int min, int max) {
        workersEsperados = numWorkers; // Reinicia workersEsperados para esta nueva tarea
        System.out.println("Esperando a que se conecten los " + numWorkers + " workers...");

        synchronized (this) {
            // Asegúrate de que haya suficientes workers disponibles
            while (subscribers.size() < numWorkers) {
                try {
                    System.out.println("Workers conectados: " + subscribers.size() + " / " + numWorkers + ". Esperando más...");
                    wait(); // Espera a que se conecten más workers
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Búsqueda de números perfectos interrumpida (esperando workers): " + e.getMessage());
                    return new int[0];
                }
            }
        }
        System.out.println("Iniciando búsqueda con " + subscribers.size() + " workers.");

        int delta = (max - min) / subscribers.size();
        int currentMin = min;

        List<CompletableFuture<int[]>> futures = new ArrayList<>();
        List<SubscriberPrx> list = new ArrayList<>(subscribers.values()); // Lista de workers activos

        for (int i = 0; i < list.size(); i++) {
            SubscriberPrx proxy = list.get(i);
            int subRangeMin = currentMin;
            // Para el último worker, asegúrate de que cubra el resto del rango
            int subRangeMax = (i == list.size() - 1) ? max : currentMin + delta;

            System.out.println("Asignando rango [" + subRangeMin + ", " + subRangeMax + "] a worker " + (i+1));
            try {
                // calculatePerfectNumAsync devuelve CompletableFuture<int[]>
                futures.add(proxy.calculatePerfectNumAsync(subRangeMin, subRangeMax));
            } catch (Exception e) {
                System.err.println("Error al asignar tarea al worker " + (i+1) + ": " + e.getMessage());
                // Considera cómo manejar workers fallidos, quizás reasignar su trabajo.
            }
            currentMin = subRangeMax + 1;
        }

        // Bloqueamos y recolectamos todos los resultados
        List<Integer> allResults = new ArrayList<>();
        for (CompletableFuture<int[]> f : futures) {
            try {
                int[] result = f.join(); // join() espera a que el CompletableFuture termine y devuelve el resultado
                for (int n : result) {
                    allResults.add(n);
                }
            } catch (Exception e) {
                System.err.println("Error recolectando resultado de un worker: " + e.getMessage());
            }
        }
        System.out.println("Búsqueda de números perfectos completada.");
        return allResults.stream().mapToInt(i -> i).toArray();
    }

    @Override
    public void requestPerfectNumbers(int min, int max, ClientCallbackPrx clientCallback, Current current) {
        System.out.println("Maestro: Recibida solicitud asíncrona de cliente para rango [" + min + ", " + max + "]");
        // La lógica principal de iniciar el trabajo debería ejecutarse en un hilo separado
        // para no bloquear la llamada de ICE del cliente y permitir la devolución asíncrona.
        clientRequestExecutor.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // Llama al método que realmente hace el trabajo de distribuir y recolectar.
                // numWorkers: Puedes definir un número fijo, o que el cliente lo envíe,
                // o usar todos los workers disponibles. Por ahora, un valor fijo para ejemplo.
                int[] perfectNums = executePerfectNumberSearch(subscribers.size(), min, max);
                // Si subscribers.size() es 0, deberías manejar ese caso.

                long duration = System.currentTimeMillis() - startTime;
                System.out.println("Maestro: Enviando resultados al cliente (rango: [" + min + ", " + max + "], tiempo: " + duration + "ms)");
                // Usa el callback del cliente para devolver los resultados
                clientCallback.perfectNumbersFound(perfectNums, duration);
            } catch (Exception e) {
                System.err.println("Error procesando solicitud del cliente para rango [" + min + ", " + max + "]: " + e.getMessage());
                // Manejo de errores: Si algo falla, notifica al cliente si es posible.
                // clientCallback.errorOccurred("Error al buscar números perfectos: " + e.getMessage());
            }
        });
    }




    public int getSubscribersNum(Current current) {
        return subscribers.size();
    }
}