package org.powertac.samplebroker.tariffmarket;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;

import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.samplebroker.interfaces.BrokerContext;

public class TariffGenerator 
{
    /**
     * Create 10 tariffs in the pairs of 2s (keep the blocks same for all the 8 tariffs, only rates are variable). 
     * In each pair, rates of non-peaks values are same, only peak blocks values are higher compare to first tariff in the pair. 
     * Next set of pair has higher rates values for all the blocks and second tariff has the same rates as first for non-peak blocks
     * and higher rates for peak blocks similar to the first pair. Do this for remaining two pairs too. 
     * 
     * Blocks: [5, 8, 13, 16, 21, 1, 5]
     *           [NP,  P, NP, P ,NP, NP]
     * 
     * If even the first tariff is not generating revenue or not attracting customers, then go to 
     * fixed CROC TOU tariff (tariff of last resort). But this CROC tariff is not used; instead in 
     * case of low revenue select highest revenue making tariff from above 10 tariffs.
     */

    private Double MONDAY_MULTIPLIER;

    private List<Double[]> tariffRates;
    private int numberOfTariffs = 11;

    public TariffGenerator()
    {
        MONDAY_MULTIPLIER = 1.1;
        tariffRates = new ArrayList<>();

        tariffRates.add(new Double[] {-0.1204, -0.1912, -0.1193, -0.2416, -0.1206, -0.1207, -0.118, -0.1784, -0.1174, -0.2286, -0.1181, -0.1181});
        tariffRates.add(new Double[] {-0.1504, -0.1812, -0.1493, -0.1816, -0.1506, -0.1507, -0.148, -0.1784, -0.1474, -0.1786, -0.1481, -0.1481});
        tariffRates.add(new Double[] {-0.1504, -0.2312, -0.1493, -0.2816, -0.1506, -0.1507, -0.148, -0.2184, -0.1474, -0.2686, -0.1481, -0.1481});
        tariffRates.add(new Double[] {-0.2642, -0.2971, -0.2092, -0.2689, -0.227, -0.089, -0.2475, -0.2773, -0.1836, -0.2697, -0.2084, -0.089});
        tariffRates.add(new Double[] {-0.2642, -0.3971, -0.2092, -0.4189, -0.227, -0.089, -0.2475, -0.3773, -0.1836, -0.4197, -0.2084, -0.089});
        tariffRates.add(new Double[] {-0.2173, -0.2712, -0.2897, -0.4798, -0.3768, -0.1387, -0.1738, -0.217, -0.2318, -0.3838, -0.3015, -0.1109});
        tariffRates.add(new Double[] {-0.2173, -0.3212, -0.2897, -0.5298, -0.4068, -0.1387, -0.1738, -0.267, -0.2318, -0.4338, -0.3315, -0.1109});
        tariffRates.add(new Double[] {-0.27, -0.43, -0.25, -0.50, -0.39, -0.15, -0.23, -0.39, -0.21, -0.46, -0.35, -0.15});
        tariffRates.add(new Double[] {-0.23, -0.53, -0.25, -0.65, -0.43, -0.10, -0.19, -0.49, -0.21, -0.60, -0.39, -0.10});
        tariffRates.add(new Double[] {-0.33, -0.49, -0.29, -0.60, -0.45, -0.13, -0.30, -0.44, -0.26, -0.55, -0.41, -0.13});
        tariffRates.add(new Double[] {-0.33, -0.65, -0.29, -0.75, -0.55, -0.13, -0.30, -0.60, -0.26, -0.70, -0.50, -0.13});
    }

    public List<Double[]> getGeneratedRates()
    {
        return tariffRates;
    }

    public int getNumberOfTariffs()
    {
        return numberOfTariffs;
    }

