package org.powertac.samplebroker.tariffmarket;

import java.util.Random;
import org.powertac.common.Rate;
import org.powertac.common.RegulationRate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.samplebroker.interfaces.BrokerContext;

public class TariffGenerator 
{
    /**
     * Desired structured of the tariff (mainly for C, IC, TSC tariffs)
     *      Weekly tariff for each hour, identify peak and non-peak hours
     *      rates for peak hours are delta_peak times than normal rates
     *      rates for Mondays are delta_monday times than the normal rates
     *      week-end rates are delta_weekend times than the normal rates
     * 
     * Convert a FPT to weekly TOUT of desired structure
     *      I/P: avg_rate ap 
     *      O/P: weekly TOUT
     *      Process: assume x is the normal rate value WHICH WE WANT TO FIND
     *               we have identified p peak hours out of 24 hours of each day
     *               multiply these peaks with delta_peak 
     *               multiply rates of monday with delta_monday
     *               multiply week-end rates with delta_weekend
     *               then the rate-value x would be,
     * 
     *      4*((24-p)*x + p*delta_peak*x) + delta_monday*((24-p)*x + p*delta_peak*x) + 2*delta_weekend*((24-p)*x + p*delta_peak*x) / 168 = ap
     *      where, except x all the values are pre-decided
     *      add some randomness N(0, 0.001) in each rate-value
     *      after calculating x, use x to structure per-hour weekly tariff in the way described above 
     *      this would generate a weekly TOU tariff with avg rate-value close to input value ap.
     */

    private Double delta_peak;
    private Double delta_monday;
    private Double delta_weekend;

    private Double discountIC;
    private Double discountTSC;
    private Double discountBS;

    private Double[] blockStructure;
    private Integer nPeaks;

    // *********** Below are Configurable Parameters for Tariff Structure **************

    private Double PERIODIC_PAYMENT_MULTIPLIER = 1.0;        // Use only if we are not making good profits in the finals  #IMPORTANT
    private Double WITHDRAWAL_PENALTY_MULTIPLIER = 50.0;

    private Double DELTA_PEAK = 1.5;
    private Double DELTA_MONDAY = 1.2;
    private Double DELTA_WEEKEND = 0.8;

    private Double DISCOUNT_IC = 0.8;
    private Double DISCOUNT_TSC = 0.8;
    private Double DISCOUNT_BS = 0.8;

    public TariffGenerator()
    {
        delta_peak = DELTA_PEAK;
        delta_monday = DELTA_MONDAY;
        delta_weekend = DELTA_WEEKEND;

        // discount (1-discountIC)*100 % given to IC customers
        discountIC = DISCOUNT_IC;
        // discount (1-discountTSC)*100 % given to TSC customers
        discountTSC = DISCOUNT_TSC;
        // discount (1-discountTSC)*100 % given to BS customers
        discountBS = DISCOUNT_BS;

        // hourly peak and non-peak structure (0 to 23) 
        blockStructure = new Double[] {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, delta_peak, delta_peak, delta_peak, delta_peak, delta_peak, 
                                       1.0, 1.0, 1.0, 1.0, delta_peak, delta_peak, delta_peak, delta_peak, delta_peak, delta_peak, 1.0};
        nPeaks = countPeaks(blockStructure);
    }

    public Integer countPeaks(Double[] blockStructure)
    {
        Integer count = 0;
        for(Double item: blockStructure)
        {
            if(item == delta_peak)
                count++;
        }
        return count;
    }

    public Double calculateNormalRate(Double ap)
    {
        Double x = 168*ap / ((4 + delta_monday + 2*delta_weekend) * (24 - nPeaks + nPeaks*delta_peak));
        return x;
    }

