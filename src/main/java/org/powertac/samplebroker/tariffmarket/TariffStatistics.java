package org.powertac.samplebroker.tariffmarket;

import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.HashMap;

public class TariffStatistics 
{
    public class StatBook
    {
        private Integer PEAKS = 3;
        private Double UNIT_PEAK_PENALTY = -18.0;

        // MAKE EVERYTHING PRIVATE ONCE TESTING IS DONE
        public Double tariffRevenue;
        public Double wholesaleCost;
        public TreeMap<Double, Double> possiblePeakDemands;
        public Integer duration;

        public StatBook()
        {
            this.tariffRevenue = 0.0;
            this.wholesaleCost = 0.0;
            this.possiblePeakDemands = new TreeMap<>();
            this.duration = 0;
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
            if(possiblePeakDemands.size() < this.PEAKS)
                possiblePeakDemands.put(demand, percBrokerDemand);
            else if(possiblePeakDemands.firstKey() < demand)
            {
                possiblePeakDemands.remove(possiblePeakDemands.firstKey());
                possiblePeakDemands.put(demand, percBrokerDemand);
            }
        }

        public void updateDuration()
        {
            this.duration++;
        }

        public Double getOverallRevenuePerTimeslot(double threshold)
        {
            Double revenue = this.tariffRevenue + this.wholesaleCost;
            System.out.println("\n\nRevenue Calculation:");
            System.out.print(this.tariffRevenue + " :: " + this.wholesaleCost + " :: " + threshold + " :: ");

            for(Map.Entry<Double, Double> item: possiblePeakDemands.entrySet())
            {
                System.out.print((((item.getKey()-threshold) > 0.0) ? ((item.getKey()-threshold) * item.getValue() * UNIT_PEAK_PENALTY * 0.25) : 0.0) + " :: ");
                revenue += ((item.getKey()-threshold) > 0.0) ? ((item.getKey()-threshold) * item.getValue() * UNIT_PEAK_PENALTY * 0.25) : 0.0;          
            }

            revenue /= this.duration;

            System.out.print(this.duration + " :: Revenue: " + revenue + "\n");

            return revenue;
        }

        public void resetStatBook()
        {
            this.tariffRevenue = 0.0;
            this.wholesaleCost = 0.0;
            this.possiblePeakDemands = new TreeMap<>();
            this.duration = 0;
        }
    }

    private Map<Integer, StatBook> tariffStatisticsMap;
    private Map<Integer, List<Double>> tariffOverallRevenueMap;

    public TariffStatistics()
    {
        tariffStatisticsMap = new HashMap<>();
        tariffOverallRevenueMap = new HashMap<>();
    }    

    public void updateStatBook(Integer ID, Double tariffRevenue, Double wholesaleCost, Double demand, Double percBrokerDemand)
    {
        StatBook statBook = tariffStatisticsMap.get(ID);

        if(statBook == null)
            statBook = new StatBook();

        statBook.updateTariffRevenue(tariffRevenue);
        statBook.updateWholesaleCost(wholesaleCost);
        statBook.updatePossiblePeakDemands(demand, percBrokerDemand);
        statBook.updateDuration();

        tariffStatisticsMap.put(ID, statBook);

        System.out.println("\n\nStatbook Info:");
        System.out.println("Total Revenue: " + statBook.tariffRevenue + " :: Total Wholesale Cost: " + statBook.wholesaleCost + " :: Duration: " + statBook.duration);
        for(Map.Entry<Double, Double> item: statBook.possiblePeakDemands.entrySet())
            System.out.println("Demand: " + item.getKey() + " :: Contribution: " + item.getValue());
    }

    public Double getTariffRevenuePerTimeslot(Integer ID, double threshold, Boolean save)
    {
        StatBook statBook = tariffStatisticsMap.get(ID);
        Double revenue;

        if(statBook != null)
            revenue = statBook.getOverallRevenuePerTimeslot(threshold);
        else
            revenue = 0.0;

        for(Map.Entry<Integer, List<Double>> item: tariffOverallRevenueMap.entrySet())
        {
            System.out.println(item.getKey());

            for(Double i: item.getValue())
                System.out.print(i + " :: ");
            System.out.println();
        }

        if(save)
        {
            List<Double> revenueList = tariffOverallRevenueMap.get(ID);

            if(revenueList == null)
                revenueList = new ArrayList<>();

            revenueList.add(revenue);
            tariffOverallRevenueMap.put(ID, revenueList);

            statBook.resetStatBook();
            tariffStatisticsMap.put(ID, statBook);
        }    

        return revenue;
    }

    public Double getAvgRevenueOfTariff(Integer ID)
    {
        List<Double> revenueList = tariffOverallRevenueMap.get(ID);
        Double avgRevenue = 0.0;

        if(revenueList == null)
            return avgRevenue;

        for(Double item: revenueList)
            avgRevenue += item;

        avgRevenue /= revenueList.size();

        return avgRevenue;
    }

    public Integer getIndexOfBestTariff()
    {
        Integer bestTariffIndex = 0;
        Double bestRevenue = -Double.MAX_VALUE;

        for(Map.Entry<Integer, List<Double>> outer: tariffOverallRevenueMap.entrySet())
        {
            List<Double> revenueList = outer.getValue();
            Double avgRevenue = 0.0;

            for(Double item: revenueList)
                avgRevenue += item;

            avgRevenue /= revenueList.size();

            if(avgRevenue > bestRevenue)
            {
                bestTariffIndex = outer.getKey();
                bestRevenue = avgRevenue;
            }
        }

        return bestTariffIndex;
    }
}
