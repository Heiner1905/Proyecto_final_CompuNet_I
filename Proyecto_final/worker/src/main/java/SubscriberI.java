import com.zeroc.Ice.Current;
import java.util.List;
import java.util.concurrent.*;
import java.util.ArrayList;

/**
 * Implementación de la interfaz Subscriber de ICE.
 * Actúa como un Trabajador en el modelo Cliente-Maestro-Trabajadores,
 * realizando el cálculo de números perfectos en rangos asignados.
 */

public class SubscriberI implements Demo.Subscriber {

    // ID asignado a este worker por el Maestro. Se inicializa en -1 y se actualiza al ser registrado.
    private int id = -1;

    // Número de hilos que cada worker usará para la paralelización interna del cálculo.
    // Se usa Runtime.getRuntime().availableProcessors() para adaptarse a los núcleos de la CPU.
    private static final int NUM_THREADS_PER_WORKER = Runtime.getRuntime().availableProcessors();

    // Umbral mínimo de tamaño de rango para decidir si se aplica paralelización interna.
    // Rangos más pequeños se procesan secuencialmente para evitar el overhead de hilos.
    private static final int PARALLELIZATION_THRESHOLD = 10000;

    // Pool de hilos para ejecutar las tareas de cálculo internas en paralelo.
    private final ExecutorService executor;

    /**
     * Constructor de SubscriberI. Inicializa el pool de hilos.
     */
    public SubscriberI(){
        this.executor = Executors.newFixedThreadPool(NUM_THREADS_PER_WORKER);
        // El ID no está asignado todavía, así que se usa id=-1 por ahora en el log.
        System.out.println("[Worker " + id + "] SubscriberI inicializado con " + NUM_THREADS_PER_WORKER + " hilos para cálculo interno.");
    }


    /**
     * Asigna un ID a este worker. Este método es llamado por el Maestro al registrar el worker.
     * @param id El ID único asignado por el Maestro.
     */
    @Override
    public void setId(int id, Current current) {
        this.id = id;
        System.out.println("[Worker " + id + "] ID asignado por el Maestro: " + id);
    }

    /**
     * Calcula los números perfectos dentro de un rango dado.
     * Este método es llamado de forma asíncrona por el Maestro.
     * @param minNum Límite inferior del rango.
     * @param maxNum Límite superior del rango.
     * @param current Contexto de la llamada ICE.
     * @return Un array de enteros con los números perfectos encontrados.
     */
    @Override
    public int[] calculatePerfectNum(int minNum, int maxNum, Current current) {
        long startTimeWorker = System.currentTimeMillis(); // Inicia la medición de tiempo del worker

        // Asegura que min sea siempre menor o igual a max para el cálculo.
        int min = Math.min(minNum, maxNum);
        int max = Math.max(minNum, maxNum);

        // Llama al método 'calculate' que gestiona la paralelización interna.
        ArrayList<Integer> perfectNums = calculate(min, max);

        long endTimeWorker = System.currentTimeMillis(); // Finaliza la medición de tiempo del worker
        long durationWorker = endTimeWorker - startTimeWorker;

        // Log del progreso y resultados en la consola del worker.
        System.out.println(String.format(
                "[Worker %d] Rango [%d, %d] procesado en %d ms. Encontrados %d números perfectos.",
                id, min, max, durationWorker, perfectNums.size()
        ));
        if (!perfectNums.isEmpty()) {
            System.out.println("[Worker " + id + "] Números perfectos en este rango: " + perfectNums);
        }

        // Convierte la lista de resultados a un array de int para devolver.
        int[] result = new int[perfectNums.size()];
        for (int i = 0; i < perfectNums.size(); i++) {
            result[i] = perfectNums.get(i);
        }
        return result;
    }


    /**
     * Gestiona la paralelización interna del cálculo de números perfectos para un subrango.
     * Divide el rango en subtareas para ser procesadas por el pool de hilos.
     * @param minNum Límite inferior del subrango.
     * @param maxNum Límite superior del subrango.
     * @return Una lista de números perfectos encontrados en el subrango.
     */
    public ArrayList<Integer> calculate(int minNum, int maxNum) {
        ArrayList<Integer> perfectNums = new ArrayList<>();

        int start = Math.min(minNum, maxNum);
        int end = Math.max(minNum, maxNum);
        int rangeSize = end - start + 1;

        // Decide si paralelizar o calcular secuencialmente.
        if (rangeSize < PARALLELIZATION_THRESHOLD || NUM_THREADS_PER_WORKER <= 1) {
            // Rango pequeño o un solo hilo: ejecuta secuencialmente.
            return sequentialCalculate(start, end);
        }

        System.out.println(String.format("[Worker %d] Dividiendo rango [%d, %d] en %d hilos internos.", id, start, end, NUM_THREADS_PER_WORKER));

        List<Callable<ArrayList<Integer>>> tasks = new ArrayList<>();
        int chunkSize = rangeSize / NUM_THREADS_PER_WORKER;
        int currentStart = start;

        // Crea tareas (Callable) para cada trozo del rango.
        for (int i = 0; i < NUM_THREADS_PER_WORKER; i++) {
            int subRangeStart = currentStart;
            int subRangeEnd = (i == NUM_THREADS_PER_WORKER - 1) ? end : currentStart + chunkSize - 1;

            tasks.add(() -> sequentialCalculate(subRangeStart, subRangeEnd));

            currentStart = subRangeEnd + 1;
        }

        try {
            // Ejecuta todas las tareas en el pool de hilos y espera a que todas terminen.
            List<Future<ArrayList<Integer>>> results = executor.invokeAll(tasks);
            for (Future<ArrayList<Integer>> result : results) {
                perfectNums.addAll(result.get()); // Consolida los resultados de cada hilo.
            }
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("[Worker " + id + "] Error durante la paralelización interna: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        return perfectNums;
    }

    /**
     * Algoritmo secuencial para encontrar números perfectos en un subrango.
     * Este método es la unidad de trabajo básica para los hilos internos.
     * @param minNum Límite inferior del subrango.
     * @param maxNum Límite superior del subrango.
     * @return Una lista de números perfectos encontrados.
     */
    private ArrayList<Integer> sequentialCalculate(int minNum, int maxNum) {
        ArrayList<Integer> perfectNums = new ArrayList<>();
        int accSum;
        for (int i = minNum; i <= maxNum; i++) {
            accSum = 0;
            for (int j = 1; j <= i / 2; j++) {
                if (i % j == 0) {
                    accSum += j;
                }
            }
            if (accSum == i) {
                perfectNums.add(i);
            }
        }
        return perfectNums;
    }

    /**
     * Recibe mensajes generales del Maestro (ej. actualizaciones de estado).
     * @param msg El mensaje recibido.
     * @param current Contexto de la llamada ICE.
     */
    @Override
    public void onUpdate(String msg, Current current) {
        System.out.println("[Worker " + id + "] Mensaje recibido: " + msg);
    }

    /**
     * Apaga de forma segura el pool de hilos del Worker.
     * Se llama al cerrar la aplicación del Worker.
     */
    public void shutdown() {
        executor.shutdown(); // Inicia el apagado del pool.
        try {
            // Espera hasta 60 segundos para que las tareas en curso terminen.
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("[Worker " + id + "] ExecutorService no se pudo cerrar correctamente.");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[Worker " + id + "] ExecutorService apagado.");
    }
}