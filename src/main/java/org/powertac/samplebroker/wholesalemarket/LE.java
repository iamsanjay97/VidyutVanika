package org.powertac.samplebroker.wholesalemarket;

import java.util.HashMap;
import java.util.Random;

import org.powertac.common.Order;
import org.powertac.common.Competition;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;
import org.powertac.samplebroker.information.SubmittedBidInformation;
import org.powertac.samplebroker.wholesalemarket.Eagerness;

public class LE extends Strategies
{
  private Eagerness eagerness;

  private HashMap<Integer, Double> prevBuyPrice;
  private HashMap<Integer, Double> prevSellPrice;

  private double step = 10.0;

  private static LE instance = null;
  private Random rand = new Random();

  private LE(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager, SubmittedBidInformation submittedBidInformation)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);

    prevBuyPrice = new HashMap<>();
    prevSellPrice = new HashMap<>();

    eagerness = Eagerness.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager, submittedBidInformation);
  }

  public static LE getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager, SubmittedBidInformation submittedBidInformation)
  {
    if(instance == null)
    {
      instance = new LE(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager, submittedBidInformation);
    }
    return instance;
  }

  /*public Double computeQuantity()
  {

  }*/

  public Double computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    double amountNeeded = amount[0];
    double totalAmountNeeded = amount[1];

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());

    Double bid = null;

    if (remainingTries > 0)
    {
      double E = eagerness.computeEagerness(timeslot, amountNeeded, totalAmountNeeded, currentTimeslot);

      if(amountNeeded > 0.0)
      {
        //Buyer
        Double buyPrice = prevBuyPrice.get(timeslot);

        if(buyPrice == null)
          buyPrice = buyLimitPriceMin*rand.nextDouble();

        bid = Math.max(buyLimitPriceMin, (buyPrice - step * (1 - E)));

        prevBuyPrice.put(timeslot, bid);
      }
      else
      {
        //Seller
        Double sellPrice = prevSellPrice.get(timeslot);

        if(sellPrice == null)
          sellPrice = sellLimitPriceMax*rand.nextDouble();

        bid = Math.max(sellLimitPriceMin, (sellPrice - step * (1 - E)));

        prevSellPrice.put(timeslot, bid);
      }
      return bid;
    }
    else
      return null;  // market order
  }

  public Order submitBid(int timeslot, double neededMWh, Double limitPrice)
  {
    return new Order(this.broker.getBroker(), timeslot, neededMWh, limitPrice);
  }
}
