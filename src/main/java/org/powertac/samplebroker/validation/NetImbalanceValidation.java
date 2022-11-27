package org.powertac.samplebroker.validation;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class NetImbalanceValidation
{
  public class CollectImbalance
  {
    public Double actual;
    public List<Double> predictions;

    public CollectImbalance()
    {
      this.actual = null;
      this.predictions = new ArrayList<>();
    }

    public void updateActualImbalance(Double imbalance)
    {
      this.actual = imbalance;
    }

    public void updatePredictions(Double imbalance)
    {
      this.predictions.add(imbalance);
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

  private Map<Integer, CollectImbalance> netImbalanceValidationMap;

  public NetImbalanceValidation()
  {
    netImbalanceValidationMap = new LinkedHashMap<>();
  }

  public void updateImbalanceMap(Integer timeslot, Double imbalance, boolean flag)          // flag is true for actualUsage updation
  {
    CollectImbalance CI = netImbalanceValidationMap.get(timeslot);

    if(CI == null)
    {
      CI = new CollectImbalance();
    }

    if(flag)
      CI.updateActualImbalance(imbalance);
    else
      CI.updatePredictions(imbalance);

    netImbalanceValidationMap.put(timeslot, CI);
  }

  public void printToFile(String bootFile, ArrayList<String> brokers)
  {
    try
    {
      FileWriter fr = new FileWriter(new File("Net_Imbalance_Validation.csv"), true);
      BufferedWriter br = new BufferedWriter(fr);

      br.write("\n\n" + bootFile + "\n" + brokers + "\n");

      for(Map.Entry<Integer, CollectImbalance> outerItem : netImbalanceValidationMap.entrySet())
      {
        Integer timeslot = outerItem.getKey();
        CollectImbalance netImbalanceData = outerItem.getValue();

        br.write(timeslot + "," + netImbalanceData.toString() + "\n");
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
