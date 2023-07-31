package org.powertac.samplebroker.util;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;

public class HelperForNormalization
{
  private Double proximityRecordX_min = Double.MAX_VALUE;              
  private Double proximityRecordX_max = -Double.MAX_VALUE;

  private Double balancingPriceRecordX_min = Double.MAX_VALUE;            
  private Double balancingPriceRecordX_max = -Double.MAX_VALUE;

  private Double quantityRecordX_min = Double.MAX_VALUE;              
  private Double quantityRecordX_max = -Double.MAX_VALUE;

  public HelperForNormalization()
  {
    try
    {
      System.out.println("Load Normalization Info");

      File file = new File("Normalization_Data.txt");
      BufferedReader br = new BufferedReader(new FileReader(file));

      proximityRecordX_min = Double.parseDouble(br.readLine());
      proximityRecordX_max = Double.parseDouble(br.readLine());

      balancingPriceRecordX_min = Double.parseDouble(br.readLine());
      balancingPriceRecordX_max = Double.parseDouble(br.readLine());

      quantityRecordX_min = Double.parseDouble(br.readLine());
      quantityRecordX_max = Double.parseDouble(br.readLine());

      br.close();
    }
    catch(IOException e)
    {
      System.out.println("Error loading Normalization Info");
    }
  }   
  
  public void addToProximityRecord(Integer proximity)
  {
    proximityRecordX_min = Math.min(proximityRecordX_min, proximity);              
    proximityRecordX_max = Math.max(proximityRecordX_max, proximity);
  }

  public void addToBalancingPriceRecord(Double balancingPrice)
  {
    balancingPriceRecordX_min = Math.min(balancingPriceRecordX_min, balancingPrice);              
    balancingPriceRecordX_max = Math.max(balancingPriceRecordX_max, balancingPrice);
  }

  public void addToQuantityRecord(Double quantity)
  {
    quantityRecordX_min = Math.min(quantityRecordX_min, quantity);              
    quantityRecordX_max = Math.max(quantityRecordX_max, quantity);
  }

	public Double normalize(double data, String flag)
	{
    switch(flag)
    {
      case "Proximity": 
                      return ((data - proximityRecordX_min) / (proximityRecordX_max - proximityRecordX_min));

      case "BalancingPrice":
                      return ((data - balancingPriceRecordX_min) / (balancingPriceRecordX_max - balancingPriceRecordX_min));

      case "Quantity":
                      return ((data - quantityRecordX_min) / (quantityRecordX_max - quantityRecordX_min));
    }
    return null;
	}

  public void storeToFile()
  {
    try
    {
      FileWriter fw = new FileWriter("Normalization_Data.txt", false);
      fw.write(proximityRecordX_min + "\n" + proximityRecordX_max + "\n");
      fw.write(balancingPriceRecordX_min + "\n" + balancingPriceRecordX_max + "\n");
      fw.write(quantityRecordX_min + "\n" + quantityRecordX_max + "\n");
      fw.close();
    }
    catch(Exception e){}
  }

  public void printAll()
  {
    System.out.println(proximityRecordX_min + " :: " + proximityRecordX_max + " :: " +
                       balancingPriceRecordX_min + " :: " + balancingPriceRecordX_max + " :: " + 
                       quantityRecordX_min + " :: " + quantityRecordX_max);
  }
}
