package org.powertac.samplebroker.messages;

import javafx.util.Pair;
import java.util.HashMap;
import java.util.Map;

public class ClearedTradeInformation
{
    private Map<Integer, Map<Integer, Pair<Double, Double>>> clearedTradebyMessageTimeslot;
    private Map<Integer, Map<Integer, Pair<Double, Double>>> clearedTradebyExecutionTimeslot;

    public ClearedTradeInformation()
    {
        clearedTradebyMessageTimeslot = new HashMap<>();
        clearedTradebyExecutionTimeslot = new HashMap<>();
    }

    public void setClearedTradebyMessageTimeslot(int messageTime, int executionTime, double price, double quantity)
    {
        Map<Integer, Pair<Double, Double>> CTMMap  = clearedTradebyMessageTimeslot.get(messageTime);
        if(CTMMap == null)
        {
            CTMMap = new HashMap<>();
        }

        if(CTMMap.get(executionTime) != null)
        {
          Double q = CTMMap.get(executionTime).getValue() + quantity;
          CTMMap.put(executionTime, new Pair<Double, Double>(price, q));
        }
        else
          CTMMap.put(executionTime, new Pair<Double, Double>(price, quantity));

        clearedTradebyMessageTimeslot.put(messageTime, CTMMap);
    }

    public Map<Integer, Map<Integer, Pair<Double, Double>>> getClearedTradebyMessageTimeslot()
    {
        return clearedTradebyMessageTimeslot;
    }

    public Map<Integer, Pair<Double, Double>> getClearedTradebyMessageTimeslot(int messageTimeslot)
    {
       return clearedTradebyMessageTimeslot.get(messageTimeslot);
    }

    public Double getLastMCPForProximity(int messageTimeslot, int proximity)
    {
      try
      {
          return clearedTradebyMessageTimeslot.get(messageTimeslot).get(messageTimeslot + proximity - 1).getKey();
      }
      catch(Exception e)
      {
        while((messageTimeslot >= 362) && (clearedTradebyMessageTimeslot.get(messageTimeslot).get(messageTimeslot + proximity - 1) == null))
        {
          messageTimeslot--;
        }

        if(messageTimeslot >= 362)
          return clearedTradebyMessageTimeslot.get(messageTimeslot).get(messageTimeslot + proximity - 1).getKey();
        else
          return 35.0;
      }
    }

    public Double getLastMCPForProximityPrev(int futureTimeslot, int prevTimeslot)
    {
      try
      {
          return clearedTradebyMessageTimeslot.get(prevTimeslot).get(futureTimeslot).getKey();
      }
      catch(Exception e)
      {
        while((prevTimeslot >= 361) && ((futureTimeslot - prevTimeslot) < 24) && (clearedTradebyMessageTimeslot.get(prevTimeslot).get(futureTimeslot) == null))
        {
          prevTimeslot--;
        }

        if((prevTimeslot >= 361) && ((futureTimeslot - prevTimeslot) < 24))
          return clearedTradebyMessageTimeslot.get(prevTimeslot).get(futureTimeslot).getKey();
        else
          return 35.0;
      }
    }

    public void setClearedTradebyExecutionTimeslot(int executionTime, int messageTime, double price, double quantity)
    {
        Map<Integer, Pair<Double, Double>> CTMMap  = clearedTradebyExecutionTimeslot.get(executionTime);
        if(CTMMap == null)
        {
            CTMMap = new HashMap<>();
        }

        if(CTMMap.get(messageTime) != null)
        {
          Double q = CTMMap.get(messageTime).getValue() + quantity;
          CTMMap.put(messageTime, new Pair<Double, Double>(price, q));
        }
        else
          CTMMap.put(messageTime, new Pair<Double, Double>(price, quantity));

        clearedTradebyExecutionTimeslot.put(executionTime, CTMMap);
    }

    public Map<Integer, Map<Integer, Pair<Double, Double>>> getClearedTradebyExecutionTimeslot()
    {
        return clearedTradebyExecutionTimeslot;
    }

    public Map<Integer, Pair<Double, Double>> getClearedTradebyExecutionTimeslot(int executionTimeslot)
    {
       return clearedTradebyExecutionTimeslot.get(executionTimeslot);
    }

    /**
    * @to String method
    */

    @Override
    public String toString()
    {
       String str = "\nCleared Trade Information\n";

       for (Map.Entry<Integer, Map<Integer, Pair<Double, Double>>> entry : clearedTradebyMessageTimeslot.entrySet())
       {
         for(Map.Entry<Integer, Pair<Double, Double>> message : entry.getValue().entrySet())
         {
           str += "Message Timeslot : " + entry.getKey() + ", Execution Timeslot " + message.getKey() + ", MCP : " + message.getValue().getKey() + ", Net Cleared Quantity : " + message.getValue().getValue() + "\n";
         }
       }

       for (Map.Entry<Integer, Map<Integer, Pair<Double, Double>>> entry : clearedTradebyExecutionTimeslot.entrySet())
       {
         for(Map.Entry<Integer, Pair<Double, Double>> message : entry.getValue().entrySet())
         {
           str += "Execution Timeslot : " + entry.getKey() + ", Message Timeslot " + message.getKey() + ", MCP : " + message.getValue().getKey() + ", Net Cleared Quantity : " + message.getValue().getValue() + "\n";
         }
       }

       return str;
    }
}
