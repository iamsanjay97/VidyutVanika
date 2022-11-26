package org.powertac.samplebroker;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;

import java.util.Random;
import java.util.List;
import java.util.Arrays;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.ArrayList;
import java.util.Collections;
import javafx.util.Pair;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.powertac.common.*;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.*;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.information.CustomerUsageInformation;
import org.powertac.samplebroker.information.UsageRecord;
import org.powertac.samplebroker.information.CustomerSubscriptionInformation;
import org.powertac.samplebroker.information.WholesaleMarketInformation;
import org.powertac.samplebroker.messages.*;
import org.powertac.samplebroker.messages.CapacityTransactionInformation.CapacityTransactionMessage;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.MessageManager;
import org.powertac.samplebroker.interfaces.Initializable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mongodb.*;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.DBCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoCollection;
import org.bson.Document;


@Service // Spring creates a single instance at startup
public class MessageManagerService implements MessageManager, Initializable, Activatable
{
    static Logger log = LogManager.getLogger(MessageManagerService.class);

    private BrokerContext brokerContext; // master

    // Spring fills in Autowired dependencies through a naming convention
    @Autowired
    private BrokerPropertiesService propertiesService;

    @Autowired
    private TimeslotRepo timeslotRepo;

    // Place holder for game information
    GameInformation gameInformation;

    // Place holder for weather information
    WeatherInformation weatherInformation;

    //Place holder for distriubution information
    DistributionInformation distributionInformation;

    //Place holder for balancing information
    BalancingMarketInformation balancingMarketInformation;

    //Place holder for capacity transaction information
    CapacityTransactionInformation capacityTransactionInformation;

    //Place holder for cash position information
    CashPositionInformation cashPositionInformation;

    //Place holder for market position information
    MarketPositionInformation marketPositionInformation;

    //Place holder for market transaction information
    MarketTransactionInformation marketTransactionInformation;

    //Place holder for cleared trade information
    ClearedTradeInformation clearedTradeInformation;

    //Place holder for WholesaleMarket Information
    WholesaleMarketInformation wholesaleMarketInformation;

    //Place holder for order books
    OrderBookInformation orderBookInformation;

    //Data structure to store usage information of individual customers
    CustomerUsageInformation custUsageInfo;

    //Default broker consumption tariff
    Double dfConsumptionRate = 0.0;

    //Default broker consumption tariff
    Double dfProductionRate = 0.0;

    double alpha = 0.3;

    //MongoDB client and Database
    MongoClient mongoClient;

    //MongoDB database
    DB mongoDatabase;

    String dbname;

    //Game Name
    String bootFile;

    List<String> brokers;

    //Dateformat pattern
    String dateFormat = "EEEEE MMMMM yyyy HH:mm";

    int[] blocks = {5, 9, 10, 16, 17, 22, 23, 4};

    int OFFSET = 24;

    Random rand;

    /**
     * Default constructor.
     */
    public MessageManagerService ()
    {
        super();
    }

    /**
     * Per-game initialization. Registration of message handlers is automated.
     */
    @Override // from Initializable
    public void initialize (BrokerContext context)
    {
        this.brokerContext = context;
        propertiesService.configureMe(this);

        gameInformation = new GameInformation();
        weatherInformation = new WeatherInformation();
        distributionInformation = new DistributionInformation();
        balancingMarketInformation = new BalancingMarketInformation();
        cashPositionInformation = new CashPositionInformation();
        capacityTransactionInformation = new CapacityTransactionInformation();
        marketPositionInformation = new MarketPositionInformation();
        marketTransactionInformation = new MarketTransactionInformation();
        clearedTradeInformation = new ClearedTradeInformation();
        wholesaleMarketInformation = new WholesaleMarketInformation();
        orderBookInformation = new OrderBookInformation();
        custUsageInfo = new CustomerUsageInformation(alpha,brokerContext.getUsageRecordLength());
        dbname = "PowerTAC2020_ZIP";

        rand = new Random();

        try
        {
          File file = new File("currentBootFile.txt");
          BufferedReader br = new BufferedReader(new FileReader(file));

          bootFile = br.readLine();
        }
        catch(IOException e)
        {}

        try
        {
            mongoClient = new MongoClient("localhost", 27017);
            mongoDatabase = mongoClient.getDB(dbname);
        }
        catch(Exception e)
        {
            log.warn("Mongo DB connection Exception " + e.toString());
        }
        log.info(" Connected to Database PowerTAC2019 -- Broker Initialize");
        System.out.println("Connected to Database " + dbname + " from Initialize in MessageManager");
    }

