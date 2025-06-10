import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Exception;

import com.zeroc.Ice.*;


/**
 * Clase principal para la aplicación del Maestro.
 * Se encarga de inicializar el entorno ICE, publicar el servicio PublisherI
 * y gestionar el ciclo de vida del Maestro.
 */
public class Server {
    public static void main(String[] args) {
        // Inicializa el comunicador de ICE, leyendo la configuración de master/properties.cfg.
        try (Communicator communicator = Util.initialize(args, "properties.cfg")) {

            // Crea un ObjectAdapter para publicar el servicio del Maestro.
            // El nombre del adaptador ("services") y el endpoint se configuran en properties.cfg.
            ObjectAdapter adapter = communicator.createObjectAdapter("services");

            // Instancia la implementación de Publisher.
            PublisherI publisher = new PublisherI();
            // Añade la instancia del Publisher al adaptador con la identidad "publisher".
            adapter.add(publisher, Util.stringToIdentity("publisher"));
            // Activa el adaptador, haciendo que el Maestro comience a escuchar peticiones.
            adapter.activate();

            // Asegura que el ExecutorService del Maestro y el Communicator de ICE se apaguen limpiamente
            // cuando la JVM se cierra (ej. por Ctrl+C o salida de programa).
            final PublisherI finalPublisher = publisher;
            final Communicator finalCommunicator = communicator;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[Maestro Shutdown Hook] Iniciando apagado de servicios del Maestro...");
                if (finalPublisher != null) {
                    finalPublisher.shutdown(); // Llama al shutdown() de PublisherI para cerrar su pool de hilos.
                }
                if (finalCommunicator != null) {
                    finalCommunicator.shutdown(); // Apaga el Communicator de Ice.
                    finalCommunicator.destroy();  // Libera todos los recursos asociados al Communicator.
                }
                System.out.println("[Maestro Shutdown Hook] Maestro apagado completamente.");
            }, "Maestro_Shutdown_Hook_Thread")); // Nombre descriptivo para el hilo.

            // Bucle para la interfaz de consola del Maestro (permite interactuar o mantenerlo vivo).
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String msj = "";

            System.out.println("Maestro: Ingrese '<num_workers_a_usar>::<min>::<max>' para iniciar una búsqueda síncrona, o 'exit' para apagar:");

            // El bucle lee comandos de la consola. La aplicación se mantiene viva hasta que se escriba "exit"
            // o se reciba una señal de apagado externo (ej. Ctrl+C).
            while ((msj = reader.readLine()) != null && !msj.equalsIgnoreCase("exit")) {
                if (!msj.contains("::")) {
                    System.out.println("Maestro: Formato incorrecto. Use <num_workers>::<min>::<max>");
                    continue;
                }

                String[] command = msj.split("::", 3);

                try {
                    int numOfWorkers = Integer.parseInt(command[0]);
                    int min = Integer.parseInt(command[1]);
                    int max = Integer.parseInt(command[2]);
                    System.out.println(String.format("Maestro: Solicitud síncrona recibida: %d workers, rango [%d, %d]", numOfWorkers, min, max));
                    // Llama al método síncrono del PublisherI. Esto bloqueará la consola.
                    int[] result = publisher.startJob(numOfWorkers, min, max, null);
                    System.out.println("Números perfectos encontrados:");
                    for (int n : result) {
                        System.out.println(n);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Maestro: Error de formato de número en la entrada. " + e.getMessage());
                } catch (java.lang.Exception e) { // Captura cualquier excepción (Ice.Exception, RuntimeException, etc.)
                    System.err.println("Maestro: Error al ejecutar startJob síncrono: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Una vez que el bucle de la consola termina (ej. por "exit"),
            // se espera que el shutdown hook maneje el apagado de ICE.
            // communicator.waitForShutdown(); // Este ya no es necesario aquí si el hook se encarga del cierre total de la JVM.
            } catch (com.zeroc.Ice.Exception e) {
                System.err.println("Error de ICE al iniciar el Maestro: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            } catch (java.io.IOException e) {
                System.err.println("Error de E/S al leer de consola en el Maestro: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            } catch (java.lang.Exception e) {
                System.err.println("Error general inesperado en el Maestro: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
        }
    }
}