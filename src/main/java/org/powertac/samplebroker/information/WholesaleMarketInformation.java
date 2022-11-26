package org.powertac.samplebroker.information;

import java.util.Map;
import java.util.HashMap;
import javafx.util.Pair;

public class WholesaleMarketInformation
{
  private Map<Integer, Pair<Integer, Double>> avgMCPMap;
  private Map<Integer, Double> totalClearedQuantityMap;
  private Map<Integer, Double> wholesaleMarketCostMap;
  private Map<Integer, Double> wholesaleMarketOrderMap;

  public WholesaleMarketInformation()
  {
    avgMCPMap = new HashMap<>();
    totalClearedQuantityMap = new HashMap<>();
    wholesaleMarketCostMap = new HashMap<>();
  wholesaleMarketOrderMap = new HashMap<>();
  }

  public void setAvgMCP(Integer timeslot, Double MCP)
  {
    if(avgMCPMap.get(timeslot) == null)
      avgMCPMap.put(timeslot, new Pair<Integer, Double>(0, 0.0));

    Double avgMCP = (avgMCPMap.get(timeslot).getValue() * avgMCPMap.get(timeslot).getKey() + MCP) / (avgMCPMap.get(timeslot).getKey() + 1);
    avgMCPMap.put(timeslot, new Pair<Integer, Double> (avgMCPMap.get(timeslot).getKey() + 1, avgMCP));
  }

  public Double getAvgMCP(Integer timeslot)
  {
    return avgMCPMap.get(timeslot).getValue();
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
    return totalClearedQuantityMap.get(timeslot);
  }

  public void setWholesaleMarketCostMap(Integer timeslot, Double cost)
  {
    Double prevCost = wholesaleMarketCostMap.get(timeslot);

    if(prevCost == null)
      prevCost = 0.0;

    wholesaleMarketCostMap.put(timeslot, prevCost + cost);
  }

  public Double getWholesaleMarketCostMap(Integer timeslot)
  {
    return wholesaleMarketCostMap.get(timeslot);
  }

  public Double getCumulativeWholesaleMarketCostMap()
  {
    double cumulativeCost = 0.0;

    for(Double cost : wholesaleMarketCostMap.values())
    {
      cumulativeCost += cost;
    }

    return cumulativeCost;
  }

  public void setWholesaleMarketOrderMap(Integer timeslot, Double quantity)
  {
    wholesaleMarketOrderMap.put(timeslot, quantity);
  }

  public Double getWholesaleMarketOrderMap(Integer timeslot)
  {
    return wholesaleMarketOrderMap.get(timeslot);
  }

  public Double getAvgWholesaleMarketOrderMap()
  {
    double avgQuantity = 0.0;

    for(Double qua : wholesaleMarketOrderMap.values())
    {
      avgQuantity += qua;
    }

    return avgQuantity;
  }
}