    // --------------- message handling -----------------
    /**
     * Handles the Competition instance that arrives at beginning of game.
     * Here we capture minimum order size to avoid running into the limit
     * and generating unhelpful error messages.
     */
    public synchronized void handleMessage (Competition comp)
    {
        gameInformation.setGameInformation(comp.getId(), comp.getName(), comp.getPomId(),comp.getSimulationBaseTime(),
                comp.getTimeslotLength(), comp.getBootstrapTimeslotCount(),
                comp.getBootstrapDiscardedTimeslots(), comp.getMinimumTimeslotCount(),
                comp.getTimeslotsOpen(), comp.getDeactivateTimeslotsAhead(),
                comp.getMinimumOrderQuantity(), comp.getMaxUpRegulationPaymentRatio(),
                comp.getUpRegulationDiscount(), comp.getMaxDownRegulationPaymentRatio(),
                comp.getDownRegulationDiscount(), comp.getEstimatedConsumptionPremium(),
                comp.getTimezoneOffset(), comp.getLatitude(), comp.getBrokers(),
                comp.getCustomers());
        log.info(" Game information from boot file loaded ");
        log.info(gameInformation.toString());

        brokers = comp.getBrokers();
    }


    /**
     * Receives a new WeatherForecast.
     */
    public synchronized void handleMessage (WeatherForecast forecast)
    {
        weatherInformation.setWeatherForecast(forecast.getTimeslotIndex(), forecast);
        //System.out.println(forecast);
    }

    /**
     * Receives a new WeatherReport.
     */
    public synchronized void handleMessage (WeatherReport report)
    {
        weatherInformation.setWeatherReport(report.getTimeslotIndex(), report);
        //System.out.println(report);
    }

    // -------------- Message handlers -------------------
    /**
     * Handles CustomerBootstrapData by populating the customer model
     * corresponding to the given customer and power type. This gives the
     * broker a running start.
     */
   public synchronized void handleMessage (CustomerBootstrapData cbd) {

       String customerName = cbd.getCustomerName();
       Integer dataLength = cbd.getNetUsage().length;
       Vector<Double> bootUsage = new Vector<Double>();
       Integer uLength = brokerContext.getUsageRecordLength();

       Double temp = 0.0;
       Double usage = 0.0;
       Integer index;

       Integer population = gameInformation.getPopulation(customerName);

       for (int i = 0; i < dataLength; i++) {
           index = i % uLength;
           usage = Math.abs(cbd.getNetUsage()[i]) / population;
           bootUsage.add(new Double(usage));
           temp += usage;
           custUsageInfo.setCustomerUsageProjectionMap(customerName, index, usage);
           custUsageInfo.setCustomerUsageMap(customerName, i+OFFSET, usage);
       }

       Double maxBootUsage = Collections.max(bootUsage);
       Double minBootUsage = Collections.min(bootUsage);
       Double avgBootUsage = temp / dataLength;

       custUsageInfo.setCustomerBootUsageMap(customerName, bootUsage);
       custUsageInfo.setCustomerAvgUsageMap(customerName, avgBootUsage);
       custUsageInfo.setCustomerMaxUsageMap(customerName, maxBootUsage);
       custUsageInfo.setCustomerMinUsageMap(customerName, minBootUsage);

       log.info("Customer Boot Strap information loaded from Message Manager -- " + customerName);
    }

    /**
     * Receives a MarketBootstrapData message, reporting usage and prices
     * for the bootstrap period. We record the overall weighted mean price,
     * as well as the mean price and usage for a week.
     */
    public synchronized void handleMessage (MarketBootstrapData mbd)
    {
        Integer dataLength = mbd.getMwh().length;
        Double clearedQuantity = 0.0;
        Double clearingPrice = 0.0;

        for(int i=0; i < dataLength; i++)
        {
            clearedQuantity = mbd.getMwh()[i];
            clearingPrice = mbd.getMarketPrice()[i];
            clearedTradeInformation.setClearedTradebyMessageTimeslot(i+24, i+24,clearingPrice, clearedQuantity);
        }
    }


