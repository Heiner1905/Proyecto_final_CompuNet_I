module Demo {
    sequence<int> IntSeq;

    interface Subscriber {
        IntSeq calculatePerfectNum(int num1, int num2);
    }

    interface Publisher {
        void addSubscriber(string name, Subscriber* o);
        void removeSubscriber(string name);
        void reportResult(string jobId, IntSeq results);
        void startJob(int num1, int num2);
    }

    interface ClientCallback {
        void receiveResults(IntSeq perfectNumbers);
    }
}