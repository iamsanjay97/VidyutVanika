package org.powertac.samplebroker.interfaces;

/**
 * Interface for portfolio manager, makes usage statistics available.
 * @author John Collins
 */
public interface PortfolioManager
{
  /**
   * Returns total net expected usage across all subscriptions for the given
   * index (normally a timeslot serial number).
   */
  public double collectUsage (int index);
  public double[] collectUsage (int currentTimeslot, boolean flag);       // LSTM

  public double[] MCPPredictorFFN (int currentTimeslot);       // FFN
  public double[] NetImbalancePredictorFFN (int currentTimeslot);       // FFN
}