    /**
     * Handles a DistributionTransaction - charges for transporting power
     */
    public synchronized void handleMessage (DistributionTransaction dt)
    {
        distributionInformation.setDistributionTransaction(dt.getPostedTimeslotIndex(), dt.getKWh(), dt.getCharge());
        //System.out.println("Quantity : " + dt.getKWh() + ", Charge : " + dt.getCharge());
    }

    /**
     * Handles a Distribution Report - charges for transporting power
     */

    public synchronized void handleMessage(DistributionReport dr)
    {
        int timeslot = dr.getTimeslot();
        distributionInformation.setTotalConsumption(timeslot, dr.getTotalConsumption());
        distributionInformation.setTotalProduction(timeslot, dr.getTotalProduction());
        //System.out.println("Total Consumption : " + dr.getTotalConsumption() + ", Total Production : " + dr.getTotalProduction());
    }

    /**
     * Handles a Balancing Transaction - charges for imbalance
     */
    public synchronized void handleMessage (BalancingTransaction bt)
    {
        //System.out.println("Broker's Imbalance : " + bt.getKWh() + " , Charge : " + bt.getCharge());
        balancingMarketInformation.setBalancingTransaction(bt.getPostedTimeslotIndex(), bt.getKWh(), bt.getCharge());
    }

    /**
     * Handles a Balancing market Report - charges for transporting power
     */

    public synchronized void handleMessage(BalanceReport br)
    {
        int timeslot = br.getTimeslotIndex();
        //System.out.println("Net Imbalance : " + br.getNetImbalance());
        balancingMarketInformation.setNetImbalance(timeslot, br.getNetImbalance());
    }

    /**
     * Handles a CapacityTransaction - a charge for contribution to overall
     * peak demand over the recent past.
     */
    public synchronized void handleMessage (CapacityTransaction ct)
    {
        capacityTransactionInformation.setCapacityTransaction(ct.getPostedTimeslotIndex(), ct.getPeakTimeslot(), ct.getThreshold(),ct.getKWh(), ct.getCharge());
        log.info("Capacity tx: " + ct.getCharge());
    }

    /**
     * CashPosition updates our current bank balance.
     */
    public void handleMessage (CashPosition cp)
    {
        Double cash = cp.getBalance();
        int timeslot = cp.getPostedTimeslotIndex();
        cashPositionInformation.setCashPosition(timeslot, cash);
        log.info("Cash position: " + cash);
        //System.out.println("CashPosition : " + cash);
    }

    /**
     * BankTransaction represents an interest payment. Value is positive for
     * credit, negative for debit.
     */
    public void handleMessage (BankTransaction btx)
    {
        cashPositionInformation.setBankInterest(btx.getPostedTimeslotIndex(), btx.getAmount());
        //System.out.println("Bank Interest : " + btx.getAmount());
    }


    /**
     * MarketPosition information
     */

    public void handleMessage(MarketPosition mp)
    {
        marketPositionInformation.setMarketPosition(mp.getTimeslotIndex(), mp.getOverallBalance());
        brokerContext.getBroker().addMarketPosition(mp, mp.getTimeslotIndex());
        System.out.println("Timeslot : " + mp.getTimeslotIndex() + ", MarketPosition : " + mp.getOverallBalance());
    }

    /**
     * MarketTransaction information
     */

    public void handleMessage(MarketTransaction mt)
    {
        //System.out.println("MarketTransaction Message :: Timeslot : " + mt.getTimeslotIndex() + ", MCP : " + mt.getPrice() + ", Cleared Quantity : " + mt.getMWh());
        Integer messageTimeslot = mt.getPostedTimeslotIndex();
        Integer executionTimeslot = mt.getTimeslotIndex();
        marketTransactionInformation.setMarketTransactionInformationbyExectionTimeslot(executionTimeslot, messageTimeslot, mt.getPrice(), mt.getMWh());
        marketTransactionInformation.setMarketTransactionInformationbyMessageTimeslot(messageTimeslot, executionTimeslot, mt.getPrice(), mt.getMWh());
        wholesaleMarketInformation.setWholesaleMarketCostMap(executionTimeslot, (mt.getPrice()*mt.getMWh()));
    }


