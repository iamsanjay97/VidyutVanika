package org.powertac.samplebroker.util;

import java.util.*;

import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.Tariff;

public class Helper 
{
    public static List<String> getListOfTargetedConsumers()
    {
      String[] customers = new String[] {"DowntownOffices", "EastsideOffices", "Village 2 NS Controllable", "Village 1 NS Controllable", "CentervilleHomes", "sf2",
                          "Village 2 SS Controllable", "OfficeComplex 2 NS Controllable", "Village 1 NS Base", "Village 2 NS Base", "OfficeComplex 1 NS Controllable",
                          "FrostyStorage", "Village 1 RaS Base", "OfficeComplex 2 SS Base", "Village 2 SS Base", "Village 1 ReS Controllable", "Village 1 SS Controllable",
                          "OfficeComplex 1 NS Base", "Village 1 RaS Controllable", "seafood-2", "sf3", "OfficeComplex 1 SS Controllable", "Village 2 RaS Controllable",
                          "HextraChemical", "freezeco-1", "fc3", "seafood-1", "Village 1 SS Base", "Village 2 RaS Base", "BrooksideHomes", "fc2", "Village 1 ReS Base",
                          "OfficeComplex 2 NS Base", "Village 2 ReS Controllable", "Village 2 ReS Base", "freezeco-2", "MedicalCenter-1", "OfficeComplex 2 SS Controllable",
                          "OfficeComplex 1 SS Base", "freezeco-3"};

      return Arrays.asList(customers);
    }

    public static List<String> getListOfTargetedProducers()
    {
      String[] customers = new String[] {"WindmillCoOp-1", "WindmillCoOp-2", "MedicalCenter-2", "SolarLeasing", "SunnyhillSolar1", "SunnyhillSolar2"};

      return Arrays.asList(customers);
    }

    public static List<String> getListOfTargetedCustomers()
    {
      String[] customers = new String[] {"BrooksideHomes", "CentervilleHomes", "DowntownOffices", "EastsideOffices", "FrostyStorage", "HextraChemical",
                           "MedicalCenter-1", "WindmillCoOp-1", "WindmillCoOp-2", "SolarLeasing"};

      return Arrays.asList(customers);
    }

    public static int getBlockNumber(int hour, int[] timeBlocks){

      int numBlocks = timeBlocks.length;
      int block = -1;
      for(int i=0; i<numBlocks; i+=2){
          int h1 = timeBlocks[i];
          int h2 = timeBlocks[i+1];

          if((h1 <= hour) && (hour <= h2)){
              block = i/2;
          }
       }
      block = block + 1;
      return block;
  }

    public double[] getHourlyTariff(TariffSpecification specification)
    {
      // Generate tariff series
      List<Rate> rates = specification.getRates();
  
      double arr[] = new double[24];
      int flag1 = 0;
  
      for(Rate rate : rates)
      {
        int begin = rate.getDailyBegin();
        int end = rate.getDailyEnd() + 1;
  
        if(begin != -1)
        {
          flag1 = 1;
          while(begin != end)
          {
            arr[begin] = Math.abs(rate.getMinValue());
            begin = (begin + 1) % 24;
          }
        }
      }
  
      if(flag1 == 0)
        Arrays.fill(arr, Math.abs(rates.get(0).getMinValue()));
  
      return arr;
    }

    public static double evaluateCost(TariffSpecification spec, boolean flag)  
    {
      Tariff tf = new Tariff(spec);
      List<Rate> rates = spec.getRates();
      double cost = 0D;

      if(rates == null)
        return -1.0D; 

      if(flag)
      {
        double sum = 0.0;
        for(Rate r : rates)
          sum += r.getMinValue(); 
        int k = rates.size();
        return sum / k;
      }

      if(tf.isTimeOfUse())
      {
        double denom = 0.0;
        for(Rate rate: rates)
        {   
          double hours = (rate.getDailyEnd() - rate.getDailyBegin()) + 1.0D;
          double days = (rate.getWeeklyEnd() - rate.getWeeklyBegin()) + 1.0D;
          
          if(rate.getDailyEnd() < rate.getDailyBegin())
              hours += 24.0D;

          if(rate.getWeeklyEnd() < rate.getWeeklyBegin())
              days += 7.0D;
          
          denom += hours*days;
          cost += hours * days * rate.getValue();
        }
        cost /= denom;
      }
      else
      {
        // absolutely fixed tariff / absolutely variable tariff
        Rate fixedRate = rates.get(0);
        if(!fixedRate.isFixed())
            cost += fixedRate.getExpectedMean();
        else
            cost += fixedRate.getValue();

        if(spec.getPowerType().equals(PowerType.CONSUMPTION))      // TOU_Tariff is more preferred to FixedPrice_Tariff with same avgrate, trying to make both equal
        {
          cost -= 0.02;
          cost = Math.max(cost, -0.47);
        }
      }

      return cost;
    }
}
