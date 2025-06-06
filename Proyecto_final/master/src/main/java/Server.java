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

            System.out.println("Type a message in format <ID>::<Message> (e.g. 0::Hola worker)");

            while ((msj = reader.readLine()) != null) {
                if (!msj.contains("::")) {
                    System.out.println("Incorrect format. Use <ID>::<Message>");
                    continue;
                }

                String[] command = msj.split("::", 2);
                try {
                    int id = Integer.parseInt(command[0]);
                    publisher.notifySubscriber(id, command[1]);
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