    /**
     * Cleared Trade information
     */
    public void handleMessage(ClearedTrade ct)
    {
        System.out.println("ClearedTrade Message :: Timeslot : " + ct.getTimeslotIndex() + ", MCP : " + ct.getExecutionPrice() + ", Cleared Quantity : " + ct.getExecutionMWh());
        Integer messageTimeslot = timeslotRepo.getTimeslotIndex(ct.getDateExecuted());
        Integer executionTimeslot = ct.getTimeslotIndex();
        clearedTradeInformation.setClearedTradebyMessageTimeslot(messageTimeslot, executionTimeslot, ct.getExecutionPrice(), ct.getExecutionMWh());
        clearedTradeInformation.setClearedTradebyExecutionTimeslot(executionTimeslot, messageTimeslot, ct.getExecutionPrice(), ct.getExecutionMWh());
        wholesaleMarketInformation.setAvgMCP(executionTimeslot, ct.getExecutionPrice());
        wholesaleMarketInformation.setTotalClearedQuantity(executionTimeslot, ct.getExecutionMWh());
    }

    /**
     * Order book information
     */
    public void handleMessage(Orderbook ob)
    {
        //System.out.println(ob.getBids());
        //System.out.println(ob.getAsks());
        Integer messageTimeslot = timeslotRepo.getTimeslotIndex(ob.getDateExecuted());
        Integer executionTimeslot = ob.getTimeslotIndex();
        orderBookInformation.setOrderBookInformationbyMessageTimeslot(messageTimeslot, executionTimeslot, ob.getClearingPrice(), ob.getBids(), ob.getAsks());
        orderBookInformation.setFirstUnclearedBidInformation(messageTimeslot, executionTimeslot, ob.getBids().iterator().next().getLimitPrice(), ob.getBids().iterator().next().getMWh());
        orderBookInformation.setFirstUnclearedAskInformation(messageTimeslot, executionTimeslot, ob.getAsks().iterator().next().getLimitPrice(), ob.getAsks().iterator().next().getMWh());
    }

    /**
     * Handles a BalancingControlEvent, sent when a BalancingOrder is
     * exercised by the DU.
     */
    public synchronized void handleMessage (BalancingControlEvent bce)
    {
        log.info("BalancingControlEvent " + bce.getKwh());
    }

