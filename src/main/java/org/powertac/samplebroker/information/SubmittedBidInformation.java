package org.powertac.samplebroker.information;

import java.util.Set;
import java.util.Map;
import javafx.util.Pair;
import java.util.HashMap;

public class SubmittedBidInformation
{
  Map<Integer, Map<Integer, Pair<Double , Double>>> submittedBidInformationbyMessageTimeslot;
  Map<Integer, Map<Integer, Pair<Double , Double>>> submittedBidInformationbyExecutionTimeslot;

  public SubmittedBidInformation()
  {
      submittedBidInformationbyMessageTimeslot = new HashMap<>();
      submittedBidInformationbyExecutionTimeslot = new HashMap<>();
  }

  public void setSubmittedBidInformationbyMessageTimeslot(Integer messageTime, Integer executionTime, Double price, Double quantity)
  {
      Map<Integer, Pair<Double, Double>> SBMMap = submittedBidInformationbyMessageTimeslot.get(messageTime);

      if(SBMMap == null)
      {
          SBMMap = new HashMap<>();
      }

      SBMMap.put(executionTime, new Pair<Double, Double>(price, quantity));
      submittedBidInformationbyMessageTimeslot.put(messageTime, SBMMap);
  }

  public Map<Integer, Pair<Double, Double>> getSubmittedBidInformationbyMessageTimeslot(Integer timeslot)
  {
      return submittedBidInformationbyMessageTimeslot.get(timeslot);
  }

  public Map<Integer, Map<Integer, Pair<Double, Double>>> getSubmittedBidInformationbyMessageTimeslot()
  {
      return submittedBidInformationbyMessageTimeslot;
  }

  public void setSubmittedBidInformationbyExecutionTimeslot(Integer executionTime, Integer messageTime, Double price, Double quantity)
  {
      Map<Integer, Pair<Double, Double>> SBMMap  = submittedBidInformationbyExecutionTimeslot.get(executionTime);

      if(SBMMap == null)
      {
          SBMMap = new HashMap<>();
      }

      SBMMap.put(messageTime, new Pair<Double, Double>(price, quantity));
      submittedBidInformationbyExecutionTimeslot.put(executionTime, SBMMap);
  }

  public Map<Integer, Pair<Double, Double>> getSubmittedBidInformationbyExecutionTimeslot(Integer timeslot)
  {
      return submittedBidInformationbyExecutionTimeslot.get(timeslot);
  }

  public Map<Integer, Map<Integer, Pair<Double, Double>>> getSubmittedBidInformationbyExecutionTimeslot()
  {
      return submittedBidInformationbyExecutionTimeslot;
  }

  public void getSubmittedBidInformationbyExecutionTimeslotKeys()
  {
    for (Map.Entry<Integer, Map<Integer, Pair<Double, Double>>> entry : submittedBidInformationbyExecutionTimeslot.entrySet())
    {
      for(Map.Entry<Integer, Pair<Double, Double>> message : entry.getValue().entrySet())
      {
        System.out.println("Execution Timeslot : " + entry.getKey() + ", Message Timeslot " + message.getKey() + ", Bid Price : " + message.getValue().getKey() + ", Bid Quantity : " + message.getValue().getValue() + "\n");
      }
    }
  }

  /**
   * @to String method
   */

  @Override
  public String toString()
  {
    String str = "\nSubmitted Bid Information\n";

    for (Map.Entry<Integer, Map<Integer, Pair<Double, Double>>> entry : submittedBidInformationbyMessageTimeslot.entrySet())
    {
      for(Map.Entry<Integer, Pair<Double, Double>> message : entry.getValue().entrySet())
      {
        str += "Message Timeslot : " + entry.getKey() + ", Execution Timeslot " + message.getKey() + ", Bid Price : " + message.getValue().getKey() + ", Bid Quantity : " + message.getValue().getValue() + "\n";
      }
    }

    for (Map.Entry<Integer, Map<Integer, Pair<Double, Double>>> entry : submittedBidInformationbyExecutionTimeslot.entrySet())
    {
      for(Map.Entry<Integer, Pair<Double, Double>> message : entry.getValue().entrySet())
      {
        str += "Execution Timeslot : " + entry.getKey() + ", Message Timeslot " + message.getKey() + ", Bid Price : " + message.getValue().getKey() + ", Bid Quantity : " + message.getValue().getValue() + "\n";
      }
    }

    return str;
  }
}
