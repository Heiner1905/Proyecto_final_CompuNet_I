import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import Demo.ClientCallbackPrx;
import com.zeroc.Ice.Current;
import Demo.SubscriberPrx;


/**
 * Implementación de la interfaz Publisher de ICE.
 * Actúa como el Maestro en el modelo Cliente-Maestro-Trabajadores,
 * coordinando la búsqueda de números perfectos.
 */
public class PublisherI implements Demo.Publisher {

    // Mapa para mantener los proxies de los workers registrados, indexados por su ID asignado.
    private final HashMap<Integer, SubscriberPrx> subscribers;

    // Contador para asignar IDs únicos a los workers (Subscriber).
    private int nextId = 0;

    // Número de workers esperados para una tarea específica (para sincronización).
    private int workersEsperados = 0;

    // Pool de hilos para procesar solicitudes de clientes de forma asíncrona,
    // evitando bloquear los hilos de comunicación de ICE.
    private final ExecutorService clientRequestExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * Constructor de PublisherI. Inicializa el mapa de workers.
     */
    public PublisherI() {
        subscribers = new HashMap<>();
    }


    /**
     * Registra un nuevo worker (Subscriber) con el Maestro.
     * @param subscriber Proxy del worker que se está registrando.
     * @param current Contexto de la llamada ICE.
     * @return El ID asignado al worker.
     */
    @Override
    public synchronized int addSubscriber(SubscriberPrx subscriber, Current current) {
        int assignedId = nextId++;
        subscribers.put(assignedId, subscriber);
        // Asignación de ID a workers
        try {
            subscriber.setId(assignedId); // Llama al método setId en la implementación del worker
        } catch (com.zeroc.Ice.Exception e) {
            System.err.println(String.format("[Maestro] Error al asignar ID al worker %d: %s", assignedId, e.getMessage()));
        }

        System.out.println("Nuevo subscriber conectado. ID asignado: " + assignedId +
                " (" + subscribers.size() + " / " + workersEsperados + ")");
        notifyAll(); // Para desbloquear startJob si estaba esperando
        return assignedId;
    }

    /**
     * Desregistra un worker del Maestro.
     * @param id ID del worker a remover.
     * @param current Contexto de la llamada ICE.
     */
    @Override
    public synchronized void removeSubscriber(int id, Current current) {
        if (!subscribers.containsKey(id)) {
            throw new IllegalArgumentException("No existe subscriber con ID: " + id);
        }
        subscribers.remove(id);
        System.out.println(String.format("[Maestro] Worker %d desconectado. Total: %d workers.", id, subscribers.size()));
    }


    /**
     * Método síncrono para iniciar la búsqueda de números perfectos.
     * Utilizado principalmente por clientes de consola o para pruebas.
     * @param numWorkers Número de workers a usar.
     * @param min Límite inferior del rango de búsqueda.
     * @param max Límite superior del rango de búsqueda.
     * @param current Contexto de la llamada ICE.
     * @return Array de números perfectos encontrados.
     */
    @Override
    public int[] startJob(int numWorkers, int min, int max, Current current) {
        return executePerfectNumberSearch(numWorkers, min, max);
    }



