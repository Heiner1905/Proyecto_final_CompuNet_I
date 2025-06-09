package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.stage.WindowEvent;

public class MainApp extends Application {

    // Necesitamos una referencia estática al controlador para poder apagar ICE
    private static ClientController clientControllerInstance;

    @Override
    public void start(Stage stage) throws Exception {
        // Mejor forma de cargar FXML:
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("/client/client_view.fxml"));
        Parent root = fxmlLoader.load(); // Carga el root
        // No necesitas 'fxmlLoader.getController()' si el controlador está configurado con fx:controller en el FXML
        // ClientController controller = fxmlLoader.getController(); // Si necesitas acceder al controlador

        stage.setTitle("Cliente - Números Perfectos");
        stage.setScene(new Scene(root));
        stage.show();
    }
    // Método para manejar el cierre de la ventana
    private void handleWindowClose(WindowEvent event) {
        System.out.println("Cerrando la aplicación JavaFX...");
        if (clientControllerInstance != null) {
            // Llama a un método en el controlador para apagar ICE
            clientControllerInstance.shutdownIce();
        }
        // Opcional: Para asegurar que toda la aplicación JavaFX se cierre,
        // incluyendo hilos de la plataforma FX si hubiera otros.
        // Platform.exit();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
