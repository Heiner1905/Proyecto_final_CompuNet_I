import Demo.PublisherPrx;
import com.zeroc.Ice.*;
import com.zeroc.Ice.Exception;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;


/**
 * Cliente de consola para solicitar la búsqueda de números perfectos de forma síncrona al Maestro.
 * Permite al usuario ingresar el número de workers, el rango mínimo y el rango máximo.
 */
public class Client {
    public static void main(String[] args) {
        // Inicializa el comunicador de ICE. Se lee la configuración de 'properties.cfg'.
        try (Communicator communicator = Util.initialize(args, "properties.cfg")) {

            // Obtiene el proxy del Publisher (Maestro) configurado en properties.cfg.
            PublisherPrx publisher = PublisherPrx.checkedCast(
                    communicator.propertyToProxy("publisher.proxy"));

            // Verifica si el proxy del Maestro se obtuvo correctamente.
            if (publisher == null) {
                System.err.println("Error: No se pudo obtener el proxy del Maestro. Verifique 'publisher.proxy' en client/properties.cfg y asegúrese de que el Maestro esté activo.");
                System.exit(1); // Sale con código de error
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Cliente: Ingrese el número de workers, el mínimo y el máximo (ej: 4 1 10000):");

            String line = reader.readLine();
            String[] tokens = line.trim().split("\\s+");

            // Valida y parsea la entrada del usuario.
            if (tokens.length != 3) {
                System.err.println("Cliente: Formato de entrada incorrecto. Debe ser '<num_workers> <min> <max>'.");
                System.exit(1);
            }

            int numWorkers = Integer.parseInt(tokens[0]);
            int min = Integer.parseInt(tokens[1]);
            int max = Integer.parseInt(tokens[2]);

            // Mide el tiempo de ejecución.
            long start = System.currentTimeMillis();

            // Realiza la llamada síncrona al Maestro para iniciar el trabajo.
            // Esta llamada es bloqueante hasta que el Maestro devuelve los resultados.
            int[] result = publisher.startJob(numWorkers, min, max);

            long end = System.currentTimeMillis();

            System.out.println("\nCliente: Números perfectos encontrados:");
            if (result.length > 0) {
                Arrays.stream(result).forEach(System.out::println);
            } else {
                System.out.println("No se encontraron números perfectos en el rango especificado.");
            }

            System.out.println("Cliente: Tiempo de ejecución: " + (end - start) + " ms");

        } catch (com.zeroc.Ice.Exception e) {
            // Captura excepciones específicas de ICE (ej. problemas de conexión, objeto no encontrado).
            System.err.println("Cliente de Consola: Error de ICE - " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (java.io.IOException e) {
            // Captura excepciones de entrada/salida (ej. al leer de la consola).
            System.err.println("Cliente de Consola: Error de E/S - " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (java.lang.NumberFormatException e) {
            // Captura errores si el usuario no ingresa números válidos.
            System.err.println("Cliente de Consola: Error de formato de número - " + e.getMessage());
            System.exit(1);
        } catch (java.lang.RuntimeException e) {
            // Captura otras excepciones de tiempo de ejecución no esperadas.
            System.err.println("Cliente de Consola: Error inesperado - " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}