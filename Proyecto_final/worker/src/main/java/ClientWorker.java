import Demo.PublisherPrx;
import Demo.SubscriberPrx;
import com.zeroc.Ice.*;

/**
 * Clase principal para la aplicación del Worker.
 * Se encarga de inicializar el entorno ICE para el worker, registrarse con el Maestro
 * y gestionar su ciclo de vida.
 */

public class ClientWorker {
    public static void main(String[] args) {
        SubscriberI subscriber = null;
        // Inicializa el comunicador de ICE, leyendo la configuración de worker/properties.cfg.
        try(Communicator communicator = Util.initialize(args,"properties.cfg")) {

            subscriber = new SubscriberI(); // Instancia la implementación del Subscriber.

            // Crea un ObjectAdapter para publicar el servicio del Worker.
            // El nombre del adaptador ("Subscriber") y el endpoint se configuran en properties.cfg.
            ObjectAdapter adapter = communicator.createObjectAdapter("Subscriber");

            // Genera un ID único para esta instancia del worker.
            String workerId = java.util.UUID.randomUUID().toString().substring(0, 8);

            // Añade la instancia del Worker al adaptador con su ID único.
            ObjectPrx proxies = adapter.add(subscriber,Util.stringToIdentity(workerId));

            // Activa el adaptador, haciendo que el Worker comience a escuchar peticiones.
            adapter.activate();

            SubscriberPrx subscriberPrx = SubscriberPrx.checkedCast(proxies);
            // Obtiene el proxy del Publisher (Maestro) configurado en worker/properties.cfg.
            PublisherPrx publisher = PublisherPrx.checkedCast(communicator.propertyToProxy("publisher.proxy"));

            if(publisher == null){
                // Lanza una excepción si el proxy del Maestro no se pudo obtener o es inválido.
                throw new RuntimeException("Error: No se pudo obtener el proxy del Maestro. Verifique 'publisher.proxy' en worker/properties.cfg y asegúrese de que el Maestro esté activo.");
            }

            System.out.println("Worker " + workerId + ": Intentando registrarse con el Maestro en " + communicator.propertyToProxy("publisher.proxy") + "...");
            int assignedIdByMaster = publisher.addSubscriber(subscriberPrx);
            // Actualiza el ID del worker con el ID definitivo asignado por el Maestro
            System.out.println("Worker " + workerId + ": ¡Registrado exitosamente con el Maestro! ID asignado: " + assignedIdByMaster);

            // Asegura que el ExecutorService del Worker y el Communicator de ICE se apaguen limpiamente
            // cuando la JVM se cierra (ej. por Ctrl+C o salida de programa).
            final SubscriberI finalSubscriber = subscriber;
            final Communicator finalCommunicator = communicator;
            final String finalWorkerId = workerId; // Para usar en el mensaje del hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[Worker Shutdown Hook] Iniciando apagado de servicios del Worker " + finalWorkerId + "...");
                if (finalSubscriber != null) {
                    finalSubscriber.shutdown(); // Llama al shutdown() de SubscriberI para cerrar su pool de hilos.
                }
                if (finalCommunicator != null) {
                    finalCommunicator.shutdown(); // Apaga el Communicator de Ice.
                    finalCommunicator.destroy();  // Libera todos los recursos asociados al Communicator.
                }
                System.out.println("[Worker Shutdown Hook] Worker " + finalWorkerId + " apagado completamente.");
            }, "Worker_Shutdown_Hook_Thread")); // Nombre descriptivo para el hilo.
            // Mantiene al Worker vivo y escuchando peticiones del Maestro hasta que se apague la JVM.
            communicator.waitForShutdown();

        } catch (com.zeroc.Ice.Exception e) {
            System.err.println("Error de ICE en el Worker: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (java.lang.RuntimeException e) { // Captura RuntimeException lanzadas (ej. "Error: No se pudo obtener...")
            System.err.println("Error de ejecución en el Worker: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (java.lang.Exception e) { // Captura cualquier otra excepción general
            System.err.println("Error general inesperado en el Worker: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
