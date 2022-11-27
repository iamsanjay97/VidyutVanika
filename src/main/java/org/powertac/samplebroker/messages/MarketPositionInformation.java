package org.powertac.samplebroker.messages;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@code MarketPosition} domain instance represents the current position of a
 * single broker for wholesale power in a given timeslot. The evolution of this
 * position over time is represented by the sequence of MarketTransaction instances
 * for this broker and timeslot. These are created by the AccountingService and
 * communicated to individual brokers after the market clears in each timeslot.
 *
 **/

public class MarketPositionInformation
{
    private Map<Integer, Double> marketPosition;

    public MarketPositionInformation()
    {
        marketPosition = new LinkedHashMap<>();
    }


    public void setMarketPosition(int timeslot,  Double balance)
    {
        marketPosition.put(timeslot, balance);
    }

    public Double getMarketPosition(int timeslot)
    {
        return this.marketPosition.get(timeslot);
    }

    public Map<Integer, Double> getMarketPosition()
    {
        return this.marketPosition;
    }

    /**
     * @to String method
     */

    @Override
    public String toString()
    {
      String str = "\nMarket Position Information\n";

      for(Map.Entry<Integer, Double> entry : marketPosition.entrySet())
      {
          str += "Message Timeslot : " + entry.getKey() + ", Market Position " + entry.getValue() + "\n";
      }

      return str;
    }
}
