import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import com.zeroc.Ice.*;

public class Server {
    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args, "properties.cfg")) {

            ObjectAdapter adapter = communicator.createObjectAdapter("services");

            PublisherI publisher = new PublisherI();
            adapter.add(publisher, Util.stringToIdentity("Publisher"));
            adapter.activate();

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String msj = "";

            System.out.println("Type a message in format <Numero de workers>::<min>::<max> (e.g. 0::3::0::8)");

            while ((msj = reader.readLine()) != null) {
                if (!msj.contains("::")) {
                    System.out.println("Incorrect format. Use <Numero de workers>::<min>::<max>");
                    continue;
                }

                String[] command = msj.split("::", 3);
                try {
                    int numOfWorkers = Integer.parseInt(command[0]);
                    int min = Integer.parseInt(command[1]);
                    int max = Integer.parseInt(command[2]);
                    publisher.startJob(numOfWorkers, min, max).thenAccept(result -> {
                        System.out.println("Números perfectos encontrados:");
                        for (int n : result) {
                            System.out.println(n);
                        }
                    }).exceptionally(ex -> {
                        System.err.println("calculus failed: " + ex.getMessage());
                        return null;
                    });
                } catch (NumberFormatException e) {
                    System.out.println("ID debe ser un número entero.");
                }
            }

            communicator.waitForShutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}