    public TariffSpecification generateBlockTOUTariff(BrokerContext brokerContext, Double[] rates) 
    {
        PowerType pt = PowerType.CONSUMPTION;
        TariffSpecification spec = new TariffSpecification(brokerContext.getBroker(), pt);

        Random rand = new Random();
        double stdev = 0.005;  // ideal to keep 0.005

        for(int i = 0; i < rates.length; i++)                   
            rates[i] += (Math.round(rand.nextGaussian()*stdev*1e6)/1e6);
        
        Rate r0 = new Rate().withValue(MONDAY_MULTIPLIER*rates[0]).withDailyBegin(5).withDailyEnd(8).withWeeklyBegin(1).withWeeklyEnd(1);
        Rate r1 = new Rate().withValue(MONDAY_MULTIPLIER*rates[1]).withDailyBegin(8).withDailyEnd(13).withWeeklyBegin(1).withWeeklyEnd(1);
        Rate r2 = new Rate().withValue(MONDAY_MULTIPLIER*rates[2]).withDailyBegin(13).withDailyEnd(16).withWeeklyBegin(1).withWeeklyEnd(1);
        Rate r3 = new Rate().withValue(MONDAY_MULTIPLIER*rates[3]).withDailyBegin(16).withDailyEnd(21).withWeeklyBegin(1).withWeeklyEnd(1);
        Rate r4 = new Rate().withValue(MONDAY_MULTIPLIER*rates[4]).withDailyBegin(21).withDailyEnd(1).withWeeklyBegin(1).withWeeklyEnd(1);
        Rate r5 = new Rate().withValue(MONDAY_MULTIPLIER*rates[5]).withDailyBegin(1).withDailyEnd(5).withWeeklyBegin(1).withWeeklyEnd(1);
        Rate r6 = new Rate().withValue(rates[0]).withDailyBegin(5).withDailyEnd(8).withWeeklyBegin(2).withWeeklyEnd(5);
        Rate r7 = new Rate().withValue(rates[1]).withDailyBegin(8).withDailyEnd(13).withWeeklyBegin(2).withWeeklyEnd(5);
        Rate r8 = new Rate().withValue(rates[2]).withDailyBegin(13).withDailyEnd(16).withWeeklyBegin(2).withWeeklyEnd(5);
        Rate r9 = new Rate().withValue(rates[3]).withDailyBegin(16).withDailyEnd(21).withWeeklyBegin(2).withWeeklyEnd(5);
        Rate r10 = new Rate().withValue(rates[4]).withDailyBegin(21).withDailyEnd(1).withWeeklyBegin(2).withWeeklyEnd(5);
        Rate r11 = new Rate().withValue(rates[5]).withDailyBegin(1).withDailyEnd(5).withWeeklyBegin(2).withWeeklyEnd(5);
        Rate r12 = new Rate().withValue(rates[6]).withDailyBegin(5).withDailyEnd(8).withWeeklyBegin(6).withWeeklyEnd(7);
        Rate r13 = new Rate().withValue(rates[7]).withDailyBegin(8).withDailyEnd(13).withWeeklyBegin(6).withWeeklyEnd(7);
        Rate r14 = new Rate().withValue(rates[8]).withDailyBegin(13).withDailyEnd(16).withWeeklyBegin(6).withWeeklyEnd(7);
        Rate r15 = new Rate().withValue(rates[9]).withDailyBegin(16).withDailyEnd(21).withWeeklyBegin(6).withWeeklyEnd(7);
        Rate r16 = new Rate().withValue(rates[10]).withDailyBegin(21).withDailyEnd(1).withWeeklyBegin(6).withWeeklyEnd(7);
        Rate r17 = new Rate().withValue(rates[11]).withDailyBegin(1).withDailyEnd(5).withWeeklyBegin(6).withWeeklyEnd(7);
        
        spec.addRate(r0);
        spec.addRate(r1);
        spec.addRate(r2);
        spec.addRate(r3);
        spec.addRate(r4);
        spec.addRate(r5);
        spec.addRate(r6);
        spec.addRate(r7);
        spec.addRate(r8);
        spec.addRate(r9);
        spec.addRate(r10);
        spec.addRate(r11);
        spec.addRate(r12);
        spec.addRate(r13);
        spec.addRate(r14);
        spec.addRate(r15);
        spec.addRate(r16);
        spec.addRate(r17);

        return spec;
    }

    public TariffSpecification generateTariffOfLastResort(BrokerContext brokerContext) 
    {
        PowerType pt = PowerType.CONSUMPTION;
        TariffSpecification spec = new TariffSpecification(brokerContext.getBroker(), pt);

        Rate r1 = new Rate().withValue(-0.11).withDailyBegin(6).withDailyEnd(11).withWeeklyBegin(2).withWeeklyEnd(5);
        Rate r2 = new Rate().withValue(-0.21).withDailyBegin(11).withDailyEnd(14).withWeeklyBegin(2).withWeeklyEnd(5);
        Rate r3 = new Rate().withValue(-0.13).withDailyBegin(14).withDailyEnd(17).withWeeklyBegin(2).withWeeklyEnd(5);
        Rate r4 = new Rate().withValue(-0.22).withDailyBegin(17).withDailyEnd(23).withWeeklyBegin(2).withWeeklyEnd(5);
        Rate r5 = new Rate().withValue(-0.05).withDailyBegin(23).withDailyEnd(6).withWeeklyBegin(2).withWeeklyEnd(5);
        
        Rate r6 = new Rate().withValue(-0.09).withDailyBegin(6).withDailyEnd(11).withWeeklyBegin(1).withWeeklyEnd(1);
        Rate r7 = new Rate().withValue(-0.20).withDailyBegin(11).withDailyEnd(14).withWeeklyBegin(1).withWeeklyEnd(1);
        Rate r8 = new Rate().withValue(-0.11).withDailyBegin(14).withDailyEnd(17).withWeeklyBegin(1).withWeeklyEnd(1);
        Rate r9 = new Rate().withValue(-0.20).withDailyBegin(17).withDailyEnd(23).withWeeklyBegin(1).withWeeklyEnd(1);
        Rate r0 = new Rate().withValue(-0.05).withDailyBegin(23).withDailyEnd(6).withWeeklyBegin(1).withWeeklyEnd(1);
        
        Rate r10= new Rate().withValue(-0.10).withDailyBegin(6).withDailyEnd(16).withWeeklyBegin(6).withWeeklyEnd(7);
        Rate r11= new Rate().withValue(-0.20).withDailyBegin(16).withDailyEnd(23).withWeeklyBegin(6).withWeeklyEnd(7);
        Rate r12= new Rate().withValue(-0.05).withDailyBegin(23).withDailyEnd(6).withWeeklyBegin(6).withWeeklyEnd(7);
    
        spec.addRate(r0);
        spec.addRate(r1);
        spec.addRate(r2);
        spec.addRate(r3);
        spec.addRate(r4);
        spec.addRate(r5);
        spec.addRate(r6);
        spec.addRate(r7);
        spec.addRate(r8);
        spec.addRate(r9);
        spec.addRate(r10);
        spec.addRate(r11);
        spec.addRate(r12);

        return spec;
    }
}