    public synchronized void handleMessage(TimeslotComplete ts)
    {
      int currentTimeslot = ts.getTimeslotIndex();
      System.out.println("From TimeslotComplete : " + currentTimeslot);

      /*try
      {
        if(currentTimeslot == 362)
        {
          String col0 = "Participant_Brokers_Info";
          DBCollection collection0 = mongoDatabase.getCollection(col0);

          DBObject document0 = new BasicDBObject();

          document0.put("Game_Name", bootFile);
          document0.put("isAgentUDE17", brokers.contains("AgentUDE17") ? 1 : 0);
          document0.put("isBunnie", brokers.contains("Bunnie") ? 1 : 0);
          document0.put("isMaxon16", brokers.contains("maxon16") ? 1 : 0);
          document0.put("isSampleBroker", brokers.contains("Sample") ? 1 : 0);
          document0.put("isSPOT17", brokers.contains("SPOT") ? 1 : 0);
          document0.put("isTacTex", brokers.contains("TacTex") ? 1 : 0);
          document0.put("isVidyutVanika", brokers.contains("VidyutVanika") ? 1 : 0);

          collection0.insert(document0);
        }
      }
      catch(Exception e){}
      ////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        String col1 = "Calendar_Info";
        DBCollection collection1 = mongoDatabase.getCollection(col1);

        DateTime currentDate = timeslotRepo.getDateTimeForIndex(currentTimeslot);

        Integer monthOfYearCurrent = currentDate.getMonthOfYear();
        Integer dayOfMonthCurrent = currentDate.getDayOfMonth();
        Integer dayOfWeekCurrent = currentDate.getDayOfWeek();
        Integer hourOfDayCurrent = currentDate.getHourOfDay();

        DBObject document1 = new BasicDBObject();

        document1.put("Game_Name", bootFile);
        document1.put("Date", currentDate.toString());
        document1.put("Timeslot", currentTimeslot);
        document1.put("Month_of_Year", monthOfYearCurrent);
        document1.put("Day_of_Month", dayOfMonthCurrent);
        document1.put("Day_of_Week", dayOfWeekCurrent);
        document1.put("Hour_of_Day", hourOfDayCurrent);

        collection1.insert(document1);
      }
      catch(Exception e){} */
      //////////////////////////////////////////////////////////////////////////////////////////////////////

      int actualBiddingTimeslot = currentTimeslot - 1;
      DateTime actualBiddingDate = timeslotRepo.getDateTimeForIndex(actualBiddingTimeslot);

      /*try
      {
        String col2 = "Calendar_Forecast_Info";
        DBCollection collection2 = mongoDatabase.getCollection(col2);

        for(int proximity = 1; proximity <= 24; proximity++)
        {
          int executionTimeslot = actualBiddingTimeslot + proximity;
          DateTime executionDate = timeslotRepo.getDateTimeForIndex(executionTimeslot);

          Integer monthOfYear = executionDate.getMonthOfYear();
          Integer dayOfMonth = executionDate.getDayOfMonth();
          Integer dayOfWeek = executionDate.getDayOfWeek();
          Integer hourOfDay = executionDate.getHourOfDay();

          DBObject document2 = new BasicDBObject();

          document2.put("Game_Name", bootFile);
          document2.put("Actual_Bidding_Date", actualBiddingDate.toString());
          document2.put("Execution_Date", executionDate.toString());
          document2.put("Bidding_Timeslot", actualBiddingTimeslot);
          document2.put("Execution_Timeslot", executionTimeslot);
          document2.put("Proximity", proximity);
          document2.put("Month_of_Year_FT", monthOfYear);
          document2.put("Day_of_Month_FT", dayOfMonth);
          document2.put("Day_of_Week_FT", dayOfWeek);
          document2.put("Hour_of_Day_FT", hourOfDay);

          collection2.insert(document2);
        }
      }
      catch(Exception e){}
      ////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        String col3 = "WeatherReport_Info";
        DBCollection collection3 = mongoDatabase.getCollection(col3);

        Double temperatureCurrent = weatherInformation.getWeatherReport(currentTimeslot).getTemperature();
        Double cloudCoverCurrent = weatherInformation.getWeatherReport(currentTimeslot).getCloudCover();
        Double windSpeedCurrent = weatherInformation.getWeatherReport(currentTimeslot).getWindSpeed();
        Double windDirectionCurrent = weatherInformation.getWeatherReport(currentTimeslot).getWindDirection();

        DBObject document3 = new BasicDBObject();

        document3.put("Game_Name", bootFile);
        document3.put("Timeslot", currentTimeslot);
        document3.put("Temperature", temperatureCurrent);
        document3.put("Cloud_Cover", cloudCoverCurrent);
        document3.put("Wind_Direction", windDirectionCurrent);
        document3.put("Wind_Speed", windSpeedCurrent);

        collection3.insert(document3);
      }
      catch(Exception e){}
      ////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        String col4 = "WeatherForecast_Info";
        DBCollection collection4 = mongoDatabase.getCollection(col4);

        for(int proximity = 1; proximity <= 24; proximity++)
        {
          int executionTimeslot = actualBiddingTimeslot + proximity;

          Double temperatureForecasted = weatherInformation.getWeatherForecast(actualBiddingTimeslot).getPredictions().get(proximity-1).getTemperature();
          Double cloudCoverForecasted = weatherInformation.getWeatherForecast(actualBiddingTimeslot).getPredictions().get(proximity-1).getCloudCover();
          Double windSpeedForecasted = weatherInformation.getWeatherForecast(actualBiddingTimeslot).getPredictions().get(proximity-1).getWindSpeed();
          Double windDirectionForecasted = weatherInformation.getWeatherForecast(actualBiddingTimeslot).getPredictions().get(proximity-1).getWindDirection();

          DBObject document4 = new BasicDBObject();

          document4.put("Game_Name", bootFile);
          document4.put("Bidding_Timeslot", actualBiddingTimeslot);
          document4.put("Execution_Timeslot", executionTimeslot);
          document4.put("Temperature_FT", temperatureForecasted);
          document4.put("Cloud_Cover_FT", cloudCoverForecasted);
          document4.put("Wind_Direction_FT", windDirectionForecasted);
          document4.put("Wind_Speed_FT", windSpeedForecasted);

          collection4.insert(document4);
        }
      }
      catch(Exception e){} */
      /////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        String col5 = "ClearedTrade_Info";
        DBCollection collection5 = mongoDatabase.getCollection(col5);

        for(int proximity = 1; proximity <= 24; proximity++)
        {
          int executionTimeslot = actualBiddingTimeslot + proximity;

          Double MCP = clearedTradeInformation.getClearedTradebyMessageTimeslot(currentTimeslot).get(executionTimeslot).getKey();
          Double netClearedQuantiy = clearedTradeInformation.getClearedTradebyMessageTimeslot(currentTimeslot).get(executionTimeslot).getValue();

          DBObject document5 = new BasicDBObject();

          document5.put("Game_Name", bootFile);
          document5.put("Bidding_Timeslot", actualBiddingTimeslot);
          document5.put("Execution_Timeslot", executionTimeslot);
          document5.put("MCP", MCP);
          document5.put("Net_Cleared_Quantity", netClearedQuantiy);

          collection5.insert(document5);
        }
      }
      catch(Exception e){} 
      /////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        String col6 = "MarketTransaction_Info";
        DBCollection collection6 = mongoDatabase.getCollection(col6);

