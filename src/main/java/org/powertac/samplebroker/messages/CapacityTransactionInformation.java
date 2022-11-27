package org.powertac.samplebroker.messages;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
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

    public CapacityTransactionInformation()
    {
        capacityTransaction = new LinkedHashMap<>();
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
        return capacityTransaction.get(timeslot);
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
