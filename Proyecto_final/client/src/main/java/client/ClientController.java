package client;

import Demo.PublisherPrx;
import Demo.ClientCallback;
import Demo.ClientCallbackPrx;
import com.zeroc.Ice.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.Instant;
import java.util.Arrays; // Para imprimir el array de enteros


/**
 * Controlador de la interfaz gráfica del cliente JavaFX para la búsqueda de números perfectos.
 * Actúa como cliente Ice y callback para el Maestro.
 */
public class ClientController implements ClientCallback { // Implementa ClientCallback
    // Componentes de la GUI inyectados por FXML
    @FXML private TextField startField, endField;
    @FXML private TextArea resultArea;
    @FXML private Label executionTimeLabel;

    // Proxies y comunicador de ICE
    private ClientCallbackPrx selfProxy; // Proxy del propio cliente (para que el Maestro pueda llamarlo de vuelta)
    private PublisherPrx publisher; // Proxy del Maestro
    private Communicator communicator; // Comunicador de ICE para gestionar la comunicación


    // Variables de estado
    private Instant startTime; // Para medir el tiempo de ejecución de la solicitud
    // ID único para esta instancia de cliente, utilizado en la identidad del objeto ICE
    private final String clientId = "client" + java.util.UUID.randomUUID().toString().substring(0, 8);


    /**
     * Método de inicialización de la GUI.
     * Inicia la configuración de ICE en un hilo separado para no bloquear la interfaz.
     */
    public void initialize() {
        new Thread(this::initIce).start();
    }

