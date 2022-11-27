package org.powertac.samplebroker.wholesalemarket;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import javafx.util.Pair;
import java.io.IOException;

import org.powertac.common.Order;
import org.powertac.common.Competition;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;
import org.powertac.samplebroker.messages.MarketPositionInformation;
import org.powertac.samplebroker.messages.MarketTransactionInformation;
import org.powertac.samplebroker.information.SubmittedBidInformation;

public class Eagerness extends Strategies
{
  private SubmittedBidInformation SBI;
  private MarketPositionInformation MPI;
  private MarketTransactionInformation MTI;

  private static Eagerness instance = null;
  private Random rand = new Random();

  private Eagerness(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager, SubmittedBidInformation submittedBidInformation)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);

    SBI = submittedBidInformation;
    MPI = this.messageManager.getMarketPositionInformation();
    MTI = this.messageManager.getMarketTransactionInformation();
  }

  public static Eagerness getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager, SubmittedBidInformation submittedBidInformation)
  {
    if(instance == null)
    {
      instance = new Eagerness(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager, submittedBidInformation);
    }
    return instance;
  }

  /*public Double computeQuantity()
  {

  }*/

  public double computeEagerness(int timeslot, double amountNeeded, double totalAmountNeeded, int currentTimeslot)
  {
    Double amountBought = MPI.getMarketPosition(timeslot);

    if(amountBought == null)
      amountBought = 0.0;

    Map<Integer, Pair<Double , Double>> list1 = null;
    Map<Integer, Pair<Double , Double>> list2 = null;

    try
    {
      list1 = SBI.getSubmittedBidInformationbyExecutionTimeslot(timeslot);
      list2 = MTI.getMarketTransactionInformationbyExectionTimeslot(timeslot);
    }
    catch(Exception e)
    {
      //e.printStackTrace();
    }

    HashSet<Integer> submittedBids = new HashSet<Integer>();
    HashSet<Integer> clearedBids = new HashSet<Integer>();

    if(list1 != null)
    {
      for(Integer item : list1.keySet())
      {
        //if(tb.clearedPrice <= 0.0)
        //{
          submittedBids.add(item);
        //}
      }
    }

    if(list2 != null)
    {
      for(Integer item: list2.keySet())
      {
        if(submittedBids.contains(item-1))
          clearedBids.add(item-1);
      }
    }

    double bidClearedCount;

    if(submittedBids.size() != 0)
      bidClearedCount = clearedBids.size()*1.0 / submittedBids.size();
    else
      bidClearedCount = 1.0;

    double alpha = 0.5;

    int proximity = timeslot - currentTimeslot;

    Double E = rand.nextDouble();

    if (amountNeeded > 0.0)
    {
      //buying
      if(totalAmountNeeded != 0.0)
      {
        E = alpha*(proximity*1.0/24) + (1 - alpha)*(bidClearedCount * amountBought / totalAmountNeeded);       // Modified Equation
      }
    }
    else
    {
      //selling
      if(totalAmountNeeded != 0.0)
        E = 1 - (alpha*(proximity*1.0/24) + (1 - alpha)*(bidClearedCount));       // Modified Equation
    }
    return E;
  }

  public Double computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    double amountNeeded = amount[0];
    double totalAmountNeeded = amount[1];

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());

    if (remainingTries > 0)
    {
      double minPrice = 0.0;
      double maxPrice = 0.0;
      double E = computeEagerness(timeslot, amountNeeded, totalAmountNeeded, currentTimeslot);

      if (amountNeeded > 0.0)
      {
        //buying
         minPrice = buyLimitPriceMin;
         maxPrice = buyLimitPriceMax;
      }
      else
      {
        //selling
        maxPrice = sellLimitPriceMax;
        minPrice = sellLimitPriceMin;
      }
      return (minPrice - E*(minPrice - maxPrice));
    }
    else
      return null;  // market order
  }

  public Order submitBid(int timeslot, double neededMWh, Double limitPrice)
  {
    return new Order(this.broker.getBroker(), timeslot, neededMWh, limitPrice);
  }
}
