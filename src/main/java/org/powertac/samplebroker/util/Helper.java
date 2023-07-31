package org.powertac.samplebroker.util;

import java.util.*;

import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.common.Tariff;

public class Helper {

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

    public static TariffSpecification generateBlockTOUTariff(BrokerContext brokerContext, PowerType pt, Double basePrice, int[] timeBlocks, double mini, double maxi) {
        TariffSpecification newTariff = new TariffSpecification(brokerContext.getBroker(), pt);
        int numBlocks = timeBlocks.length;
        Random rand = new Random();

        for (int i = 0; i < numBlocks; i+=2) {
            double r = rand.nextDouble();
            r = mini + (maxi - mini) * r;
            r = r * basePrice;
            Rate rate = new Rate().withValue(r).withDailyBegin(timeBlocks[i]).withDailyEnd(timeBlocks[i+1]);
            //System.out.println("index i "+ i+ " time blocks " + timeBlocks[i] + " " + timeBlocks[i+1] + " rate " + rate.toString());
            newTariff.addRate(rate);
        }

        //System.out.println(" tariff publication from helper " + newTariff.getRates());

        return newTariff;
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

  public static Double evaluateCost(TariffSpecification spec)
    // evaluates mean cost of tariff
    {
        Tariff tf = new Tariff(spec);
        List<Rate> rates = spec.getRates();
        Double cost = 0D;

        if(tf.isTimeOfUse())
        {
            if(!tf.isWeekly())
            {
                // daily TOU Tariffs
                for(Rate rate: rates)
                {
                    Double hours = (rate.getDailyEnd() - rate.getDailyBegin() + 1.0D) / 24.0D;
                    if(rate.getDailyEnd() < rate.getDailyBegin())
                        hours += 1.0D;
                    cost += hours * rate.getValue();
                }
            }
            else
            {
                // weekly TOU rates
                for(Rate rate: rates)
                {
                    Double hours = (rate.getDailyEnd() - rate.getDailyBegin() + 1.0D);
                    Double days = (double)(rate.getWeeklyEnd() - rate.getWeeklyBegin());
                    if(rate.getDailyEnd() < rate.getDailyBegin())
                    {
                        if(days == 0)
                            days = 7.0D;
                        hours += (24.0D * days);
                    }
                    cost += hours * rate.getValue();
                }
                cost /= 168.0D;
            }
        }

        else if(tf.isTiered())
        {
            // tiered tariffs
            Double threshold = null, totalThresh = 0D;
            for(Rate rate: rates)
            {
                if(threshold == null)
                {
                    totalThresh += 1.0;
                    cost += rate.getValue();
                }
                else
                {
                    totalThresh += threshold;
                    cost += (rate.getValue() * threshold);
                }
                threshold = rate.getTierThreshold();
            }
            cost /= totalThresh;
        }

        else
        {
            // absolutely fixed tariff or absolutely variable tariff
            Rate fixedRate = rates.get(0);

            if(!fixedRate.isFixed())
                cost += fixedRate.getExpectedMean();
            else
                cost += fixedRate.getValue();
        }

        return cost;
    }
}