    /**
     * Configura y lanza el entorno ICE para el cliente.
     * Inicializa el comunicador, el adaptador y los proxies necesarios.
     */
    private void initIce() {
        try {
            // Inicializa el comunicador de ICE. Se lee la configuración de 'properties.cfg'.
            communicator = Util.initialize(new String[0], "properties.cfg");

            // Crea un adaptador para que el Maestro pueda realizar llamadas de callback a este cliente.
            // Se usa "default" para que ICE elija un puerto efímero libre automáticamente,
            // permitiendo múltiples instancias del cliente en la misma máquina.
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("ClientCallbackAdapter", "default");

            // Añade la instancia de este controlador (que implementa ClientCallback) al adaptador
            // con una identidad única para este cliente.
            ObjectPrx clientBaseProxy = adapter.add(this, Util.stringToIdentity(clientId));
            adapter.activate();

            // Guarda el proxy del propio cliente, tipado como ClientCallbackPrx,
            // para pasarlo al Maestro en las solicitudes.
            selfProxy = ClientCallbackPrx.checkedCast(clientBaseProxy); // <<< Aquí se guarda el proxy del propio cliente
            if (selfProxy == null) {
                // Lanza un error si la conversión de proxy falla, lo cual es crítico.
                throw new Error("Error al castear el proxy del propio cliente a ClientCallbackPrx. Revise las definiciones Slice.");
            }

            // Obtiene el proxy del Maestro (Publisher) configurado en client/properties.cfg.
            publisher = PublisherPrx.checkedCast(communicator.propertyToProxy("publisher.proxy"));
            if (publisher == null) {
                // Si publisher.proxy no está en properties.cfg o es inválido
                throw new Error("Error al obtener el proxy del Maestro. Verifique 'publisher.proxy' en client/properties.cfg y asegúrese de que el Maestro esté activo.");
            }

            // Mensajes de confirmación en consola.
            System.out.println("Cliente ICE con ID: " + clientId + " inicializado y esperando solicitudes.");
            System.out.println("Conectado al Maestro en: " + communicator.propertyToProxy("publisher.proxy"));

            // Agrega un shutdown hook para asegurar un cierre limpio del comunicador ICE
            // cuando la JVM se apague.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (communicator != null) {
                    communicator.shutdown(); // Cierra el comunicador
                    communicator.destroy();
                    System.out.println("Cliente ICE apagado.");
                }
            }));

        } catch (java.lang.Exception e) {
            // Captura cualquier excepción durante la inicialización de ICE y la muestra en la GUI y consola.
            System.err.println("Cliente: Error durante la inicialización de ICE - " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> resultArea.setText("Error al iniciar ICE: " + e.getMessage()));
        }
    }



    /**
     * Maneja el evento de clic en el botón "Buscar" de la GUI.
     * Envía la solicitud de búsqueda de números perfectos al Maestro de forma asíncrona.
     */
    @FXML
    private void onBuscarClicked() {
        try {
            // Valida y parsea el rango ingresado por el usuario.
            int start = Integer.parseInt(startField.getText());
            int end = Integer.parseInt(endField.getText());
            startTime = Instant.now(); // Marca el inicio del tiempo de ejecución.

            // Valida que el rango sea válido
            if (start < 0 || end < 0) {
                Platform.runLater(() -> resultArea.setText("Los números de rango deben ser positivos."));
                return;
            }
            if (start > end) { // Permite que el Maestro los reordene, pero informa al usuario
                Platform.runLater(() -> resultArea.setText("Rango invertido. El Maestro lo ajustará. Solicitando [" + end + ", " + start + "]"));
            }

            // Ejecuta la petición al Maestro en un hilo separado para no bloquear la GUI.
            new Thread(() -> {
                try {
                    // Verifica que los proxies estén inicializados antes de usarlos.
                    if (selfProxy == null) {
                        Platform.runLater(() -> resultArea.setText("Error interno: Proxy de callback del cliente no inicializado."));
                        return;
                    }
                    if (publisher == null) {
                        Platform.runLater(() -> resultArea.setText("Error interno: Proxy del Maestro no inicializado."));
                        return;
                    }

                    // Envía la solicitud al Maestro, incluyendo el rango y el proxy de callback del propio cliente.
                    publisher.requestPerfectNumbers(start, end, selfProxy);

                    // Actualiza la GUI para indicar que la solicitud fue enviada.
                    Platform.runLater(() -> resultArea.setText("Solicitud enviada. Esperando resultados..."));
                } catch (com.zeroc.Ice.Exception e) {
                    // Captura errores de comunicación con ICE.
                    System.err.println("Cliente: Error de ICE al enviar solicitud - " + e.getMessage());
                    e.printStackTrace();
                    Platform.runLater(() -> resultArea.setText("Error al enviar solicitud al Maestro: " + e.getMessage()));
                } catch (java.lang.Exception e) {
                    // Captura cualquier otra excepción inesperada.
                    System.err.println("Cliente: Error inesperado al enviar solicitud - " + e.getMessage());
                    e.printStackTrace();
                    Platform.runLater(() -> resultArea.setText("Error inesperado al enviar solicitud: " + e.getMessage()));
                }
            }, "Client_Request_Thread").start();
        } catch (java.lang.NumberFormatException e) {
            // Captura errores si el usuario no ingresa valores numéricos válidos.
            Platform.runLater(() -> resultArea.setText("Por favor, ingresa valores numéricos válidos para el rango."));
        }
    }

    /**
     * Método invocado por el Maestro para devolver los números perfectos encontrados.
     * Implementa el callback ClientCallback.
     * @param perfectNums Array de números perfectos encontrados.
     * @param durationMs Duración total de la búsqueda en milisegundos (reportada por el Maestro).
     * @param current Contexto de la llamada ICE.
     */
    @Override
    public void perfectNumbersFound(int[] perfectNums, long durationMs, Current current) {
        // Actualiza la GUI en el hilo de la aplicación JavaFX.
        Platform.runLater(() -> {
            resultArea.setText("Números perfectos encontrados: " + Arrays.toString(perfectNums));
            executionTimeLabel.setText("Tiempo de ejecución total: " + durationMs + " ms");
            System.out.println("Cliente: Resultados recibidos. Tiempo total: " + durationMs + " ms. Cantidad de números: " + perfectNums.length);
        });
    }

    /**
     * Método para apagar el comunicador ICE del cliente.
     * Llamado desde MainApp cuando la ventana principal se cierra.
     */
    public void shutdownIce() {
        if (communicator != null) {
            System.out.println("Apagando Communicator de ICE para el cliente " + clientId + "...");
            communicator.shutdown(); // Cierra el Communicator
            communicator.destroy();   // Libera todos los recursos de ICE
            communicator = null; // Evita usarlo después de cerrarlo
        }
    }


}