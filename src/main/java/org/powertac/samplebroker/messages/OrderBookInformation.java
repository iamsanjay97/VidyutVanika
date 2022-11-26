package org.powertac.samplebroker.messages;

import java.util.Map;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.HashMap;
import javafx.util.Pair;

import org.powertac.common.OrderbookOrder;

public class OrderBookInformation {

    public class OrderBookMessage
    {
        public Integer executionTimeslot;
        public Double clearedPrice;
        public SortedSet<OrderbookOrder> bids;
        public SortedSet<OrderbookOrder> asks;

        public OrderBookMessage(Integer executionTimeslot, Double clearedPrice, SortedSet<OrderbookOrder> bids, SortedSet<OrderbookOrder> asks)
        {
            this.executionTimeslot = executionTimeslot;
            this.clearedPrice = clearedPrice;
            this.bids = bids;
            this.asks = asks;
        }
    }

    private Map<Integer, ArrayList<OrderBookMessage>> orderBookMap;
    private Map<Integer, Map<Integer, Pair<Double, Double>>> firstUnclearedBidInformation;
    private Map<Integer, Map<Integer, Pair<Double, Double>>> firstUnclearedAskInformation;

    public OrderBookInformation()
    {
      orderBookMap = new HashMap<>();
      firstUnclearedBidInformation = new HashMap<>();
      firstUnclearedAskInformation = new HashMap<>();
    }

    public void setFirstUnclearedBidInformation(Integer messageTime, Integer executionTime, Double price, Double quantity)
    {
        Map<Integer, Pair<Double, Double>> FUBMap  = firstUnclearedBidInformation.get(messageTime);
        if(FUBMap == null)
        {
            FUBMap = new HashMap<>();
        }

        FUBMap.put(executionTime, new Pair<Double, Double>(price, quantity));

        firstUnclearedBidInformation.put(messageTime, FUBMap);
    }

    public Map<Integer, Pair<Double, Double>> getFirstUnclearedBidInformation(int messageTimeslot)
    {
       return firstUnclearedBidInformation.get(messageTimeslot);
    }

    public Double getFirstUnclearedBid(int messageTimeslot, int proximity)
    {
      try
      {
          return firstUnclearedBidInformation.get(messageTimeslot).get(messageTimeslot + proximity - 1).getKey();
      }
      catch(Exception e)
      {
        while((messageTimeslot >= 362) && (firstUnclearedBidInformation.get(messageTimeslot).get(messageTimeslot + proximity - 1) == null))
        {
          messageTimeslot--;
        }

        if(messageTimeslot>= 362)
          return firstUnclearedBidInformation.get(messageTimeslot).get(messageTimeslot + proximity - 1).getKey();
        else
          return 0.0;
      }
    }

    public Double getFirstUnclearedBidPrev(int futureTimeslot, int prevTimeslot)
    {
      try
      {
          return firstUnclearedBidInformation.get(prevTimeslot).get(futureTimeslot).getKey();
      }
      catch(Exception e)
      {
        while((prevTimeslot >= 361) && ((futureTimeslot - prevTimeslot) < 24) && (firstUnclearedBidInformation.get(prevTimeslot).get(futureTimeslot) == null))
        {
          prevTimeslot--;
        }

        if((prevTimeslot >= 361) && ((futureTimeslot - prevTimeslot) < 24))
          return firstUnclearedBidInformation.get(prevTimeslot).get(futureTimeslot).getKey();
        else
          return 0.0;
      }
    }

    public Map<Integer, Map<Integer, Pair<Double, Double>>> getFirstUnclearedBidInformation()
    {
        return firstUnclearedBidInformation;
    }

    public void setFirstUnclearedAskInformation(Integer messageTime, Integer executionTime, Double price, Double quantity)
    {
        Map<Integer, Pair<Double, Double>> FUAMap  = firstUnclearedAskInformation.get(messageTime);
        if(FUAMap == null)
        {
            FUAMap = new HashMap<>();
        }

        FUAMap.put(executionTime, new Pair<Double, Double>(price, quantity));

        firstUnclearedAskInformation.put(messageTime, FUAMap);
    }

    public Map<Integer, Pair<Double, Double>> getFirstUnclearedAskInformation(int messageTimeslot)
    {
       return firstUnclearedAskInformation.get(messageTimeslot);
    }

    public Double getFirstUnclearedAsk(int messageTimeslot, int proximity)
    {
      try
      {
          return firstUnclearedAskInformation.get(messageTimeslot).get(messageTimeslot + proximity - 1).getKey();
      }
      catch(Exception e)
      {
        while((messageTimeslot >= 362) && (firstUnclearedAskInformation.get(messageTimeslot).get(messageTimeslot + proximity - 1) == null))
        {
          messageTimeslot--;
        }

        if(messageTimeslot>= 362)
          return firstUnclearedAskInformation.get(messageTimeslot).get(messageTimeslot + proximity - 1).getKey();
        else
          return 70.0;
      }
    }

    public Double getFirstUnclearedAskPrev(int futureTimeslot, int prevTimeslot)
    {
      try
      {
          return firstUnclearedAskInformation.get(prevTimeslot).get(futureTimeslot).getKey();
      }
      catch(Exception e)
      {
        while((prevTimeslot >= 361) && ((futureTimeslot - prevTimeslot) < 24) && (firstUnclearedAskInformation.get(prevTimeslot).get(futureTimeslot) == null))
        {
          prevTimeslot--;
        }

        if((prevTimeslot >= 361) && ((futureTimeslot - prevTimeslot) < 24))
          return firstUnclearedAskInformation.get(prevTimeslot).get(futureTimeslot).getKey();
        else
          return 0.0;
      }
    }

    public Map<Integer, Map<Integer, Pair<Double, Double>>> getFirstUnclearedAskInformation()
    {
        return firstUnclearedAskInformation;
    }

    public void setOrderBookInformationbyMessageTimeslot(Integer messageTimeslot, Integer executionTimeslot, Double clearedPrice, SortedSet<OrderbookOrder> bids, SortedSet<OrderbookOrder> asks)
    {
        ArrayList<OrderBookMessage> orders = orderBookMap.get(messageTimeslot);

        if(orders == null)
            orders = new ArrayList<OrderBookMessage>();

        orders.add(new OrderBookMessage(executionTimeslot, clearedPrice, bids, asks));
        orderBookMap.put(messageTimeslot, orders);
    }

    public ArrayList<OrderBookMessage> getOrderBookInformationbyMessageTimeslot(Integer messageTimeslot)
    {
        return orderBookMap.get(messageTimeslot);
    }
}
