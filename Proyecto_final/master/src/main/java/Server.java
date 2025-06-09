import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import com.zeroc.Ice.*;

public class Server {
    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args, "properties.cfg")) {

            ObjectAdapter adapter = communicator.createObjectAdapter("services");

            PublisherI publisher = new PublisherI();
            adapter.add(publisher, Util.stringToIdentity("publisher"));
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

                    int numOfWorkers = Integer.parseInt(command[0]);
                    int min = Integer.parseInt(command[1]);
                    int max = Integer.parseInt(command[2]);
                    int[] result = publisher.startJobLocal(numOfWorkers, min, max);
                    System.out.println("Números perfectos encontrados:");
                    for (int n : result) {
                        System.out.println(n);
                    }
            }

            communicator.waitForShutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}