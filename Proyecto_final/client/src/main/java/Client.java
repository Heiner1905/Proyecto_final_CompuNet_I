import Demo.PublisherPrx;
import com.zeroc.Ice.*;
import com.zeroc.Ice.Exception;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class Client {
    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args, "properties.cfg")) {

            PublisherPrx publisher = PublisherPrx.checkedCast(
                    communicator.propertyToProxy("publisher.proxy"));

            if (publisher == null) {
                throw new RuntimeException("No se pudo obtener el proxy del Publisher.");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Ingrese el número de workers, el mínimo y el máximo (ej: 4 1 10000):");

            String line = reader.readLine();
            String[] tokens = line.trim().split("\\s+");

            int numWorkers = Integer.parseInt(tokens[0]);
            int min = Integer.parseInt(tokens[1]);
            int max = Integer.parseInt(tokens[2]);

            long start = System.currentTimeMillis();

            int[] result = publisher.startJob(numWorkers, min, max);

            long end = System.currentTimeMillis();

            System.out.println("Números perfectos encontrados:");
            Arrays.stream(result).forEach(System.out::println);

            System.out.println("Tiempo de ejecución: " + (end - start) + " ms");

        } catch (Exception | IOException e) {
            e.printStackTrace();
        }
    }
}