        Map<Integer, Pair<Double, Double>> MTI = marketTransactionInformation.getMarketTransactionInformationbyMessageTimeslot(currentTimeslot);

        for(Map.Entry<Integer, Pair<Double, Double>> message : MTI.entrySet())
        {
          DBObject document6 = new BasicDBObject();

          document6.put("Game_Name", bootFile);
          document6.put("Bidding_Timeslot", currentTimeslot-1);
          document6.put("Execution_Timeslot", message.getKey());
          document6.put("MCP", message.getValue().getKey());
          document6.put("Broker's_Cleared_Quantity", message.getValue().getValue());

          collection6.insert(document6);
        }
      }
      catch(Exception e){}
      //////////////////////////////////////////////////////////////////////////////////////////////////////

      /*try
      {
        String col7 = "FirstUnclearedBidAsk_Info";
        DBCollection collection7 = mongoDatabase.getCollection(col7);

        Map<Integer, Pair<Double, Double>> FUA = orderBookInformation.getFirstUnclearedAskInformation(currentTimeslot);
        Map<Integer, Pair<Double, Double>> FUB = orderBookInformation.getFirstUnclearedBidInformation(currentTimeslot);

        for(int proximity = 1; proximity <= 24; proximity++)
        {
          int executionTimeslot = actualBiddingTimeslot + proximity;

          if((FUA.get(executionTimeslot) != null) && (FUB.get(executionTimeslot) != null))
          {
            DBObject document7 = new BasicDBObject();

            document7.put("Game_Name", bootFile);
            document7.put("Bidding_Timeslot", actualBiddingTimeslot);
            document7.put("Execution_Timeslot", executionTimeslot);
            document7.put("First_Uncleared_Ask_Price", FUA.get(executionTimeslot).getKey());
            document7.put("First_Uncleared_Ask_Quantity", FUA.get(executionTimeslot).getValue());
            document7.put("First_Uncleared_Bid_Price", FUB.get(executionTimeslot).getKey());
            document7.put("First_Uncleared_Bid_Quantity", FUB.get(executionTimeslot).getValue());

            collection7.insert(document7);
          }
        }
      }
      catch(Exception e){}
      /////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        String col8 = "BalancingTransaction_and_Report_Info";
        DBCollection collection8 = mongoDatabase.getCollection(col8);

        Double netImbalance = balancingMarketInformation.getNetImbalance(currentTimeslot);
        Double brokerImbalance = balancingMarketInformation.getBalancingTransaction(currentTimeslot).getKey();
        Double brokerImbalancePenalty = balancingMarketInformation.getBalancingTransaction(currentTimeslot).getValue();

        DBObject document8 = new BasicDBObject();

        document8.put("Game_Name", bootFile);
        document8.put("Timeslot", currentTimeslot);
        document8.put("Broker's_Imbalance", brokerImbalance);
        document8.put("Broker's_Imbalance_Charge", brokerImbalancePenalty);
        document8.put("Net_Imbalance", netImbalance);

        collection8.insert(document8);
      }
      catch(Exception e){}
      ///////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        String col9 = "DistributionTransaction_and_Report_Info";
        DBCollection collection9 = mongoDatabase.getCollection(col9);

        Double totalProduction = distributionInformation.getTotalProduction(currentTimeslot);
        Double totalConsumption = distributionInformation.getTotalConsumption(currentTimeslot);
        Double distriubutionKWh = distributionInformation.getDistributionTransaction(currentTimeslot).getKey();
        Double distriubutionCharge = distributionInformation.getDistributionTransaction(currentTimeslot).getValue();

        DBObject document9 = new BasicDBObject();

        document9.put("Game_Name", bootFile);
        document9.put("Timeslot", currentTimeslot);
        document9.put("Total_Production", totalProduction);
        document9.put("Total_Consumption", totalConsumption);
        document9.put("Distribution_KWh", distriubutionKWh);
        document9.put("Distribution_Charge", distriubutionCharge);

        collection9.insert(document9);
      }
      catch(Exception e){}
      /////////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        String col10 = "Aggregated_ClearedTrade_Info";
        DBCollection collection10 = mongoDatabase.getCollection(col10);

        Double avgMCP = wholesaleMarketInformation.getAvgMCP(currentTimeslot);
        Double totalClearedQuantity = wholesaleMarketInformation.getTotalClearedQuantity(currentTimeslot);

        DBObject document10 = new BasicDBObject();

        document10.put("Game_Name", bootFile);
        document10.put("Timeslot", currentTimeslot);
        document10.put("Avg_MCP", avgMCP);
        document10.put("Total_Cleared_Quantity", totalClearedQuantity);

        collection10.insert(document10);
      }
      catch(Exception e){} */
      ///////////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        String col11 = "Cash_and_Market_Position_Info";
        DBCollection collection11 = mongoDatabase.getCollection(col11);

        Double cashPosition = cashPositionInformation.getCashPosition(currentTimeslot);
        Double bankInterest = cashPositionInformation.getBankInterest(currentTimeslot);
        Double marketPosition = marketPositionInformation.getMarketPosition(currentTimeslot);

        DBObject document11 = new BasicDBObject();

        document11.put("Game_Name", bootFile);
        document11.put("Timeslot", currentTimeslot);
        document11.put("Cash_Position", cashPosition);
        document11.put("Bank_Interest", bankInterest);
        document11.put("Market_Position", marketPosition);

        collection11.insert(document11);
      }
      catch(Exception e){}
      ///////////////////////////////////////////////////////////////////////////////////////////////////////////

      /*try
      {
        String col12 = "CapacityTransaction_Info";
        DBCollection collection12 = mongoDatabase.getCollection(col12);

        List<CapacityTransactionMessage> capacityTransaction = capacityTransactionInformation.getCapacityTransaction(currentTimeslot);

        if(capacityTransaction != null)
        {
          for(CapacityTransactionMessage message : capacityTransaction)
          {
            DBObject document12 = new BasicDBObject();

            document12.put("Game_Name", bootFile);
            document12.put("Timeslot", currentTimeslot);
            document12.put("Peak_Timeslot", message.peakTimeslot);
            document12.put("Threshold", message.threshold);
            document12.put("Exceeded_MWh", message.exceededKWh);
            document12.put("Penalty", message.charge);

            collection12.insert(document12);
          }
        }
      }
      catch(Exception e){}
      ///////////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        Map<String, Map<Integer, UsageRecord>> consumptionInfoMap = custUsageInfo.getCustomerAcutalUsageMap();

        for (Map.Entry<String, Map<Integer, UsageRecord>> om: consumptionInfoMap.entrySet())
        {
            String customerName = om.getKey();
            Map<Integer, UsageRecord> im = om.getValue();

            Double maxUsage = custUsageInfo.getCustomerMaxUsage(customerName);
            Double minUsage = custUsageInfo.getCustomerMinUsage(customerName);
            Double avgUsage = custUsageInfo.getCustomerAvgUsageMap(customerName);
            Double usage = im.get(currentTimeslot).getConsumptionPerPopulation();

            Double tariff = im.get(currentTimeslot).getUnitTariff();

            DBCollection collection = mongoDatabase.getCollection(customerName);

            DBObject document = new BasicDBObject();

            document.put("Game Name", bootFile);
            document.put("Timeslot", currentTimeslot);
            document.put("Max_Usage", maxUsage);
            document.put("Min_Usage", minUsage);
            document.put("Avg_Usage", avgUsage);
            document.put("Tariff", tariff);
            document.put("Usage Per Population", usage);

            collection.insert(document);
        }
      }
      catch(Exception e)
      {
      }*/
    }

    // public synchronized void handleMessage(SimEnd se)
    // {
    //   int timeslot = timeslotRepo.currentTimeslot().getSerialNumber();

    //   Double wholesaleMarketCost = wholesaleMarketInformation.getCumulativeWholesaleMarketCostMap();
    //   Double avgMarketOrderQuantity = wholesaleMarketInformation.getAvgWholesaleMarketOrderMap();

    //   try
    //   {
    //     FileWriter fw = new FileWriter("GameInfo_Baseline.txt", true);
    //     fw.write("Broker : " + brokerContext.getBrokerUsername() + " :: Last Timeslot : " + timeslot + " :: WholesaleMarketCost : " + wholesaleMarketCost + " :: AvgMarketOrderQuantity : " + (avgMarketOrderQuantity/(timeslot-360)) + "\n");
    //     fw.close();
    //   }
    //   catch(Exception e)
    //   {
    //     e.printStackTrace();
    //   }
    // }

    @Override // from Activatable
    public synchronized void activate (int timeslotIndex)
    {
        //System.out.println("MarketPosition at time " + (timeslotIndex-1) + " : " + marketPositionInformation.getMarketPosition(timeslotIndex-1));
        log.info(" Activate from Message Manager " + timeslotIndex);
    }

    /**
     *
     * @return GameInformation object for use by other classes
     */
    public GameInformation getGameInformation()
    {
        return gameInformation;
    }

    /**
     *
     * @return WeatherInformation object for use by other classes
     */
    public WeatherInformation getWeatherInformation()
    {
        return weatherInformation;
    }

    /**
     *
     * @return Distribution object for use by other classes
     */
    public DistributionInformation getDistributionInformation()
    {
        return distributionInformation;
    }

    /**
     *
     * @return BalancingMarketInformation object for use by other classes
     */
    public BalancingMarketInformation getBalancingMarketInformation()
    {
        return balancingMarketInformation;
    }

    /**
     *
     * @return CapacityTransaction Information object for use by other classes
     */
    public CapacityTransactionInformation getCapacityTransactionInformation()
    {
        return capacityTransactionInformation;
    }

    /**
     *
     * @return CashPosition Information object for use by other classes
     */
    public CashPositionInformation getCashPositionInformation()
    {
        return cashPositionInformation;
    }
    /**
     *
     * @return MarketPosition Information object for use by other classes
     */
    public MarketPositionInformation getMarketPositionInformation()
    {
        return marketPositionInformation;
    }
    /**
     *
     * @return Market transaction Information object for use by other classes
     */
    public MarketTransactionInformation getMarketTransactionInformation()
    {
        return marketTransactionInformation;
    }

     /* @return Order book Information object for use by other classes
     */
    public OrderBookInformation getOrderBookInformation()
    {
        return orderBookInformation;
    }

    /* @return Order book Information object for use by other classes
    */
   public ClearedTradeInformation getClearTradeInformation()
   {
       return clearedTradeInformation;
   }

   /**
     * @return
     */

    public CustomerUsageInformation getCustomerUsageInformation()
    {
        return custUsageInfo;
    }

    /**
      * @return
      */

     public WholesaleMarketInformation getWholesaleMarketInformation()
     {
         return wholesaleMarketInformation;
     }
}
