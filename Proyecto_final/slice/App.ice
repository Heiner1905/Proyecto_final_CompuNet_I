module Demo {
    sequence<int> IntSeq;

    interface Subscriber {
        IntSeq calculatePerfectNum(int minNum, int maxNum);
        void onUpdate(string msg);
    }

    interface ClientCallback {
                // Corrección de la línea 17:
                // 'int[]' en Java es 'sequence<int>' (o tu IntSeq) en Slice.
                // 'long' en Java es 'long' (tipo base) en Slice.
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