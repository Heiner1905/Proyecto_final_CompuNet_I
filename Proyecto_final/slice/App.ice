module Demo {
    sequence<int> IntSeq;

    interface Subscriber {
        IntSeq calculatePerfectNum(int minNum, int maxNum);
        void onUpdate(string msg);
    }

    interface Publisher {
        void addSubscriber(int name, Subscriber* o);
        void removeSubscriber(int name);
        void reportResult(int jobId, IntSeq results);
        void startJob(int minNum, int maxNum);
        void receiveResults(IntSeq perfectNumbers);
    }
}