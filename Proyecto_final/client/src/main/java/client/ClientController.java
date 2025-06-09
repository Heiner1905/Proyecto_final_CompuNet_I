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


public class ClientController implements ClientCallback { // Implementa ClientCallback
    @FXML private TextField startField, endField;
    @FXML private TextArea resultArea;
    @FXML private Label executionTimeLabel;

    private ClientCallbackPrx selfProxy;

    private PublisherPrx publisher; // Asumo que Publisher es el Maestro
    private Instant startTime;
    private Communicator communicator; // Mantener una referencia al communicator para shutdown


    private final String clientId = "client" + java.util.UUID.randomUUID().toString().substring(0, 8); // Declarar como final a nivel de clase

    public void initialize() {
        new Thread(this::initIce).start();
    }

    private void initIce() {
        try {
            communicator = Util.initialize(new String[0], "properties.cfg");
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("ClientCallbackAdapter", "default");

            ObjectPrx clientBaseProxy = adapter.add(this, Util.stringToIdentity(clientId));
            adapter.activate();

            selfProxy = ClientCallbackPrx.checkedCast(clientBaseProxy); // <<< Aquí se guarda el proxy del propio cliente
            if (selfProxy == null) {
                throw new Error("Failed to cast own proxy to ClientCallbackPrx.");
            }

            // Leer la configuración del proxy del Maestro desde client/properties.cfg
            publisher = PublisherPrx.checkedCast(communicator.propertyToProxy("publisher.proxy"));
            if (publisher == null) {
                // Si publisher.proxy no está en properties.cfg o es inválido
                throw new Error("Error al obtener el proxy del Maestro. Verifique 'publisher.proxy' en client/properties.cfg y asegúrese de que el Maestro esté activo.");
            }

            System.out.println("Cliente ICE con ID: " + clientId + " inicializado y esperando.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (communicator != null) {
                    communicator.shutdown();
                    System.out.println("Cliente ICE apagado.");
                }
            }));

        } catch (java.lang.Exception e) { // Usar java.lang.Exception para evitar ambigüedad
            e.printStackTrace();
            Platform.runLater(() -> resultArea.setText("Error al iniciar ICE: " + e.getMessage()));
        }
    }

    @FXML
    private void onBuscarClicked() {
        try {
            int start = Integer.parseInt(startField.getText());
            int end = Integer.parseInt(endField.getText());
            startTime = Instant.now();

            new Thread(() -> {
                try {
                    if (selfProxy == null) { // Fallback por si la inicialización falló
                        Platform.runLater(() -> resultArea.setText("Error interno: Proxy del cliente no inicializado (selfProxy es null)."));
                        return;
                    }
                    if (publisher == null) {
                        Platform.runLater(() -> resultArea.setText("Error interno: Proxy del Maestro no inicializado."));
                        return;
                    }

                    // Llama al nuevo método del Publisher
                    publisher.requestPerfectNumbers(start, end, selfProxy);

                    Platform.runLater(() -> resultArea.setText("Solicitud enviada. Esperando resultados..."));
                } catch (java.lang.Exception e) { // Usar java.lang.Exception para evitar ambigüedad
                    e.printStackTrace();
                    Platform.runLater(() -> resultArea.setText("Error al enviar solicitud: " + e.getMessage()));
                }
            }).start();
        } catch (NumberFormatException e) {
            Platform.runLater(() -> resultArea.setText("Por favor, ingresa valores numéricos válidos."));
        }
    }

    public void shutdownIce() {
        if (communicator != null) {
            System.out.println("Apagando Communicator de ICE para el cliente " + clientId + "...");
            communicator.shutdown(); // Cierra el Communicator
            communicator.destroy();   // Libera todos los recursos de ICE
            communicator = null; // Evita usarlo después de cerrarlo
        }
    }

    @Override
    public void perfectNumbersFound(int[] perfectNums, long durationMs, Current current) {
        Platform.runLater(() -> {
            resultArea.setText("Números perfectos encontrados: " + Arrays.toString(perfectNums));
            executionTimeLabel.setText("Tiempo de ejecución total: " + durationMs + " ms");
        });
    }
}