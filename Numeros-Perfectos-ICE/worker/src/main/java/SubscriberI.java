import com.zeroc.Ice.Current;

import java.util.ArrayList;

public class SubscriberI implements Demo.Subscriber{
    @Override
    public int[] calculatePerfectNum(int minNum, int maxNum){
        int[] perfectNum;
        if(maxNum>minNum){
            ArrayList<Integer> nums = calculate(minNum,maxNum);
            perfectNum = new int[nums.size()];
            for(int i=0; i<nums.size(); i++){
                perfectNum[i] = nums.get(i);
            }
        }else{
            ArrayList<Integer> nums = calculate(maxNum,minNum);
            perfectNum = new int[nums.size()];
            for(int i=0; i<nums.size(); i++){
                perfectNum[i] = nums.get(i);
            }
        }

        return perfectNum;

    }

    public ArrayList<Integer> calculate(int minNum, int maxNum){
        ArrayList<Integer> perfectNums = new ArrayList<>();
        int accSum;
        for (int i = minNum; i <= maxNum; i++) {
            accSum = 0;
            for (int j = 1; j <= Math.ceil(i/2); j++) {
                if (i % j == 0) {
                    accSum += j;
                }
            }
            if (accSum == i) {
                perfectNums.add(i);
            }
        }
        return perfectNums;
    }
}