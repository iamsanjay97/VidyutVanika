package org.powertac.samplebroker.wholesalemarket;

import org.powertac.common.Order;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;

abstract public class Strategies
{
  BrokerContext broker;
  double buyLimitPriceMax;
  double buyLimitPriceMin;
  double sellLimitPriceMax;
  double sellLimitPriceMin;
  MessageManager messageManager;

  public Strategies(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    this.broker = broker;
    this.buyLimitPriceMax = buyLimitPriceMax;
    this.buyLimitPriceMin = buyLimitPriceMin;
    this.sellLimitPriceMax = sellLimitPriceMax;
    this.sellLimitPriceMin = sellLimitPriceMin;
    this.messageManager = messageManager;
  }

  public Double computeQuantity(Integer timeslot, Integer currentTimeslot, Double amount)
  {
      System.out.println("Handled in MarketManagerService !");
      return null;
  }

  abstract public Double computeLimitPrice(int timeslot, int currentTimeslot, double ...amount);

  abstract public Order submitBid(int timeslot, double neededMWh, Double limitPrice);
}
