package org.powertac.samplebroker.messages;

import javafx.util.Pair;
import java.util.HashMap;
import java.util.Map;

public class MarketTransactionInformation
{
    private Map<Integer, Map<Integer, Pair<Double, Double>>> marketTransactionInformationbyMessageTimeslot;
    private Map<Integer, Map<Integer, Pair<Double, Double>>> marketTransactionInformationbyExectionTimeslot;
    private Map<Integer, Double> brokerWholesaleCostMap;

    public MarketTransactionInformation()
    {
        marketTransactionInformationbyMessageTimeslot = new HashMap<>();
        marketTransactionInformationbyExectionTimeslot = new HashMap<>();
        brokerWholesaleCostMap = new HashMap<>();
    }

    public void setMarketTransactionInformationbyMessageTimeslot(Integer messageTime, Integer executionTime, Double price, Double quantity)
    {
        Map<Integer, Pair<Double, Double>> MTMMap  = marketTransactionInformationbyMessageTimeslot.get(messageTime);
        if(MTMMap == null)
        {
            MTMMap = new HashMap<>();
        }

        if(MTMMap.get(executionTime) != null)
        {
          Double q = MTMMap.get(executionTime).getValue() + quantity;
          MTMMap.put(executionTime, new Pair<Double, Double>(price, q));
        }
        else
          MTMMap.put(executionTime, new Pair<Double, Double>(price, quantity));

        marketTransactionInformationbyMessageTimeslot.put(messageTime, MTMMap);
    }

    public Map<Integer, Pair<Double, Double>> getMarketTransactionInformationbyMessageTimeslot(int messageTimeslot)
    {
       return marketTransactionInformationbyMessageTimeslot.get(messageTimeslot);
    }

    public Map<Integer, Map<Integer, Pair<Double, Double>>> getMarketTransactionInformationbyMessageTimeslot()
    {
        return marketTransactionInformationbyMessageTimeslot;
    }

    public void setMarketTransactionInformationbyExectionTimeslot(Integer executionTime, Integer messageTime, Double price, Double quantity)
    {
        Map<Integer, Pair<Double, Double>> MTMMap  = marketTransactionInformationbyExectionTimeslot.get(executionTime);
        if(MTMMap == null)
        {
            MTMMap = new HashMap<>();
        }

        if(MTMMap.get(messageTime) != null)
        {
          Double q = MTMMap.get(messageTime).getValue() + quantity;
          MTMMap.put(messageTime, new Pair<Double, Double>(price, q));
        }
        else
          MTMMap.put(messageTime, new Pair<Double, Double>(price, quantity));

        marketTransactionInformationbyExectionTimeslot.put(executionTime, MTMMap);
    }

    public Map<Integer, Pair<Double, Double>> getMarketTransactionInformationbyExectionTimeslot(int executionTimeslot)
    {
       return marketTransactionInformationbyExectionTimeslot.get(executionTimeslot);
    }

    public Map<Integer, Map<Integer, Pair<Double, Double>>> getMarketTransactionInformationbyExectionTimeslot()
    {
        return marketTransactionInformationbyExectionTimeslot;
    }

    public void setBrokerWholesaleCostMap(Integer timeslot, Double price, Double quantity) 
    {
      if(brokerWholesaleCostMap.get(timeslot) == null)  
        brokerWholesaleCostMap.put(timeslot, 0.0);

      Double cost = (price * quantity) + brokerWholesaleCostMap.get(timeslot);

      brokerWholesaleCostMap.put(timeslot, cost);
    }

    public Double getBrokerWholesaleCost(Integer timeslot)
    {
      if(brokerWholesaleCostMap.get(timeslot) != null)
        return brokerWholesaleCostMap.get(timeslot);
      else
        return 0.0;
    }

    /**
    * @to String method
    */

    @Override
    public String toString()
    {
       String str = "\nMarket Transaction Information\n";

       for (Map.Entry<Integer, Map<Integer, Pair<Double, Double>>> entry : marketTransactionInformationbyMessageTimeslot.entrySet())
       {
         for(Map.Entry<Integer, Pair<Double, Double>> message : entry.getValue().entrySet())
         {
           str += "Message Timeslot : " + entry.getKey() + ", Execution Timeslot " + message.getKey() + ", MCP : " + message.getValue().getKey() + ", Broker's Cleared Quantity : " + message.getValue().getValue() + "\n";
         }
       }

       for (Map.Entry<Integer, Map<Integer, Pair<Double, Double>>> entry : marketTransactionInformationbyExectionTimeslot.entrySet())
       {
         for(Map.Entry<Integer, Pair<Double, Double>> message : entry.getValue().entrySet())
         {
           str += "Execution Timeslot : " + entry.getKey() + ", Message Timeslot " + message.getKey() + ",MCP : " + message.getValue().getKey() + ", Broker's Cleared Quantity : " + message.getValue().getValue() + "\n";
         }
       }

       return str;
    }
}
