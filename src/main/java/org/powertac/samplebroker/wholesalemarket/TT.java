package org.powertac.samplebroker.wholesalemarket;

import org.powertac.common.Order;
import org.powertac.common.Competition;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;

import org.powertac.samplebroker.messages.BalancingMarketInformation;

public class TT extends Strategies
{
  private BalancingMarketInformation BMS;

  private static TT instance = null;

  private TT(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);

    BMS = this.messageManager.getBalancingMarketInformation();
  }

  public static TT getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    if(instance == null)
    {
      instance = new TT(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
    }
    return instance;
  }

  /*public Double computeQuantity()
  {

  }*/

  public Double computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    double amountNeeded = amount[0];

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());

    if (remainingTries > 0)
    {
      if(amountNeeded > 0.0)
        return Math.max(-this.BMS.getAvgBalancingPrice(), buyLimitPriceMin);
      else
        return Math.min(this.BMS.getAvgBalancingPrice(), sellLimitPriceMax);
    }
    else
      return null; // market order
  }

  public Order submitBid(int timeslot, double neededMWh, Double limitPrice)
  {
    return new Order(this.broker.getBroker(), timeslot, neededMWh, limitPrice);
  }
}