    // Use the method to generate Weekly TOUT for CONSUMPTION, IC, TSC tariff-types
    public TariffSpecification generateWeeklyTOUTariff(BrokerContext brokerContext, Double ap, PowerType powerType)
    {
        TariffSpecification spec = new TariffSpecification(brokerContext.getBroker(), powerType);
        Double normalRate = calculateNormalRate(ap);
        Random rand = new Random();
        double stdev = 0.001;
        Double typeMultiplier = 1.0;

        if(powerType.equals(PowerType.INTERRUPTIBLE_CONSUMPTION))
            typeMultiplier = discountIC;
        else if(powerType.equals(PowerType.THERMAL_STORAGE_CONSUMPTION))
            typeMultiplier = discountTSC;
        else if(powerType.equals(PowerType.BATTERY_STORAGE))
            typeMultiplier = discountBS;

        for(int i = 1; i <= 7; i++)
        {
            Double multiplier = 1.0;
            if(i == 1)
                multiplier = delta_monday;
            else if(i == 6 || i == 7)
                multiplier = delta_weekend;

            for(int j = 0; j < 24; j++)
            {
                Double rateValue = Math.round((normalRate*blockStructure[j]*multiplier*typeMultiplier + rand.nextGaussian()*stdev)*1e6)/1e6;
                Rate rate = new Rate().withValue(rateValue).withDailyBegin(j).withDailyEnd(j).withWeeklyBegin(i).withWeeklyEnd(i);

                if(powerType.isInterruptible())
                    rate.withMaxCurtailment(0.5);

                spec.addRate(rate);
            }
        }    
        
        if(powerType.equals(PowerType.THERMAL_STORAGE_CONSUMPTION) || powerType.equals(PowerType.BATTERY_STORAGE))
        {
            RegulationRate regulationRate = new RegulationRate().withUpRegulationPayment(-1.30*ap).withDownRegulationPayment(0.70*ap);
            spec.addRate(regulationRate);
            spec.withEarlyWithdrawPayment(50.0*ap).withMinDuration(82800000);
        }
        else   
        {
            spec.withPeriodicPayment(PERIODIC_PAYMENT_MULTIPLIER*ap);
            spec.withEarlyWithdrawPayment(WITHDRAWAL_PENALTY_MULTIPLIER*ap).withMinDuration(82800000);   
        }

        return spec;
    }

    // Use the method to generate FPT for PRODUCTION and STORAGE tariff-types
    public TariffSpecification generateFPTariff(BrokerContext brokerContext, Double ap, PowerType powerType, boolean flag)
    {
        TariffSpecification spec = new TariffSpecification(brokerContext.getBroker(), powerType);

        Random rand = new Random();
        double stdev = 0.001;

        if(powerType.equals(PowerType.BATTERY_STORAGE))
        {
            Double rateValue = ap*discountBS + Math.round(rand.nextGaussian()*stdev*1e6)/1e6;
            spec.withMinDuration(302400000).withSignupPayment(10.1).withEarlyWithdrawPayment(-19);
            Rate rate = new Rate().withValue(rateValue);
            RegulationRate regulationRate = new RegulationRate().withUpRegulationPayment(-1.30*ap).withDownRegulationPayment(0.70*ap);
            spec.addRate(rate);
            spec.addRate(regulationRate);
        }
        else
        {
            if(flag)
            {
                for(int j = 0; j < 24; j++)
                {
                    Double rateValue = Math.round((ap + rand.nextGaussian()*stdev)*1e6)/1e6;
                    Rate rate = new Rate().withValue(rateValue).withDailyBegin(j).withDailyEnd(j);
                    spec.addRate(rate);
                }
            }
            else
            {
                Double rateValue = ap + Math.round(rand.nextGaussian()*stdev*1e6)/1e6;
                Rate rate = new Rate().withValue(rateValue);
                spec.addRate(rate);
            }
        }

        return spec;
    }

    public Double increseTariffAvgRate(Double ap)
    {
        Random rand = new Random();
        double mean = 0.02;
        double stdev = 0.0033;

        Double rateValue = Math.round((ap + rand.nextGaussian()*stdev + mean)*1e6)/1e6;
        return rateValue;
    }

    public Double decreseTariffAvgRate(Double ap)
    {
        Random rand = new Random();
        double mean = -0.02;
        double stdev = 0.0033;

        Double rateValue = Math.round((ap + rand.nextGaussian()*stdev + mean)*1e6)/1e6;
        return rateValue;
    }
}
