package org.powertac.samplebroker.messages;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import javafx.util.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class BalancingMarketInformation
{
    private Map<Integer, Pair <Double, Double>> balancingTransaction;
    private Map<Integer, Double> netImbalance;

    public BalancingMarketInformation()
    {
        balancingTransaction = new LinkedHashMap<>();
        netImbalance = new LinkedHashMap<>();
    }

    public void setBalancingTransaction(Integer timeslot, Double kwh, Double charge)
    {
        balancingTransaction.put(timeslot, new Pair <Double, Double> (kwh, charge));
    }

    public Pair <Double, Double> getBalancingTransaction(Integer timeslot)
    {
        return balancingTransaction.get(timeslot);
    }

    public Map<Integer, Pair <Double, Double>> getBalancingTransaction()
    {
        return balancingTransaction;
    }

    public void setNetImbalance(Integer timeslot, Double kwh)
    {
        netImbalance.put(timeslot, kwh);
    }

    public Double getNetImbalance(Integer timeslot)
    {
        return netImbalance.get(timeslot);
    }

    public Double getAvgImbalance()
    {
        Double avgImbalance = 0.0;

        for(Pair<Double, Double> item : balancingTransaction.values())
        {
          avgImbalance += Math.abs(item.getKey());
        }

        avgImbalance /= balancingTransaction.size();

        return avgImbalance;
    }

    public Map<Integer, Double> getNetImbalance()
    {
        return netImbalance;
    }

    public Double getAvgBalancingPrice()
    {
      Double avg = 0.0;
      int count = 0;

      for(Pair<Double, Double> item : balancingTransaction.values())
      {
        avg += Math.abs(item.getValue()/item.getKey()) * 1000.0;
        count++;
      }
      avg /= count;

      return avg;
    }

    /**
     * @to String method
     */

    @Override
    public String toString()
    {
      String str = "\nBalancing Information\n";

      for (Map.Entry<Integer, Pair<Double, Double>> entry : balancingTransaction.entrySet())
      {
        str += "Message Timeslot : " + entry.getKey() + ", Broker's Imbalance " + entry.getValue().getKey() + ", Penalty : " + entry.getValue().getValue() + "\n";
      }

      for (Map.Entry<Integer, Double> entry : netImbalance.entrySet())
      {
        str += "Message Timeslot : " + entry.getKey() + ", Net Imbalance " + entry.getValue() + "\n";
      }

      return str;
    }

    public void printToFile(String bootFile, ArrayList<String> brokers)
    {
      try
      {
        FileWriter fr = new FileWriter(new File("Balancing_Validation.csv"), true);
        BufferedWriter br = new BufferedWriter(fr);

        br.write("\n\n" + bootFile + "\n" + brokers + "\n");

        for(Map.Entry<Integer, Pair<Double, Double>> outerItem : balancingTransaction.entrySet())
        {
          Integer timeslot = outerItem.getKey();
          Pair<Double, Double> oItems = outerItem.getValue();

          br.write(timeslot + "," + oItems.getKey() + "," + oItems.getValue() + "\n");
        }

        br.close();
        fr.close();
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
    }
}
