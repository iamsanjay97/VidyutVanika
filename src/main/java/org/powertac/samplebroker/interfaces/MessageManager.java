package org.powertac.samplebroker.interfaces;

import org.powertac.samplebroker.information.CustomerUsageInformation;
import org.powertac.samplebroker.information.WholesaleMarketInformation;
import org.powertac.samplebroker.messages.*;

public interface MessageManager {
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
