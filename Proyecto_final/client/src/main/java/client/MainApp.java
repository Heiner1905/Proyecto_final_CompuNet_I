package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.stage.WindowEvent;


/**
 * Clase principal de la aplicación JavaFX para el cliente.
 * Se encarga de cargar la interfaz de usuario y gestionar el ciclo de vida básico de la aplicación.
 */
public class MainApp extends Application {

    // Referencia estática al controlador para poder acceder a sus métodos de limpieza de ICE
    private static ClientController clientControllerInstance;

    /**
     * Método start de JavaFX, punto de entrada de la aplicación GUI.
     * @param stage El Stage principal de la aplicación.
     * @throws Exception Si ocurre un error al cargar el FXML.
     */
    @Override
    public void start(Stage stage) throws Exception {
        // Carga el archivo FXML que define la interfaz de usuario.
        // Se usa MainApp.class.getResource para asegurar que el FXML se encuentre en el classpath.
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("/client/client_view.fxml"));
        Parent root = fxmlLoader.load(); // Carga el root

        // Obtiene la instancia del controlador (ClientController) que JavaFX ha creado.
        clientControllerInstance = fxmlLoader.getController();

        // Configura el Stage (ventana) principal.
        stage.setTitle("Cliente - Números Perfectos");
        stage.setScene(new Scene(root));

        // Cuando el usuario cierra la ventana, se llama a handleWindowClose.
        stage.setOnCloseRequest(this::handleWindowClose);

        // Muestra la ventana.
        stage.show();
    }


    /**
     * Manejador del evento de cierre de la ventana principal de JavaFX.
     * Asegura que los recursos de ICE sean liberados.
     * @param event El evento de cierre de ventana.
     */    private void handleWindowClose(WindowEvent event) {
        System.out.println("Cerrando la aplicación JavaFX...");
        if (clientControllerInstance != null) {
            // Llama a un método en el controlador para apagar ICE
            clientControllerInstance.shutdownIce();
        }
        Platform.exit();
    }


    /**
     * Método main, punto de entrada del programa Java.
     * Inicia la aplicación JavaFX.
     * @param args Argumentos de la línea de comandos.
     */
    public static void main(String[] args) {
        launch(args); // Ejecuta la aplicación JavaFX.
    }
}
