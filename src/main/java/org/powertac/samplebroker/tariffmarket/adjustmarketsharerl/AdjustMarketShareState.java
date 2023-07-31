package org.powertac.samplebroker.tariffmarket.adjustmarketsharerl;

/**
 *  State Space:  [1 State] :: Market-share buckets
 * 
* 		0 - |optimalMarketShare - currentMarketShare| <= optimalMarketShare*0.1
        1 - (optimalMarketShare - currentMarketShare) > optimalMarketShare*0.1      &       (optimalMarketShare - currentMarketShare) <= optimalMarketShare*0.4
        2 - (optimalMarketShare - currentMarketShare) > optimalMarketShare*0.4      &       (optimalMarketShare - currentMarketShare) <= optimalMarketShare*0.7
        3 - (optimalMarketShare - currentMarketShare) > optimalMarketShare*0.7
        4 - (-optimalMarketShare + currentMarketShare) > optimalMarketShare*0.1     &       (-optimalMarketShare + currentMarketShare) <= optimalMarketShare*0.4
        5 - (-optimalMarketShare + currentMarketShare) > optimalMarketShare*0.4     &       (-optimalMarketShare + currentMarketShare) <= optimalMarketShare*0.7
        6 - (-optimalMarketShare + currentMarketShare) > optimalMarketShare*0.7
 */

public class AdjustMarketShareState 
{
    Integer currentState;
    Integer defaultState = 0;
    Double relativeMarketShare;
    Double curMarketShare;
    final Integer STATE_SPACE_SIZE = 7;

    public AdjustMarketShareState()
    {
        currentState = defaultState;
        relativeMarketShare = 0.0;
    }   

    public void setState(Double optimalMarketShare, Double currentMarketShare)
    {
        curMarketShare = Double.valueOf(currentMarketShare);
        relativeMarketShare = optimalMarketShare - currentMarketShare;

        if(relativeMarketShare >= 0)
        {
            if(relativeMarketShare <= (optimalMarketShare*0.1))
                currentState = 0;
            else if(relativeMarketShare <= (optimalMarketShare*0.4))
                currentState = 1;
            else if(relativeMarketShare <= (optimalMarketShare*0.7))
                currentState = 2;
            else
                currentState = 3;
        }
        else
        {
            if(-relativeMarketShare <= (optimalMarketShare*0.1))
                currentState = 0;
            else if(-relativeMarketShare <= (optimalMarketShare*0.4))
                currentState = 4;
            else if(-relativeMarketShare <= (optimalMarketShare*0.7))
                currentState = 5;
            else
                currentState = 6;
        }
    }

    public Integer getStateIndex()
    {
        return currentState;
    }

    public Double getRelativeMarketShare()
    {
        return relativeMarketShare;
    }

    public Double getCurMarketShare()
    {
        return curMarketShare;
    }

    public Integer getSize()
    {
        return STATE_SPACE_SIZE;
    }

    public String toString()
    {
        return "Current State: " + relativeMarketShare;
    }
}
