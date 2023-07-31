package org.powertac.samplebroker.tariffmarket;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.ArrayList;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.util.Helper;

public class TariffHeuristics 
{
    /**
     * HEURISTIC STRATEGY INSPIRED BY THE IDEA OF MAINTING THE MARKET-SHARE BETWEEN CERTAIN BOUNDS:
     *  --> Pulish CONSUMPTION, INTERUPPTIBLE_CONSUMPTION, PRODUCTION, THERMAL_STORAGE_CONSUMPTION and BATTERY_STORAGE tariffs, 
     *      keep updating all the tariffs frequently based on market condition.  
     *  --> PRODUCTION tariff should be such that its offer avg_rate value is less than CONSUMPTION 
     *      tariff avg_rate value; except in rare cases where we need to have PRODUCTION customers to
     *      maintain balanced portfolio and all competitor tariffs are higher than our CONSUMPTION rate
     *      value then offer PRODUCTION tariff rate value up to our minimum viable cost.
     * 
     *  Goal1: To have overall market-share (after combining market-shares of all the activate tariffs) 
     *         between the certain lower and higher bounds. 
     *  Goal2: Published tariff should be such which don't make our demand peaks coinside with market-
     *         peaks, to reduce the effect of capacity transaction penalties.
     * 
     * How to evaluate tariffs --> Use evaluateCost() function which takes only tariff spec as input. 
     * 
     * How to convert a FPT to weekly TOUT of desired structure --> look TariffGenerator class  
     */

    private BrokerContext brokerContext;
    private TariffGenerator tariffGenerator;
    private TariffStatistics tariffStatistics;
    private PowerType[] offeredPowerTypes;
    private Map<PowerType, Double> initialAvgRates;
    private boolean utilityFlag = false;

    public TariffHeuristics(BrokerContext brokerCtx)
    {
        brokerContext = brokerCtx;
        tariffGenerator = new TariffGenerator();
        tariffStatistics = new TariffStatistics();
        offeredPowerTypes = new PowerType[] { PowerType.PRODUCTION, PowerType.CONSUMPTION, PowerType.THERMAL_STORAGE_CONSUMPTION, PowerType.BATTERY_STORAGE};//, PowerType.INTERRUPTIBLE_CONSUMPTION};

        // set initial avg rate-values of all the tariffs
        initialAvgRates = new HashMap<>();
        initialAvgRates.put(PowerType.PRODUCTION, 0.03);
        initialAvgRates.put(PowerType.CONSUMPTION, -0.18);
        initialAvgRates.put(PowerType.THERMAL_STORAGE_CONSUMPTION, -0.18);
        // initialAvgRates.put(PowerType.INTERRUPTIBLE_CONSUMPTION, -0.18);
        initialAvgRates.put(PowerType.BATTERY_STORAGE, -0.18);
    }

    public List<TariffSpecification> getInitialTariffs()
    {
        List<TariffSpecification> initialTariffs = new ArrayList<>();
        // offeredPowerTypes = new PowerType[] {PowerType.CONSUMPTION};   // temporary
        for(PowerType pType: offeredPowerTypes)
        {
            if(pType.isConsumption())
                initialTariffs.add(tariffGenerator.generateWeeklyTOUTariff(brokerContext, initialAvgRates.get(pType), pType));
            else
                initialTariffs.add(tariffGenerator.generateFPTariff(brokerContext, initialAvgRates.get(pType), pType, true));
        }
        return initialTariffs;
    }

    public List<TariffSpecification> getDefaultBrokerTariffs(Double ap)
    {
        List<TariffSpecification> initialTariffs = new ArrayList<>();

        // Double consRate = -ap*profitRate;
        // Double prodRate = ap/profitRate;

        // initialTariffs.add(tariffGenerator.generateFPTariff(brokerContext, consRate, PowerType.CONSUMPTION, false));    
        initialTariffs.add(tariffGenerator.generateFPTariff(brokerContext, ap, PowerType.PRODUCTION, false));
        // initialTariffs.add(tariffGenerator.generateFPTariff(brokerContext, -0.50, PowerType.STORAGE));
        
        return initialTariffs;
    }

    public Double getTariffAvgRate(TariffSpecification spec)
    {
        return tariffStatistics.getTariffAvgRate(spec.getId());
    }

    public void createTariffStateBook(TariffSpecification spec)
    {
        Double avgRate = Helper.evaluateCost(spec, utilityFlag);
        tariffStatistics.createStateBook(spec.getId(), avgRate);
    }
    
    public void updateTariffStats(Long tariffID, Double tariffMarketShare, Double usage, Double tariffRevenue, Double wholesaleCost, Double demand, Double percBrokerDemand)
    {
        tariffStatistics.updateStatBook(tariffID, tariffMarketShare, usage, tariffRevenue, wholesaleCost, demand, percBrokerDemand);
    }

