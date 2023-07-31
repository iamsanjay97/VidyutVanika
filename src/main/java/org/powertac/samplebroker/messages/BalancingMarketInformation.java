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
    private Pair<Integer, Double> avgBalancingPrice;
    private Pair<Integer, Double> avgBuyBalancingPrice;

    public BalancingMarketInformation()
    {
        balancingTransaction = new LinkedHashMap<>();
        netImbalance = new LinkedHashMap<>();
        avgBalancingPrice = new Pair<Integer, Double>(1, 0.0);
        avgBuyBalancingPrice = new Pair<Integer, Double>(1, 0.0);
    }

    public void setBalancingTransaction(Integer timeslot, Double kwh, Double charge)
    {
        balancingTransaction.put(timeslot, new Pair <Double, Double> (kwh, charge));
        setAvgBalancingPrice(charge);
    }

    public Pair <Double, Double> getBalancingTransaction(Integer timeslot)
    {
        return balancingTransaction.get(timeslot);
    }

    public Double getBalancingCharge(Integer timeslot)
    {
        if(balancingTransaction.get(timeslot) != null)
          return balancingTransaction.get(timeslot).getValue();
        else 
          return 0.0;
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

    public Map<Integer, Double> getNetImbalance()
    {
        return netImbalance;
    }

    public void setAvgBalancingPrice(Double charge)
    {
      Double avg = ((avgBalancingPrice.getKey() * avgBalancingPrice.getValue()) + Math.abs(charge)) / (avgBalancingPrice.getKey() + 1);
      avgBalancingPrice = new Pair<Integer, Double>(avgBalancingPrice.getKey() + 1, avg);
    }

    public Double getAvgBalancingPrice()
    {
      return avgBalancingPrice.getValue();
    }

    public void setAvgBuyBalancingPrice(Double charge)
    {
      Double avg = ((avgBuyBalancingPrice.getKey() * avgBuyBalancingPrice.getValue()) + Math.abs(charge)) / (avgBuyBalancingPrice.getKey() + 1);
      avgBuyBalancingPrice = new Pair<Integer, Double>(avgBuyBalancingPrice.getKey() + 1, avg);
    }

    public Double getAvgBuyBalancingPrice()
    {
      if(avgBuyBalancingPrice.getValue() != null)
        return avgBuyBalancingPrice.getValue();
      else
        return 50.0;
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
