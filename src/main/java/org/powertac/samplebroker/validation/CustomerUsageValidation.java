package org.powertac.samplebroker.validation;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class CustomerUsageValidation
{
  public class CollectUsage
  {
    public Double actual;
    public List<Double> predictions;

    public CollectUsage()
    {
      this.actual = null;
      this.predictions = new ArrayList<>();
    }

    public void updateActualUsage(Double usage)
    {
      this.actual = usage;
    }

    public void updatePredictions(Double usage)
    {
      this.predictions.add(usage);
    }

    public String toString()
    {
      String out = "";

      for(Double item : predictions)
        out += item + ",";

      out += this.actual;

      return out;
    }
  }

  private Map<String, Map<Integer, CollectUsage>> customerUsageValidationMap;
  private Map<Integer, CollectUsage> netUsageValidationMap;

  public CustomerUsageValidation()
  {
    customerUsageValidationMap = new LinkedHashMap<>();
    netUsageValidationMap = new LinkedHashMap<>();
  }

  public void updateCustomerUsageMap(String customer, Integer timeslot, Double usage, boolean flag)          // flag is true for actualUsage updation
  {
    Map<Integer, CollectUsage> CUVM = customerUsageValidationMap.get(customer);

    if(CUVM == null)
    {
      CUVM = new LinkedHashMap<>();
    }

    CollectUsage CU = CUVM.get(timeslot);

    if(CU == null)
    {
      CU = new CollectUsage();
    }

    if(flag)
      CU.updateActualUsage(usage);
    else
      CU.updatePredictions(usage);

    CUVM.put(timeslot, CU);
    customerUsageValidationMap.put(customer, CUVM);
  }

  public void updateNetUsageMap(Integer timeslot, Double usage, boolean flag)          // flag is true for actualUsage updation
  {
    CollectUsage CUVM = netUsageValidationMap.get(timeslot);

    if(CUVM == null)
    {
      CUVM = new CollectUsage();
    }

    if(flag)
      CUVM.updateActualUsage(usage);
    else
      CUVM.updatePredictions(usage);

    netUsageValidationMap.put(timeslot, CUVM);
  }

  public void printToFile(String bootFile, ArrayList<String> brokers)
  {
    for(Map.Entry<String, Map<Integer, CollectUsage>> outerItem : customerUsageValidationMap.entrySet())
    {
      try
      {
        String customer = outerItem.getKey();
        Map<Integer, CollectUsage> oItems = outerItem.getValue();

        try
        {
          FileWriter fr = new FileWriter(new File(customer + "_Usage_Validation.csv"), true);
          BufferedWriter br = new BufferedWriter(fr);

          br.write("\n\n" + bootFile + "\n" + brokers + "\n");

          for(Map.Entry<Integer, CollectUsage> innerItem : oItems.entrySet())
          {
            Integer timeslot = innerItem.getKey();
            CollectUsage usageData = innerItem.getValue();

            br.write(timeslot + "," + usageData.toString() + "\n");
          }

          br.close();
          fr.close();
        }
        catch(Exception e)
        {
          e.printStackTrace();
        }
      }
      catch(Exception e){}
    }

    try
    {
      FileWriter fr = new FileWriter(new File("Net_Usage_Validation.csv"), true);
      BufferedWriter br = new BufferedWriter(fr);

      br.write("\n\n" + bootFile + "\n" + brokers + "\n");

      for(Map.Entry<Integer, CollectUsage> outerItem : netUsageValidationMap.entrySet())
      {
        Integer timeslot = outerItem.getKey();
        CollectUsage usageData = outerItem.getValue();

        br.write(timeslot + "," + usageData.toString() + "\n");
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
