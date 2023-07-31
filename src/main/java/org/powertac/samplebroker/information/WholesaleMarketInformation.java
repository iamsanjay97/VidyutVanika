package org.powertac.samplebroker.information;

import java.util.Map;
import java.util.HashMap;
import javafx.util.Pair;

public class WholesaleMarketInformation
{
  private Map<Integer, Pair<Double, Double>> avgMCPMap;
  private Map<Integer, Double> totalClearedQuantityMap;
  private Double totalValue = 0.0;
  private Double totalUsage = 0.0;
  private Double defaultMCP = 40.0;

  public WholesaleMarketInformation()
  {
    avgMCPMap = new HashMap<>();
    totalClearedQuantityMap = new HashMap<>();
  }

  public void setAvgMCP(Integer timeslot, Double MCP, Double clearedQuantity)
  {
    if(avgMCPMap.get(timeslot) == null)
      avgMCPMap.put(timeslot, new Pair<Double, Double>(0.0, 0.0));

    Double avgMCP = ((avgMCPMap.get(timeslot).getValue() * avgMCPMap.get(timeslot).getKey()) + (MCP * clearedQuantity)) / (avgMCPMap.get(timeslot).getKey() + clearedQuantity);
    avgMCPMap.put(timeslot, new Pair<Double, Double> (avgMCPMap.get(timeslot).getKey() + clearedQuantity, avgMCP));
  }

  public Double getAvgMCP(Integer timeslot)
  {
    if( avgMCPMap.get(timeslot) != null)
      return avgMCPMap.get(timeslot).getValue();
    else
      return defaultMCP;
  }

  public void setTotalClearedQuantity(Integer timeslot, Double clearedQuantity)
  {
    if(totalClearedQuantityMap.get(timeslot) == null)
      totalClearedQuantityMap.put(timeslot, clearedQuantity);
    else
    {
      totalClearedQuantityMap.put(timeslot, totalClearedQuantityMap.get(timeslot) + clearedQuantity);
    }
  }

  public Double getTotalClearedQuantity(Integer timeslot)
  {
    if(totalClearedQuantityMap.get(timeslot) != null)
      return totalClearedQuantityMap.get(timeslot);
    else
      return 0.0;
  }

  public void setMeanMarketPrice(Double MCP, Double quantity)
  {
    totalUsage += quantity;
    totalValue += MCP * quantity;
  }

  public Double getMeanMarketPrice()
  {
    if(totalUsage != 0.0)
      return Math.abs(totalValue/totalUsage);
    else
      return defaultMCP;
  }
}
