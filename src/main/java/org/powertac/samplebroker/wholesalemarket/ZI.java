package org.powertac.samplebroker.wholesalemarket;

import org.powertac.common.Order;
import org.powertac.common.Competition;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;

public class ZI extends Strategies
{
  private static ZI instance = null;

  private ZI(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
  }

  public static ZI getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    if(instance == null)
    {
      instance = new ZI(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
    }
    return instance;
  }

  public Double computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    double amountNeeded = amount[0];

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());

    if (remainingTries > 0)
    {
      double minPrice = 0.0;
      double maxPrice = 0.0;

      if (amountNeeded > 0.0)
      {
        // buying
        maxPrice = this.buyLimitPriceMax;
        minPrice = this.buyLimitPriceMin;
      }
      else
      {
        // selling
        maxPrice = this.sellLimitPriceMax;
        minPrice = this.sellLimitPriceMin;
      }
      return (minPrice + Math.random()*(maxPrice - minPrice));
    }
    else
      return null; // market order
  }

  public Order submitBid(int timeslot, double neededMWh, Double limitPrice)
  {
    return new Order(this.broker.getBroker(), timeslot, neededMWh, limitPrice);
  }
}
