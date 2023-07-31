package org.powertac.samplebroker.information;

import java.util.Map;
import java.util.List;
import javafx.util.Pair;
import java.util.HashMap;
import java.util.ArrayList;

public class SubmittedBidInformation
{
  Map<Integer, Map<Integer, List<Pair<Double , Double>>>> submittedBidInformationbyMessageTimeslot;
  Map<Integer, Map<Integer, List<Pair<Double , Double>>>> submittedBidInformationbyExecutionTimeslot;

  public SubmittedBidInformation()
  {
      submittedBidInformationbyMessageTimeslot = new HashMap<>();
      submittedBidInformationbyExecutionTimeslot = new HashMap<>();
  }

  public void setSubmittedBidInformationbyMessageTimeslot(Integer messageTime, Integer executionTime, Double price, Double quantity)
  {
      Map<Integer, List<Pair<Double, Double>>> SBMMap = submittedBidInformationbyMessageTimeslot.get(messageTime);

      if(SBMMap == null)
      {
          SBMMap = new HashMap<>();
      }

      List<Pair<Double, Double>> SBMList = SBMMap.get(executionTime);

      if(SBMList == null)
      {
          SBMList = new ArrayList<>();
      }

      SBMList.add(new Pair<Double, Double>(price, quantity));
      SBMMap.put(executionTime, SBMList);
      submittedBidInformationbyMessageTimeslot.put(messageTime, SBMMap);
  }

  public Map<Integer, List<Pair<Double, Double>>> getSubmittedBidInformationbyMessageTimeslot(Integer timeslot)
  {
      return submittedBidInformationbyMessageTimeslot.get(timeslot);
  }

  public Map<Integer, Map<Integer, List<Pair<Double, Double>>>> getSubmittedBidInformationbyMessageTimeslot()
  {
      return submittedBidInformationbyMessageTimeslot;
  }

  public void setSubmittedBidInformationbyExecutionTimeslot(Integer executionTime, Integer messageTime, Double price, Double quantity)
  {
      Map<Integer, List<Pair<Double, Double>>> SBMMap  = submittedBidInformationbyExecutionTimeslot.get(executionTime);

      if(SBMMap == null)
      {
          SBMMap = new HashMap<>();
      }

      List<Pair<Double, Double>> SBMList  = SBMMap.get(messageTime);

      if(SBMList == null)
      {
          SBMList = new ArrayList<>();
      }

      SBMList.add(new Pair<Double, Double>(price, quantity));
      SBMMap.put(messageTime, SBMList);
      submittedBidInformationbyExecutionTimeslot.put(executionTime, SBMMap);
  }

  public Map<Integer, List<Pair<Double, Double>>> getSubmittedBidInformationbyExecutionTimeslot(Integer timeslot)
  {
      return submittedBidInformationbyExecutionTimeslot.get(timeslot);
  }

  public Map<Integer, Map<Integer, List<Pair<Double, Double>>>> getSubmittedBidInformationbyExecutionTimeslot()
  {
      return submittedBidInformationbyExecutionTimeslot;
  }

  /**
   * @to String method
   */

  @Override
  public String toString()
  {
    String str = "\nSubmitted Bid Information\n";

    for (Map.Entry<Integer, Map<Integer, List<Pair<Double, Double>>>> entry : submittedBidInformationbyMessageTimeslot.entrySet())
    {
      for(Map.Entry<Integer, List<Pair<Double, Double>>> message : entry.getValue().entrySet())
      {
        for(Pair<Double, Double> item : message.getValue())
        {
            str += "Message Timeslot : " + entry.getKey() + ", Execution Timeslot " + message.getKey() + ", Bid Price : " + item.getKey() + ", Bid Quantity : " + item.getValue() + "\n";
        }
      }
    }

    for (Map.Entry<Integer, Map<Integer, List<Pair<Double, Double>>>> entry : submittedBidInformationbyExecutionTimeslot.entrySet())
    {
      for(Map.Entry<Integer, List<Pair<Double, Double>>> message : entry.getValue().entrySet())
      {
        for(Pair<Double, Double> item : message.getValue())
        {
          str += "Execution Timeslot : " + entry.getKey() + ", Message Timeslot " + message.getKey() + ", Bid Price : " + item.getKey() + ", Bid Quantity : " + item.getValue() + "\n";
        }
      }
    }

    return str;
  }
}