    /**
     * Método privado que contiene la lógica central para distribuir y recolectar el trabajo de los workers.
     * Puede ser llamado tanto por `startJob` (síncrono) como por `requestPerfectNumbers` (asíncrono).
     * @param numWorkers Número de workers que el Maestro intentará usar para esta tarea.
     * @param min Límite inferior del rango de búsqueda.
     * @param max Límite superior del rango de búsqueda.
     * @return Array de números perfectos encontrados en el rango total.
     */
    public int[] executePerfectNumberSearch(int numWorkers, int min, int max) {
        // Asegura que el rango mínimo sea menor o igual al máximo.
        min = Math.min(min, max);
        max = Math.max(min, max);

        // Actualiza el número de workers esperados para esta tarea.
        this.workersEsperados = numWorkers;

        synchronized (this) {
            // Espera si no hay suficientes workers conectados para la tarea actual.
            // Se espera con un timeout para evitar bloqueos indefinidos si un worker nunca llega.
            while (subscribers.size() < numWorkers) {
                try {
                    System.out.println("Workers conectados: " + subscribers.size() + " / " + numWorkers);
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restaura el estado de interrupción.
                    System.err.println("startJob interrumpido: " + e.getMessage());
                    return new int[0]; // Retorna vacío si se interrumpe la espera.
                }
            }
        }

        // Si no hay workers disponibles después de la espera, no se puede realizar la tarea.
        if (subscribers.isEmpty()) {
            System.err.println("[Maestro] No hay workers disponibles para realizar la búsqueda.");
            return new int[0];
        }

        System.out.println(String.format("[Maestro] Iniciando distribucion del rango [%d, %d] con %d workers.", min, max, subscribers.size()));

        // Divide el rango total en subrangos para cada worker activo.
        int delta = (max - min) / subscribers.size();
        int currentMin = min;

        List<CompletableFuture<int[]>> futures = new ArrayList<>();
        // Crea una lista de los workers activos para esta distribución.
        List<SubscriberPrx> list = new ArrayList<>(subscribers.values());

        // Contador atómico para rastrear el progreso de las tareas completadas por los workers.
        AtomicInteger completedTasks = new AtomicInteger(0);

        // Asigna un subrango a cada worker y recolecta sus CompletableFuture.
        for (int i = 0; i < list.size(); i++) {
            SubscriberPrx proxy = list.get(i);
            int currentMax = (i == list.size() - 1) ? max : currentMin + delta;

            // Asegura que el último worker cubra el final del rango.
            int finalMin = currentMin;
            int finalMax = currentMax;

            System.out.println(String.format("[Maestro] Asignando rango [%d, %d] a worker #%d.", finalMin, finalMax, i + 1));
            try {
                // Realiza la llamada asíncrona al worker para calcular el subrango.
                CompletableFuture<int[]> future = proxy.calculatePerfectNumAsync(finalMin, finalMax);
                futures.add(future);

                // Añade un callback a cada future para registrar cuando la tarea se completa,
                // sin bloquear el hilo principal.
                int workerIndex = i; // Necesario para la lambda
                future.thenRun(() -> {
                    int finishedCount = completedTasks.incrementAndGet();
                    System.out.println(String.format("[Maestro] Tarea del worker #%d (rango [%d, %d]) completada. Progreso: %d/%d.",
                            workerIndex + 1, finalMin, finalMax, finishedCount, list.size()));
                }).exceptionally(ex -> {
                    // Maneja excepciones si una tarea de worker falla.
                    System.err.println(String.format("[Maestro] Error en worker #%d (rango [%d, %d]): %s",
                            workerIndex + 1, finalMin, finalMax, ex.getMessage()));
                    return null;
                });
            } catch (com.zeroc.Ice.Exception e) {
                System.err.println(String.format("[Maestro] Error al asignar tarea a worker #%d (Proxy: %s): %s", i + 1, e.getMessage()));
                // Si la asignación falla, ese worker no podrá contribuir a esta tarea.
            }
            currentMin = finalMax + 1;
        }

        // Bloqueamos y recolectamos todos los resultados
        List<Integer> allResults = new ArrayList<>();
        for (CompletableFuture<int[]> f : futures) {
            try {
                int[] result = f.join(); // Espera a que el future se complete y obtiene el resultado.
                if (result != null) { // Asegura que el resultado no sea null (si hubo una excepción handled).
                    for (int n : result) {
                        allResults.add(n);
                    }
                }
            } catch (java.lang.Exception e) {
                System.err.println("[Maestro] Error recolectando resultado de un worker (durante join()): " + e.getMessage());
            }
        }
        System.out.println(String.format("[Maestro] Recoleccion de resultados para rango [%d, %d] completada. Total perfectos: %d", min, max, allResults.size()));
        return allResults.stream().mapToInt(i -> i).toArray(); // Convierte la lista a un array int[].
    }



