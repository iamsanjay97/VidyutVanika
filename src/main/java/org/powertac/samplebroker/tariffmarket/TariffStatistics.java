package org.powertac.samplebroker.tariffmarket;

import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import org.javatuples.Pair;

public class TariffStatistics 
{
    public class StatBook
    {
        // private Double UNIT_PEAK_PENALTY = -18.0;
        private Integer PEAKS = 3;
        public Boolean active;
        public Double avgRate;
        public Double tariffMarketShare;
        public Pair<Double, Integer> tariffAvgMarketShare;
        public Double tariffUsage;
        public Double tariffRevenue;
        public Double wholesaleCost;
        public TreeMap<Double, Double> possiblePeakDemands;
        public Integer duration;

        public StatBook(Double avgRate)
        {
            this.active = true;
            this.avgRate = avgRate;
            this.tariffMarketShare = -Double.MAX_VALUE;
            this.tariffAvgMarketShare = new Pair<Double, Integer> (0.0, 0);
            this.tariffUsage = 0.0;
            this.tariffRevenue = 0.0;
            this.wholesaleCost = 0.0;
            this.possiblePeakDemands = new TreeMap<>();
            this.duration = 0;
        }

        public void updateTariffMarketShare(Double tariffMarketShare)    
        {
            this.tariffMarketShare = Math.max(this.tariffMarketShare, tariffMarketShare);
            Double avgMarketShare = (this.tariffAvgMarketShare.getValue0() * this.tariffAvgMarketShare.getValue1() + tariffMarketShare) / (this.tariffAvgMarketShare.getValue1() + 1);
            Integer occ = this.tariffAvgMarketShare.getValue1() + 1;
            this.tariffAvgMarketShare = new Pair<Double, Integer> (avgMarketShare, occ);
        }

        public void updateTariffUsage(Double usage)
        {
            this.tariffUsage += usage;
        }

        public void updateTariffRevenue(Double revenue)
        {
            this.tariffRevenue += revenue; 
        }

        public void updateWholesaleCost(Double cost)
        {
            this.wholesaleCost += cost;
        }

        public void updatePossiblePeakDemands(Double demand, Double percBrokerDemand)
        {
            if(possiblePeakDemands.size() < this.PEAKS) {
                possiblePeakDemands.put(demand, percBrokerDemand);

            }
            else if(possiblePeakDemands.firstKey() < demand) {
                possiblePeakDemands.remove(possiblePeakDemands.firstKey());
                possiblePeakDemands.put(demand, percBrokerDemand);
            }
        }

        public void updateDuration()
        {
            this.duration++;
        }

        public Double getOverallRevenue()
        {
            Double revenue = this.tariffRevenue + this.wholesaleCost;
            return revenue;
        }

        public void resetMarketShare()
        {
            this.tariffMarketShare = 0.0;
            this.tariffAvgMarketShare = new Pair<Double, Integer> (0.0, 0);
        }

        public void deactivateTariffEntry()
        {
            this.active = false;
        }
    }

    private Map<Long, StatBook> tariffStatisticsMap;

    public TariffStatistics()
    {
        tariffStatisticsMap = new HashMap<>();
    }    

    public void createStateBook(Long tariffID, Double avgRate)
    {
        StatBook stateBook = new StatBook(avgRate);
        tariffStatisticsMap.put(tariffID, stateBook);
    }

    public void updateStatBook(Long tariffID, Double tariffMarketShare, Double usage, Double tariffRevenue, Double wholesaleCost, Double demand, Double percBrokerDemand)
    {
        StatBook statBook = tariffStatisticsMap.get(tariffID);
        if(statBook == null)
            return;

        statBook.updateTariffMarketShare(tariffMarketShare);
        statBook.updateTariffUsage(usage);
        statBook.updateTariffRevenue(tariffRevenue);
        statBook.updateWholesaleCost(wholesaleCost);
        statBook.updatePossiblePeakDemands(demand, percBrokerDemand);
        statBook.updateDuration();
        tariffStatisticsMap.put(tariffID, statBook);
    }

    public Double getTariffMarketShare(Long tariffID)
    {
        StatBook statBook = tariffStatisticsMap.get(tariffID);
        if(statBook == null)
            return 0.0;
        else 
            return statBook.tariffMarketShare;
    }

    public Double getTariffAvgMarketShare(Long tariffID)
    {
        StatBook statBook = tariffStatisticsMap.get(tariffID);
        if(statBook == null)
            return 0.0;
        else 
            return statBook.tariffAvgMarketShare.getValue0();
    }

    public Double getTariffProfit(Long tariffID)
    {
        StatBook statBook = tariffStatisticsMap.get(tariffID);
        if(statBook == null)
            return 0.0;
        else 
            return statBook.getOverallRevenue();
    }

    public Double getTariffAvgRate(Long tariffID)
    {
        StatBook statBook = tariffStatisticsMap.get(tariffID);
        if(statBook == null)
            return 0.0;
        else 
        return statBook.avgRate;
    }

    public void resetMarketShareinStatBook(Long tariffID)
    {
        StatBook statBook = tariffStatisticsMap.get(tariffID);
        if(statBook != null)
            statBook.resetMarketShare();
    }

    public void removeFromStatBook(Long tariffID)
    {
        StatBook statBook = tariffStatisticsMap.get(tariffID);
        if(statBook != null)
            statBook.deactivateTariffEntry();
    }

    public String toString()
    {
        String s = "";
        for(Map.Entry<Long, StatBook> item: tariffStatisticsMap.entrySet())
        {
            if(item.getValue().active)
            {
                s += "\nAccounting Details of " + item.getKey() + " tariff : ";
                s += "\nAvg Rate: " + item.getValue().avgRate;
                s += "\nDuration: " + item.getValue().duration;
                s += "\nTariff's Highest Market-Share: " + item.getValue().tariffMarketShare;
                s += "\nTariff's Average Market-Share: " + item.getValue().tariffAvgMarketShare.getValue0();
                s += "\nTariff Total Usage: " + item.getValue().tariffUsage;
                s += "\nTariff Total Revenue: " + item.getValue().tariffRevenue;
                s += "\nTotal Wholesale Cost: " + item.getValue().wholesaleCost;
                s += "\n\n";
            }
        }
        return s;
    }
}