    public Boolean isTariffHealthy(TariffSpecification spec, Double TARIFF_LOWER_BOUND, Double TARIFF_UPPER_BOUND, Boolean isProduction)
    {
        double avgRate = tariffStatistics.getTariffAvgRate(spec.getId());
        double tariffMarketShare = tariffStatistics.getTariffMarketShare(spec.getId());
        double tariffProfit = tariffStatistics.getTariffProfit(spec.getId());
        System.out.println(spec.getId() + " :: " + tariffProfit + " :: " + tariffMarketShare);

        if(isProduction)
        {
            if((avgRate > 0.0) && (TARIFF_LOWER_BOUND <= tariffMarketShare) && (tariffMarketShare <= TARIFF_UPPER_BOUND))
                return true;
            else
                return false;
        }
        else
        {
            if((avgRate < 0.0) && (tariffProfit >= 0) && ((TARIFF_LOWER_BOUND <= tariffMarketShare) && (tariffMarketShare <= TARIFF_UPPER_BOUND)))
                return true;
            else
                return false;
        }
    }

    public Double getCumulativeMarketShare(List<TariffSpecification> specs)
    {
        Double cMarketShare = 0.0;
        for(TariffSpecification spec: specs)
            cMarketShare += tariffStatistics.getTariffAvgMarketShare(spec.getId());
        return cMarketShare;
    }

    public Double getLeastSubscribedTariffAvgRate(List<TariffSpecification> specs)
    {
        Double marketShare = 1.0;
        TariffSpecification leastSubscribedTariff = null;
        for(TariffSpecification spec: specs)
        {
           Double mShare = tariffStatistics.getTariffMarketShare(spec.getId());
           
           if(mShare < marketShare)
           {
               marketShare = mShare;
               leastSubscribedTariff = spec;
           }
        }
        if(leastSubscribedTariff != null)
            return tariffStatistics.getTariffAvgRate(leastSubscribedTariff.getId());
        else
            return null;
    }

    public Double getMostSubscribedTariffAvgRate(List<TariffSpecification> specs)
    {
        Double marketShare = 0.0;
        TariffSpecification mostSubscribedTariff = null;
        for(TariffSpecification spec: specs)
        {
           Double mShare = tariffStatistics.getTariffMarketShare(spec.getId());
           
           if(mShare > marketShare)
           {
               marketShare = mShare;
               mostSubscribedTariff = spec;
           }
        }
        if(mostSubscribedTariff != null)
            return tariffStatistics.getTariffAvgRate(mostSubscribedTariff.getId());
        else
            return null;
    }

    public TreeMap<Double, TariffSpecification> sortTariffsByCost(List<TariffSpecification> specs, String powerType)
    {
        TreeMap<Double, TariffSpecification> sortedTariffs = new TreeMap<>();
        for(TariffSpecification spec: specs)
        {
            Double avgRate = tariffStatistics.getTariffAvgRate(spec.getId());

            if(powerType.equals("CONSUMPTION") && (avgRate < 0.0))
                sortedTariffs.put(avgRate, spec);
            else if(powerType.equals("PRODUCTION") && (avgRate > 0.0))
                sortedTariffs.put(avgRate, spec);
            else 
                sortedTariffs.put(avgRate, spec);
        }
        return sortedTariffs;
    }

    public Double increseTariffAvgRate(Double avgRate)
    {
        return tariffGenerator.increseTariffAvgRate(avgRate);
    }

    public Double decreseTariffAvgRate(Double avgRate)
    {
        return tariffGenerator.decreseTariffAvgRate(avgRate);
    }

    public TariffSpecification improveConsTariffs(Double avgRate, PowerType powerType)
    {
        TariffSpecification improvedTariff = tariffGenerator.generateWeeklyTOUTariff(brokerContext, avgRate, powerType);
        return improvedTariff;
    }

    public TariffSpecification improveProdTariffs(Double avgRate, PowerType powerType)
    {
        TariffSpecification improvedTariff = tariffGenerator.generateFPTariff(brokerContext, avgRate, powerType, true);
        return improvedTariff;
    }

    public void resetMarketShareinStatBook(TariffSpecification spec)
    {
        tariffStatistics.resetMarketShareinStatBook(spec.getId());
    }

    public void removeFromStatBook(TariffSpecification spec)
    {
        tariffStatistics.removeFromStatBook(spec.getId());
    }

    public String toString()
    {
        return tariffStatistics.toString();
    }

    public TariffStatistics getTariffStatistics()
    {
        return tariffStatistics;
    }
}
