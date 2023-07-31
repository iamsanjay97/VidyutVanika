package org.powertac.samplebroker.messages;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

public class CapacityTransactionInformation
{
    public class CapacityTransactionMessage
    {
        public Integer peakTimeslot;
        public Double threshold;
        public Double exceededKWh;
        public Double charge;

        public CapacityTransactionMessage(Integer peakTimeslot, Double threshold, Double exceededKWh, Double charge)
        {
            this.peakTimeslot = peakTimeslot;
            this.threshold = threshold;
            this.exceededKWh = exceededKWh;
            this.charge = charge;
        }
    }

    private Map<Integer, List<CapacityTransactionMessage>> capacityTransaction;
    private Map<Integer, Double> exceededThresholdMap;
    private Map<Integer, Double> capacityTransactionPrediction;

    public CapacityTransactionInformation()
    {
        capacityTransaction = new HashMap<>();
        exceededThresholdMap = new HashMap<>();
        capacityTransactionPrediction = new HashMap<>();
    }

    public void setCapacityTransaction(Integer timeslot, Integer peakTimeslot, Double threshold, Double exceededKWh, Double charge)
    {
        List<CapacityTransactionMessage> CTM = capacityTransaction.get(timeslot);

        if(CTM == null)
        {
          CTM = new ArrayList<>();
        }
        CTM.add(new CapacityTransactionMessage(peakTimeslot, threshold, exceededKWh, charge));
        capacityTransaction.put(timeslot, CTM);
    }

    public List<CapacityTransactionMessage> getCapacityTransaction(Integer timeslot)
    {
        if(capacityTransaction.containsKey(timeslot))
            return capacityTransaction.get(timeslot);
        else
            return null;
    }

    public Double getCapacityTransactionCharge(Integer timeslot)
    {
        if(capacityTransaction.containsKey(timeslot))
        {
            Double totalCharge = 0.0;

            for(CapacityTransactionMessage mes: capacityTransaction.get(timeslot))
                totalCharge += mes.charge;

            return totalCharge;
        }
        else
            return 0.0;
    }

    public void setExceededThresholdMap(Integer timeslot, Double exceededThreshold)
    {
        exceededThresholdMap.put(timeslot, exceededThreshold);
    }

    public Double getExceededThresholdMap(Integer timeslot)
    {
        if(exceededThresholdMap.get(timeslot) != null)
            return exceededThresholdMap.get(timeslot);
        else
            return 0.0;
    }

    public Map<Integer, Double> getCapacityTransactionPenaltyMap()
    {
        Map<Integer, Double> map = new HashMap<>();

        for (Map.Entry<Integer, List<CapacityTransactionMessage>> entry : capacityTransaction.entrySet())
        {
            List<CapacityTransactionMessage> CTM = entry.getValue();

            for(CapacityTransactionMessage message : CTM)
            {
                map.put(message.peakTimeslot, message.charge);
            }
        }

        return map;
    }

    public void setCapacityTransactionPrediction(Integer timeslot, Double weight)
    {
        if(capacityTransactionPrediction.get(timeslot) == null)
            capacityTransactionPrediction.put(timeslot, 0.0);

        Double count = capacityTransactionPrediction.get(timeslot);
        capacityTransactionPrediction.put(timeslot, count+weight);
    }

    public Double getCapacityTransactionPrediction(Integer timeslot)
    {
        if(capacityTransactionPrediction.get(timeslot) != null)
            return capacityTransactionPrediction.get(timeslot);
        else
            return 0.0;
    }


    /**
     * @to String method
     */

    @Override
    public String toString()
    {
      String str = "\nCapaciity Transaction Information\n";

      for (Map.Entry<Integer, List<CapacityTransactionMessage>> entry : capacityTransaction.entrySet())
      {
        List<CapacityTransactionMessage> CTM = entry.getValue();

        for(CapacityTransactionMessage message : CTM)
        {
            str += "Message Timeslot : " + entry.getKey() + ", Peak Timeslot " + message.peakTimeslot + ", Threshold : " + message.threshold + ", Exceeded KWh : " + message.exceededKWh + ", Charge : " + message.charge + "\n";
        }
      }

      return str;
    }
}
