package org.powertac.samplebroker.messages;

import java.util.LinkedHashMap;
import java.util.Map;

public class CashPositionInformation
{
    private Map<Integer, Double> cashPosition;
    private Map<Integer, Double> bankInterest;

    public CashPositionInformation()
    {
        cashPosition = new LinkedHashMap<>();
        bankInterest = new LinkedHashMap<>();
    }

    public void setCashPosition(Integer timeslot,Double balance)
    {
        cashPosition.put(timeslot, balance);
    }

    public Double getCashPosition(Integer timeslot)
    {
        return cashPosition.get(timeslot);
    }

    public void setBankInterest(Integer timeslot, Double amount)
    {
        bankInterest.put(timeslot, amount);
    }

    public Double getBankInterest(Integer timeslot)
    {
      if(bankInterest.get(timeslot) != null)
        return bankInterest.get(timeslot);
      else 
        return 0.0;
    }

    /**
     * @to String method
     */

    @Override
    public String toString()
    {
      String str = "\nCash Position Information\n";

      for (Map.Entry<Integer, Double> entry : cashPosition.entrySet())
      {
        str += "Message Timeslot : " + entry.getKey() + ", Cash Position " + entry.getValue() + "\n";
      }

      for (Map.Entry<Integer, Double> entry : bankInterest.entrySet())
      {
        str += "Message Timeslot : " + entry.getKey() + ", Bank Interest " + entry.getValue() + "\n";
      }

      return str;
    }
}
