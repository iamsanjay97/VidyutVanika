package org.powertac.samplebroker.wholesalemarket;

import java.util.Map;
import java.util.HashMap;
import javafx.util.Pair;

import org.powertac.common.Order;
import org.powertac.common.Competition;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;

public class AFP extends Strategies
{
  private static AFP instance = null;

  private Map<Integer, Pair<Integer, Double>> avgLimitPriceMap;

  private AFP(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);

    avgLimitPriceMap = new HashMap<>();
  }

  public static AFP getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    if(instance == null)
    {
      instance = new AFP(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
    }
    return instance;
  }

  public void setAvgLimitPriceMap(Integer proximity, Double clearingPrice)
  {
    Pair<Integer, Double> item = avgLimitPriceMap.get(proximity);

    if(item == null)
      item = new Pair<Integer, Double>(0, 0.0);

    Double avgPrice = (item.getKey() * item.getValue() + clearingPrice) / (item.getKey() + 1);

    avgLimitPriceMap.put(proximity, new Pair<Integer, Double>((item.getKey() + 1), avgPrice));
  }

  public Double getAvgLimitPriceMap(Integer proximity)
  {
    return avgLimitPriceMap.get(proximity).getValue();
  }

  public Double computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    Integer proximity = timeslot - currentTimeslot;
    double lp = getAvgLimitPriceMap(proximity);

    double amountNeeded = amount[0];

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());

    if (remainingTries > 0)
    {
      Double limitPrice = 0.0;

      if (amountNeeded > 0.0)
      {
        // buying
        limitPrice = -lp;
      }
      else
      {
        // selling
        limitPrice = lp;
      }
      return limitPrice;
    }
    else
      return null; // market order
  }

  public Double computeQuantity(Integer timeslot, Integer currentTimeslot, Double amountNeeded)
  {
    Integer proximity = timeslot - currentTimeslot;

    if(proximity == 1)
      return amountNeeded;

    Double sum = 0D;

    for(int i = 1; i <= proximity; i++)
    {
      if(amountNeeded > 0)
        sum += 1.0D/(getAvgLimitPriceMap(i));
      else
        sum += getAvgLimitPriceMap(i);
    }

    amountNeeded /= sum;

    if(amountNeeded > 0.0)
      amountNeeded /= (Double)getAvgLimitPriceMap(proximity);
    else
      amountNeeded *= (Double)getAvgLimitPriceMap(proximity);

    return amountNeeded;
  }

  public Order submitBid(int timeslot, double neededMWh, Double limitPrice)
  {
    return new Order(this.broker.getBroker(), timeslot, neededMWh, limitPrice);
  }
}
