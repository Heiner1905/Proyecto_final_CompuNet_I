module Demo {
    sequence<int> IntSeq;

    interface Subscriber {
        IntSeq calculatePerfectNum(int minNum, int maxNum);
        void onUpdate(string msg);
    }

    interface Publisher {
        int getSubscribersNum();
        int addSubscriber(Subscriber* o);
        void removeSubscriber(int id);
        void reportResult(int jobId, IntSeq results);
        void startJob(int numWorkers, int minNum, int maxNum);
        void receiveResults(IntSeq perfectNumbers);
    }
}