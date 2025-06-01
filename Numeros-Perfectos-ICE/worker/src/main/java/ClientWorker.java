import com.zeroc.Ice.*;

import Demo.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ClientWorker {
    public static void main(String[] args) {
        try(Communicator communicator = Util.initialize(args,"properties.cfg")) {

            Subscriber subscriber = new SubscriberI();

            ObjectAdapter adapter = communicator.createObjectAdapter("Subscriber");

            ObjectPrx proxies = adapter.add(subscriber,Util.stringToIdentity("NN"));

            adapter.activate();

            SubscriberPrx subscriberPrx = SubscriberPrx.checkedCast(proxies);

            PublisherPrx publisher = PublisherPrx.checkedCast(communicator.propertyToProxy("publisher.proxy"));

            if(publisher == null){
                throw new Error("Bat Ice Proxy");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String name = reader.readLine();

            publisher.addSubscriber(name, subscriberPrx);
            communicator.waitForShutdown();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}
