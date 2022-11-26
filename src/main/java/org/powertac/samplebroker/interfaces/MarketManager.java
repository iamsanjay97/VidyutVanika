package org.powertac.samplebroker.interfaces;

import org.powertac.samplebroker.information.SubmittedBidInformation;

/**
 * Encapsulates broker market interactions.
 * @author John Collins
 */
public interface MarketManager
{

  /**
   * Returns the mean price observed in the market
   */
  public double getMeanMarketPrice ();

  public SubmittedBidInformation getSubmittedBidInformation();
}
