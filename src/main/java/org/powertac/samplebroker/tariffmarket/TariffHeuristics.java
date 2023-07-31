package org.powertac.samplebroker.tariffmarket;

import java.util.List;

import org.powertac.common.TariffSpecification;
import org.powertac.samplebroker.interfaces.BrokerContext;

public class TariffHeuristics 
{
    /**
     * HEURISTIC STRATEGY:
     * Start with the best tariff for customers (cheapest tariff) available [could be a configurable parameter based on game-size]. After 3.5 days publish 
     * next (second cheapest) tariff and record revenue of the previous tariff 
     * [revenue per timeslot = (profit from tariff - wholesale cost - possible peak penalty on highest three peaks in previous 3.5 days window*0.5)/number_of_timeslots_passed].
     * After the end of 1st week, check the revenue of the 2nd tariff in the same way as previous (without using true peak penalties). Also calculate the true 
     * revenue for both the windows using true peak penalties. 
     * If second tariff did better than first in terms of revenue, go to next (third cheapest) tariff and so on. If second tariff earned even less revenue 
     * than first go back to first tariff. If first tariff is also not generating revenue (revenue < 0), then go to fixed CROC TOU tariff (tariff of last resort). 
     * Repeat this process for 4 weeks, after that use the best tariff so far in terms of revenue, keep checking revenue every 1 week therafter and publish best 
     * tariff so far everytime after that (if the current tariff is the best so far, then maintain it). 
     * 
     * Note: Revoke previous tariff before publishing the new tariff. (revenue > 0) should be always true for any tariff.
     */

    private BrokerContext brokerContext;
    private TariffGenerator tariffGenerator;
    private TariffStatistics tariffStatistics;
    private List<Double[]> generatedRates;
    private TariffSpecification generatedTariffOfLastResort;

    private TariffSpecification currentTariff;

    private int initialTariffIndex;
    private int currentTariffIndex;
    private int lastTariffIndex;
    private int numberOfTariffs;

    public TariffHeuristics(BrokerContext brokerCtx, int gameSize)
    {
        brokerContext = brokerCtx;
        tariffGenerator = new TariffGenerator();
        tariffStatistics = new TariffStatistics();
        generatedRates = tariffGenerator.getGeneratedRates();
        generatedTariffOfLastResort = tariffGenerator.generateTariffOfLastResort(brokerCtx);

        currentTariff = null;

        if(gameSize <= 3)
            initialTariffIndex = 5;        
        else if(gameSize <= 5)
            initialTariffIndex = 3;
        else
            initialTariffIndex = 2;

        currentTariffIndex = initialTariffIndex;
        lastTariffIndex = -1;
        numberOfTariffs = tariffGenerator.getNumberOfTariffs();
    }
    
    public void updateTariffStats(Double tariffRevenue, Double wholesaleCost, Double demand, Double percBrokerDemand)
    {
        tariffStatistics.updateStatBook(currentTariffIndex, tariffRevenue, wholesaleCost, demand, percBrokerDemand);
    }

    public TariffSpecification getInitialTariff()
    {
        Double[] rates = generatedRates.get(initialTariffIndex);
        TariffSpecification spec = tariffGenerator.generateBlockTOUTariff(brokerContext, rates);
        currentTariff = spec;
        return spec;
    }

    public Double getTariffRevenuePerTimeslot(double threshold, Boolean save)
    {
        return tariffStatistics.getTariffRevenuePerTimeslot(currentTariffIndex, threshold, save);
    }

    public Double getAvgTariffRevenuePerTimeslotOfCurrentTariff()
    {
        return tariffStatistics.getAvgRevenueOfTariff(currentTariffIndex);
    }

    public Double getAvgTariffRevenuePerTimeslotOfPrevTariff()
    {
        if(currentTariffIndex != 0)
            return tariffStatistics.getAvgRevenueOfTariff(currentTariffIndex-1);
        else    
            return -1e9;
    }

    public TariffSpecification getCurrentTariff()
    {
        return currentTariff;
    }

    public Integer getCurrentTariffIndex()
    {
        return currentTariffIndex;
    }

    public TariffSpecification getNextTariff()
    {
        if(currentTariffIndex == (numberOfTariffs-1))   // Signal that the current tariff is the best (costliest) available, no need to publish a new tariff
            return null;
        else
        {
            lastTariffIndex = currentTariffIndex;
            currentTariffIndex++;
            
            Double[] rates = generatedRates.get(currentTariffIndex);
            TariffSpecification spec = tariffGenerator.generateBlockTOUTariff(brokerContext, rates); 
            currentTariff = spec;

            return spec;
        }
    }

    public TariffSpecification getPrevTariff()
    {
        if(currentTariffIndex == 0)   // Signal that the current tariff is the cheapest available, if even that is not good then publish tariff of last resort 
        {
            lastTariffIndex = currentTariffIndex;
            currentTariff = generatedTariffOfLastResort;
            return generatedTariffOfLastResort;
        }
        else
        {
            lastTariffIndex = currentTariffIndex;
            currentTariffIndex--;

            Double[] rates = generatedRates.get(currentTariffIndex);
            TariffSpecification spec = tariffGenerator.generateBlockTOUTariff(brokerContext, rates); 
            currentTariff = spec;

            return spec;
        }
    }

    public Integer getLastTariffIndex()
    {
        return lastTariffIndex;
    }

    public TariffSpecification getTariffOfLastResort()
    {
        return generatedTariffOfLastResort;
    }

    public TariffSpecification getTheBestTariff()
    {
        Integer index = tariffStatistics.getIndexOfBestTariff();

        if(index == currentTariffIndex)            // current tariff is the best tariff
            return null;

        Double[] rates = generatedRates.get(index);
        TariffSpecification spec = tariffGenerator.generateBlockTOUTariff(brokerContext, rates); 

        lastTariffIndex = currentTariffIndex;
        currentTariffIndex = index;
        currentTariff = spec;

        return spec;
    }

    public Integer getBestTariffIndex()
    {
        return tariffStatistics.getIndexOfBestTariff();
    }
}
