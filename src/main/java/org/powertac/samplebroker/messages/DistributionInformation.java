package org.powertac.samplebroker.messages;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.util.Pair;

public class DistributionInformation
{
    private Map<Integer, Double> totalConsumption;
    private Map<Integer, Double> totalProduction;
    private Map<Integer, Pair<Double, Double>> distributionTransaction;

    public DistributionInformation()
    {
        totalConsumption = new LinkedHashMap<>();
        totalProduction = new LinkedHashMap<>();
        distributionTransaction = new LinkedHashMap<>();
    }

    public void setTotalConsumption(Integer timeslot,  Double consumption)
    {      
      if(totalConsumption.get(timeslot) == null)
        totalConsumption.put(timeslot, consumption);
      else
        totalConsumption.put(timeslot, totalConsumption.get(timeslot) + consumption);
    }

    public Double getTotalConsumption(Integer timeslot)
    {
      if(totalConsumption.get(timeslot) != null)
        return totalConsumption.get(timeslot);
      else
        return 0.0;
    }

    public Map<Integer, Double> getTotalConsumption()
    {
      return totalConsumption;
    }

    public void setTotalProduction(Integer timeslot, Double production)
    {
      if(totalProduction.get(timeslot) == null)
        totalProduction.put(timeslot, production);
      else
        totalProduction.put(timeslot, totalProduction.get(timeslot) + production);
    }

    public Double getTotalProduction(Integer timeslot)
    {
      if(totalProduction.get(timeslot) != null)
        return totalProduction.get(timeslot);
      else
        return 0.0;
    }

    public void setDistributionTransaction(Integer timeslot, Double kwh, Double charge)
    {
        distributionTransaction.put(timeslot, new Pair <Double, Double> (kwh, charge));
    }

    public Pair <Double, Double> getDistributionTransaction(Integer timeslot)
    {
        return distributionTransaction.get(timeslot);
    }

    /**
     * @to String method
     */

    @Override
    public String toString()
    {
      String str = "\nDistribution Information\n";

      for (Map.Entry<Integer, Double> entry : totalConsumption.entrySet())
      {
        str += "Message Timeslot : " + entry.getKey() + ", Total Consumption " + entry.getValue() + "\n";
      }

      for (Map.Entry<Integer, Double> entry : totalProduction.entrySet())
      {
        str += "Message Timeslot : " + entry.getKey() + ", Total Production " + entry.getValue() + "\n";
      }

      for (Map.Entry<Integer, Pair<Double, Double>> entry : distributionTransaction.entrySet())
      {
        str += "Message Timeslot : " + entry.getKey() + ", Quantity " + entry.getValue().getKey() + ", Price " + entry.getValue().getValue() + "\n";
      }

      return str;
    }
}
