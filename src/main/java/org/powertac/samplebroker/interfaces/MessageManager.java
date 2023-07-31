package org.powertac.samplebroker.interfaces;

import java.util.List;

import org.powertac.samplebroker.information.CustomerUsageInformation;
import org.powertac.samplebroker.information.WholesaleMarketInformation;
import org.powertac.samplebroker.messages.*;

public interface MessageManager {

    public Double collectNetDemand(Integer timeslot);

    /**
     * Calculate Capacity Transaction Threshold
     * @return
     */
    public Double calculateThreshold();

    /**
     * Returns the avg of a list
     */
    public Double calculateMean(List<Double> list);

    /**
     * Returns the s.d. of a list
     */
    public Double calculateStdev(List<Double> list, Double mean);

    /**
     * Returns the list net demands
     */
    public List<Double> getListOfNetDemands();

    /**
     * Returns game information object
     */
    public GameInformation getGameInformation ();

    /**
     *
     * @return Weather information object; Contains Weather Report and Weather Forecasts
     */
    public WeatherInformation getWeatherInformation();

    /**
     *
     * @return Distribution object for use by other classes
     */
    public DistributionInformation getDistributionInformation();

    /**
     *
     * @return BalancingMarketInformation object for use by other classes
     */
    public BalancingMarketInformation getBalancingMarketInformation();

    /**
     *
     * @return CapacityTransaction Information object for use by other classes
     */
    public CapacityTransactionInformation getCapacityTransactionInformation();

    /**
     *
     * @return CashPosition Information object for use by other classes
     */
    public CashPositionInformation getCashPositionInformation();
    /**
     *
     * @return MarketPosition Information object for use by other classes
     */
    public MarketPositionInformation getMarketPositionInformation();
    /**
     *
     * @return Market transaction Information object for use by other classes
     */
    public MarketTransactionInformation getMarketTransactionInformation();

    /**
     * @return Order book Information object for use by other classes
     */
    public OrderBookInformation getOrderBookInformation();

    /**
     *
     * @return Distribution object for use by other classes
     */
    public ClearedTradeInformation getClearTradeInformation();

    /**
     * @return customer usage information
     */

    public CustomerUsageInformation getCustomerUsageInformation();

    /**
     * @return customer usage information
     */

    public WholesaleMarketInformation getWholesaleMarketInformation();
}
