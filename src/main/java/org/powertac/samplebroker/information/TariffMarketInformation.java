package org.powertac.samplebroker.information;

import java.util.Map;
import javafx.util.Pair;
import java.util.HashMap;

import org.powertac.common.TariffSpecification;

public class TariffMarketInformation
{
  private Map<Integer, Double> marketShareVolumeC;
  private Map<Integer, Double> marketShareVolumeP;
  private Map<Integer, Map<TariffSpecification, Pair<Double, Double>>> brokerTariffRepo;

  public TariffMarketInformation()
  {
      marketShareVolumeC = new HashMap<>();
      marketShareVolumeP = new HashMap<>();
      brokerTariffRepo = new HashMap<>();
  }

  public void setMarketShareVolumeMapC(Integer timeslot, Double marketShare) 
  {
    marketShareVolumeC.put(timeslot, marketShare);
  }

  public Double getMarketShareVolumeMapC(Integer timeslot)
  {
    if(marketShareVolumeC.get(timeslot) != null)
      return marketShareVolumeC.get(timeslot);
    else
      return 0.0;
  }

  public void setMarketShareVolumeMapP(Integer timeslot, Double marketShare) 
  {
    marketShareVolumeP.put(timeslot, marketShare);
  }

  public Double getMarketShareVolumeMapP(Integer timeslot)
  {
    if(marketShareVolumeP.get(timeslot) != null)
      return marketShareVolumeP.get(timeslot);
    else
      return 0.0;
  }

  public void updateBrokerTariffRepo(Integer timeslot, TariffSpecification spec, Double revenue, Double usage)
  {
    Map<TariffSpecification, Pair<Double, Double>> BTMap  = brokerTariffRepo.get(timeslot);

    if(BTMap == null)
    {
      BTMap = new HashMap<>();
    }

    if(BTMap.get(spec) != null)
    {
      Pair<Double, Double> item = BTMap.get(spec);
      BTMap.put(spec, new Pair<Double, Double>(item.getKey()+revenue, item.getValue()+usage));
    }
    else
      BTMap.put(spec, new Pair<Double, Double>(revenue, usage));

    brokerTariffRepo.put(timeslot, BTMap);
  }

  public Double getTariffRevenue(Integer timeslot)
  {
    Map<TariffSpecification, Pair<Double, Double>> BTMap  = brokerTariffRepo.get(timeslot);

    if(BTMap == null)
      return 0.0;
    else
    {
      Double revenue = 0.0;
      for(Pair<Double, Double> item: BTMap.values())
      {
        revenue += item.getKey();
      }
      return revenue;
    }
  }

  public Double getTariffRevenue(Integer timeslot, TariffSpecification spec)
  {
    Map<TariffSpecification, Pair<Double, Double>> BTMap  = brokerTariffRepo.get(timeslot);

    if(BTMap == null)
      return 0.0;
    else
    {
      Pair<Double, Double> item = BTMap.get(spec);
      if(item == null)
        return 0.0;
      else
        return item.getKey();
    }
  }

  public Double getTariffUsage(Integer timeslot)
  {
    Map<TariffSpecification, Pair<Double, Double>> BTMap  = brokerTariffRepo.get(timeslot);

    if(BTMap == null)
      return 0.0;
    else
    {
      Double usage = 0.0;
      for(Pair<Double, Double> item: BTMap.values())
      {
        usage += item.getValue();
      }
      return usage;
    }
  }

  public Double getTariffUsage(Integer timeslot, TariffSpecification spec)
  {
    Map<TariffSpecification, Pair<Double, Double>> BTMap  = brokerTariffRepo.get(timeslot);

    if(BTMap == null)
      return 0.0;
    else
    {
      Pair<Double, Double> item = BTMap.get(spec);
      if(item == null)
        return 0.0;
      else
        return item.getValue();
    }
  }

  public Pair<Double, Double> getConsProdTariffUsage(Integer timeslot)
  {
    Map<TariffSpecification, Pair<Double, Double>> BTMap  = brokerTariffRepo.get(timeslot);

    if(BTMap == null)
      return new Pair<Double, Double>(0.0, 0.0);
    else
    {
      Double consumption = 0.0;
      Double production = 0.0;

      for(Map.Entry<TariffSpecification, Pair<Double, Double>> outer: BTMap.entrySet())
      {
        if((outer.getKey() != null) && (outer.getKey().getPowerType().isConsumption()))
        {
          Pair<Double, Double> item = outer.getValue();
          consumption += item.getValue();
        }

        if((outer.getKey() != null) && (outer.getKey().getPowerType().isProduction()))
        {
          Pair<Double, Double> item = outer.getValue();
          production += item.getValue();
        }
      }
      return new Pair<Double, Double>(consumption, production);
    }
  }
}
