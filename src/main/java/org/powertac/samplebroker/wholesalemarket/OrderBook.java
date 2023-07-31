package org.powertac.samplebroker.wholesalemarket;

import java.util.List;
import java.util.ArrayList;
import org.powertac.common.Order;
import org.powertac.samplebroker.helpers.MarketManagerInformation;
import org.powertac.samplebroker.interfaces.BrokerContext;

public class OrderBook
{
    Double buypricemin = -100.0;
    Double buypricemax = -10.0;
    Double sellpricemin = 10.0;
    Double sellpricemax = 100.0;
    Double minQuantity = 0.01;
    BrokerContext mybroker;
    MarketManagerInformation mminfo;

    public OrderBook(BrokerContext broker, MarketManagerInformation mminfo)
    { 
        this.mybroker = broker;
        this.mminfo = mminfo;
    }

    public List<Order> generateOrders (int delivery_timeslot, double neededMWh, int auctionsleft)
    {
      List<Order> brokerorders = new ArrayList<Order>();

      //bids ---> buying energy
      if (neededMWh>0.0)
      {
        if (auctionsleft==24) {
          return brokerorders;
        }
        Double lastaskprice;
        Double cpgenco_position;
        try {
          lastaskprice = mminfo.get_uncleared_ask_prices(delivery_timeslot).getDouble(auctionsleft);
          cpgenco_position = mminfo.get_wholesale_market_quantities(delivery_timeslot).getDouble(23);
        } 
        catch (Exception e) {
          e.printStackTrace();
          lastaskprice = 40.0;
          cpgenco_position = 500.0;
        }

        // auctions --> (3,4,5,6,...23)
        if (auctionsleft>2)
        {
          if (auctionsleft==23 && cpgenco_position>900){
          //   System.out.println("HIGH MISO DEMAND > 900");
          }
          Double minprice = -Math.max(sellpricemin+3.0, lastaskprice-4.0); //-0.85*lastaskprice; 
          Double maxprice = -(sellpricemin+3.0); //-0.45*lastaskprice;
          int numbids = Math.min((int)(neededMWh/(2.0*minQuantity)), 30) ;
          for (int i = 0; i < numbids; i++) 
          {
            Double limitprice = minprice + (i*(maxprice-minprice))/(numbids - 1 + 1e-5);
            Double limitquantity = neededMWh/(double)numbids; //(i==0) ? neededMWh - 2*minQuantity*(numbids-1) : 2*minQuantity;
            Order myorder = new Order(this.mybroker.getBroker(), delivery_timeslot, limitquantity, limitprice);
            brokerorders.add(myorder);
          }
        }
        // auctions --> (1,2)
        else if (auctionsleft<=2)
        {
          Double laststandprice = -(lastaskprice+3.0);
          Double laststandquantity = neededMWh;
          Order myorder = new Order(this.mybroker.getBroker(), delivery_timeslot, laststandquantity, laststandprice);
          brokerorders.add(myorder);
        }
      }

      //asks ---> selling energy
      else if (neededMWh < 0.0)
      {
        if (auctionsleft==1)
        {
          Double limitprice = null;
          Double limitquantity = neededMWh;
          Order myorder = new Order(this.mybroker.getBroker(), delivery_timeslot, limitquantity, limitprice);
          brokerorders.add(myorder);
        }
        // if (auctionsleft==24) {
        //   return brokerorders;
        // }
        // Double lastaskprice;
        // Double cpgenco_position;
        // try {
        //   lastaskprice = mminfo.get_uncleared_ask_prices(delivery_timeslot).getDouble(auctionsleft);
        //   cpgenco_position = mminfo.get_wholesale_market_quantities(delivery_timeslot).getDouble(23);
        // } 
        // catch (Exception e) {
        //   e.printStackTrace();
        //   lastaskprice = 40.0;
        //   cpgenco_position = 500.0;
        // }

        // // auctions --> (3,4,5,6,...23)
        // if (auctionsleft>2)
        // {
        //   if (auctionsleft==23 && cpgenco_position>900){
        //   //   System.out.println("HIGH MISO DEMAND > 900");
        //   }
        //   Double minprice = (sellpricemin+10.0);  
        //   Double maxprice = Math.max(sellpricemin+10.0, lastaskprice-4.0);
        //   int numbids = Math.min((int)(Math.abs(neededMWh)/(2.0*minQuantity)), 30) ;
        //   for (int i = 0; i < numbids; i++) 
        //   {
        //     Double limitprice = minprice + (i*(maxprice-minprice))/(numbids - 1 + 1e-5);
        //     Double limitquantity = neededMWh/(double)numbids;
        //     Order myorder = new Order(this.mybroker.getBroker(), delivery_timeslot, limitquantity, limitprice);
        //     brokerorders.add(myorder);
        //   }
        // }
        // // auctions --> (1,2)
        // else if (auctionsleft<=2)
        // {
        //   Double laststandprice = (lastaskprice+6.0);
        //   Double laststandquantity = neededMWh;
        //   Order myorder = new Order(this.mybroker.getBroker(), delivery_timeslot, laststandquantity, laststandprice);
        //   brokerorders.add(myorder);
        // }
      }

      return brokerorders;
    }
}
