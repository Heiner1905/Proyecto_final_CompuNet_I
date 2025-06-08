import com.zeroc.Ice.Current;

import java.util.concurrent.*;

import java.util.ArrayList;

public class SubscriberI implements Demo.Subscriber {

    private int id = -1; // ID asignado dinámicamente por Publisher

    // Numero de hilos que se van a usar para ejecutar las tareas en paralelo, es constante para asegurar la
    // consistencia del valor durante la ejecución del programa
    private static final int NUM_THREADS = 4;

    private final ExecutorService executor;

    public SubscriberI(){
        this.executor = Executors.newFixedThreadPool(NUM_THREADS);
    }


    public void setId(int id) {
        this.id = id;
        System.out.println("Subscriber ID asignado: " + id);
    }

    @Override
    public int[] calculatePerfectNum(int minNum, int maxNum, Current current) {
        int[] perfectNum;
        int min = Math.min(minNum, maxNum);
        int max = Math.max(minNum, maxNum);
        ArrayList<Integer> nums = calculate(min, max);
        perfectNum = new int[nums.size()];
        for (int i = 0; i < nums.size(); i++) {
            perfectNum[i] = nums.get(i);
        }
        return perfectNum;
    }

    public ArrayList<Integer> calculate(int minNum, int maxNum) {
        ArrayList<Integer> perfectNums = new ArrayList<>();
        int accSum;
        for (int i = minNum; i <= maxNum; i++) {
            accSum = 0;
            // Divisores de i
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

    @Override
    public void onUpdate(String msg, Current current) {
        System.out.println("Mensaje recibido por Subscriber " + id + ": " + msg);
    }

    // Esta funcion asegura que se apaguen los hilos de forma segura despues de un tiempo especificado

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService no se pudo cerrar correctamente.");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}