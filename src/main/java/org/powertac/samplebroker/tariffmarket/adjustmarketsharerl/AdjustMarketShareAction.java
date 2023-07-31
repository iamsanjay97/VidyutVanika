package org.powertac.samplebroker.tariffmarket.adjustmarketsharerl;

import java.util.Random;

/**
 *  Action Space:  [5 actions] :: Maintain, Lower1 & Match1, Lower2 & Match1, Upper1 & Match1, Upper2 & Match1
		
 *  Match1: (currentRate + oppBestTariff) / 2
 * 	Maintain (0): currentRate 
  ○ New Tariff: 
		currentRate + step
		
		Lower1 & Match1 (1): step = -0.02 
		Lower2 & Match1 (2): step = -0.04
		Upper1 & Match1 (3): step = 0.02
        Upper2 & Match1 (4): step = 0.04
*/

public class AdjustMarketShareAction
{
    Random rand;
    Integer defaultAction = 0;
    Integer currentAction;
    Double rateValue;
    Double rateChange;
    final Integer ACTION_SPACE_SIZE = 5;

    public AdjustMarketShareAction()
    {
        currentAction = defaultAction;
        rateValue = 0.0;
        rand = new Random();
    }   

    public Double[] setAction(Integer actionIndex, Double currentRateValue, Double cheapestOpponentTariff)
    {
        Double step;

        switch(actionIndex)
        {
            case 0:
                    currentAction = 0;
                    rateValue = currentRateValue;
                    break;
            case 1:
                    step = -0.02;
                    currentAction = 1;
                    rateValue = currentRateValue + step;                    
                    break;
            case 2:
                    step = -0.04;
                    currentAction = 2;
                    rateValue = currentRateValue + step;
                    break;
            case 3:
                    step = 0.02;
                    currentAction = 3;
                    rateValue = currentRateValue + step;
                    break;
            case 4:
                    step = 0.04;
                    currentAction = 4;
                    rateValue = currentRateValue + step;
                    break;
            default:
                    currentAction = 0;
                    rateValue = currentRateValue;
        }

        Double[] output = new Double[2];
        output[0] = rateValue;

        if(actionIndex != 0)
            output[1] = (currentRateValue + cheapestOpponentTariff) / 2;
        else 
            output[1] = null;

        return output;
    }

    public Integer getActionIndex()
    {
        return currentAction;
    }

    public Double getNewRateValue()
    {
        return rateValue;
    }

    public Double getCurrentRateValue()
    {
        return rateValue - rateChange;
    }

    public Double getRateChangeValue()
    {
        return rateChange;
    }

    public Integer getSize()
    {
        return ACTION_SPACE_SIZE;
    }

    public String toString()
    {
        return "Current Action: " + currentAction;
    }
}