    /**
     * Método principal para iniciar la búsqueda de números perfectos de forma asíncrona.
     * Llamado por el cliente JavaFX.
     * @param min Límite inferior del rango de búsqueda.
     * @param max Límite superior del rango de búsqueda.
     * @param clientCallback Proxy de callback del cliente para devolver los resultados.
     * @param current Contexto de la llamada ICE.
     */
    @Override
    public void requestPerfectNumbers(int min, int max, ClientCallbackPrx clientCallback, Current current) {
        System.out.println("Maestro: Recibida solicitud asíncrona de cliente para rango [" + min + ", " + max + "]");

        // Ejecuta la lógica de búsqueda en un hilo del pool para no bloquear el hilo de ICE que recibió la solicitud.
        clientRequestExecutor.submit(() -> {
            long startTime = System.currentTimeMillis(); // Mide el tiempo de inicio de la solicitud completa.

            int numActiveWorkers = subscribers.size();
            // Si no hay workers conectados, se notifica al cliente y se aborta.
            if (numActiveWorkers == 0) {
                System.err.println(String.format("[Maestro] Error: No hay workers conectados para procesar el rango [%d, %d].", min, max));
                clientCallback.perfectNumbersFound(new int[0], 0L, (Map<String, String>) current); // current puede ser null aquí si no es una llamada directa
                return;
            }

            System.out.println(String.format("[Maestro] Iniciando procesamiento para rango [%d, %d] con %d workers disponibles.", min, max, numActiveWorkers));

            try {
                // Ejecuta la búsqueda real distribuyendo el trabajo a los workers.
                int[] perfectNums = executePerfectNumberSearch(subscribers.size(), min, max);
                long duration = System.currentTimeMillis() - startTime;

                System.out.println(String.format("[Maestro] Tarea para rango [%d, %d] completada en %d ms. Enviando resultados al cliente.", min, max, duration));

                // Envía los resultados de vuelta al cliente a través del callback.
                clientCallback.perfectNumbersFound(perfectNums, duration);
            } catch (java.lang.Exception e) {
                // Captura cualquier excepción durante el procesamiento y la reporta.
                System.err.println(String.format("[Maestro] Error procesando solicitud para rango [%d, %d]: %s", min, max, e.getMessage()));
                e.printStackTrace();
                // Notifica al cliente que hubo un error (se envía array vacío y duración 0).
                clientCallback.perfectNumbersFound(new int[0], 0L, (Map<String, String>) current); // current puede ser null aquí
            }
        });
    }

    /**
     * Apaga de forma segura el pool de hilos del Maestro.
     * Se llama al cerrar la aplicación del Maestro.
     */
    public void shutdown() {
        clientRequestExecutor.shutdown(); // Inicia el apagado del pool.
        try {
            // Espera hasta 60 segundos para que las tareas en curso terminen.
            if (!clientRequestExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                clientRequestExecutor.shutdownNow(); // Si no terminan, fuerza el apagado.
                // Espera un poco más para que el apagado forzado se complete.
                if (!clientRequestExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("[Maestro] ExecutorService no se pudo cerrar correctamente.");
                }
            }
        } catch (InterruptedException ie) {
            clientRequestExecutor.shutdownNow(); // Fuerza el cierre si se interrumpe la espera.
            Thread.currentThread().interrupt(); // Restaura el estado de interrupción.
        }
        System.out.println("[Maestro] ExecutorService apagado.");
    }



    /**
     * Retorna el número actual de workers (subscribers) registrados.
     * @param current Contexto de la llamada ICE.
     * @return Cantidad de workers conectados.
     */
    public int getSubscribersNum(Current current) {
        return subscribers.size();
    }
}