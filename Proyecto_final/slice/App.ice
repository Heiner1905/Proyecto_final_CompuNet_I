module Demo {
    sequence<int> IntSeq;

    interface Subscriber {
        IntSeq calculatePerfectNum(int minNum, int maxNum);
        void onUpdate(string msg);
        void setId(int id);
    }

    interface ClientCallback {
                void perfectNumbersFound(IntSeq perfectNums, long durationMs);
        }

    interface Publisher {
        int getSubscribersNum();
        int addSubscriber(Subscriber* o);
        void removeSubscriber(int id);
        IntSeq startJob(int numWorkers, int min, int max);
        void requestPerfectNumbers(int min, int max, ClientCallback* clientCallback);
    }


}