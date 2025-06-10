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

            communicator.waitForShutdown(); // Asegura que se haya apagado el comunicador
            } catch (com.zeroc.Ice.Exception e) {
                System.err.println("Error de ICE al iniciar el Maestro: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            } catch (java.lang.Exception e) {
                System.err.println("Error general inesperado en el Maestro: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
        }
    }
}