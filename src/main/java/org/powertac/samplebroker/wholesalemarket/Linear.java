package org.powertac.samplebroker.wholesalemarket;

import org.powertac.common.Order;
import org.powertac.common.Competition;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;

public class Linear extends Strategies
{
  private double step = 2.0;

  private static Linear instance = null;

  private Linear(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
  }

  public static Linear getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    if(instance == null)
    {
      instance = new Linear(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
    }
    return instance;
  }

  public Double computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    double amountNeeded = amount[0];

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());

    if (remainingTries > 0)
    {
      double startPrice = 0.0;

      if(amountNeeded > 0.0)
      {
        //Buyer
        startPrice = buyLimitPriceMax;

        return Math.max(buyLimitPriceMin, (startPrice - step * (24 - remainingTries)));
      }
      else
      {
        //Seller
        startPrice = sellLimitPriceMax;

        return Math.max(sellLimitPriceMin, (startPrice - step * (24 - remainingTries)));
      }
    }
    else
      return null;  // market order
  }

  public Order submitBid(int timeslot, double neededMWh, Double limitPrice)
  {
    return new Order(this.broker.getBroker(), timeslot, neededMWh, limitPrice);
  }
}
