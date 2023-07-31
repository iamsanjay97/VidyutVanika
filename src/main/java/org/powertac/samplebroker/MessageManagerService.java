/*
 * Copyright (c) 2019-2020 by the original author
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.powertac.samplebroker;

import java.util.Random;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.Collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.powertac.common.*;
import org.powertac.common.msg.*;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.information.CustomerUsageInformation;
import org.powertac.samplebroker.information.UsageRecord;
import org.powertac.samplebroker.information.WholesaleMarketInformation;
import org.powertac.samplebroker.messages.*;
import org.powertac.samplebroker.messages.CapacityTransactionInformation.CapacityTransactionMessage;
import org.powertac.samplebroker.util.Helper;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.MessageManager;
import org.powertac.samplebroker.interfaces.Initializable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoDatabase;

import javafx.util.Pair;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.joda.time.DateTime; 


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

    NetDemandRecord netDemandRecord;

    Map<Integer, Double> netDemand;

    @ConfigurableValue(valueType = "Double", description = "Capacity Transaction Gamma Value")
    private static final Double CAPACITY_TRANSACTION_GAMMA = 1.22;

    //Default broker consumption tariff
    Double dfConsumptionRate = 0.0;

    //Default broker consumption tariff
    Double dfProductionRate = 0.0;

    double alpha = 0.3;

    //MongoDB client and Database
    MongoClient mongoClient;

    //MongoDB database
    MongoDatabase mongoDatabase;

    String dbname; 

    List<String> brokers;

    //Dateformat pattern
    String dateFormat = "EEEEE MMMMM yyyy HH:mm";

    int[] blocks = {5, 9, 10, 16, 17, 22, 23, 4};

    int OFFSET = 24;

    Random rand;

    private List<String> listOfTargetedConsumers;
    private List<String> listOfTargetedProducers;

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
        dbname = "PowerTAC2022_DDPG_Testing";

        netDemand = new HashMap<>();
        netDemandRecord = new NetDemandRecord();

        rand = new Random();

        listOfTargetedConsumers = Helper.getListOfTargetedConsumers();
        listOfTargetedProducers = Helper.getListOfTargetedProducers();

        try
        {
            mongoClient = new MongoClient("localhost", 27017);
            mongoDatabase = mongoClient.getDatabase(dbname);
        }
        catch(Exception e)
        {
            log.warn("Mongo DB connection Exception " + e.toString());
        }
        log.info(" Connected to Database " + dbname + " -- Broker Initialize");
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
           bootUsage.add(Double.valueOf(usage));
           temp += usage;
           custUsageInfo.setCustomerUsageProjectionMap(customerName, index, usage);
           custUsageInfo.setCustomerUsageMap(customerName, i+OFFSET, usage);

           Double demand = netDemand.get(i);

           if(demand != null)
           {
            demand -= cbd.getNetUsage()[i];
            netDemand.put(i, demand);
           }
           else
            netDemand.put(i, -cbd.getNetUsage()[i]);
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

        for (int i = 0; i < mbd.getMwh().length; i++) 
        {
          if (Math.abs(mbd.getMwh()[i]) > 0.0) {
            wholesaleMarketInformation.setMeanMarketPrice(-Math.abs(mbd.getMarketPrice()[i]), Math.abs(mbd.getMwh()[i]));        
          }
        }

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
        // System.out.println("Total Consumption : " + dr.getTotalConsumption() + ", Total Production : " + dr.getTotalProduction());

        netDemand.put(timeslot, dr.getTotalConsumption() - dr.getTotalProduction());
        netDemandRecord.localProduceConsumeNetDemand((dr.getTotalConsumption() - dr.getTotalProduction()), timeslot);

        List<Double> listOfNetDemands = getListOfNetDemands();

        Double mean = calculateMean(listOfNetDemands);
        Double stdev = calculateStdev(listOfNetDemands, mean);

        Double currentDemand = dr.getTotalConsumption() - dr.getTotalProduction();
        Double threshold = mean + CAPACITY_TRANSACTION_GAMMA * stdev;

        Double exceededThreshold = Math.max(0.0, (currentDemand - threshold));
        capacityTransactionInformation.setExceededThresholdMap(timeslot, exceededThreshold);
    }

    /**
     * Handles a Balancing Transaction - charges for imbalance
     */
    public synchronized void handleMessage (BalancingTransaction bt)
    {
        System.out.println("Broker's Imbalance : " + bt.getKWh() + " , Charge : " + bt.getCharge());
        balancingMarketInformation.setBalancingTransaction(bt.getPostedTimeslotIndex(), bt.getKWh(), bt.getCharge());

        Double mWh = bt.getKWh() / 1e3;
        Double chargePerMWh = bt.getCharge() / Math.abs(mWh);

        balancingMarketInformation.setAvgBalancingPrice(chargePerMWh);

        if(mWh < 0)
        {
          if(bt.getCharge() > 0)
            return;
          
          balancingMarketInformation.setAvgBuyBalancingPrice(chargePerMWh);
        }

        if ((Math.abs(mWh) > 0.0) && (bt.getCharge() < 0.0)) 
          wholesaleMarketInformation.setMeanMarketPrice(chargePerMWh, mWh);
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
        System.out.println(ct.getPostedTimeslotIndex() + " : " + ct.getPeakTimeslot() + " : " + ct.getThreshold() + " : " + ct.getKWh() + " : " + ct.getCharge());
        capacityTransactionInformation.setCapacityTransaction(ct.getPostedTimeslotIndex(), ct.getPeakTimeslot(), ct.getThreshold(),ct.getKWh(), ct.getCharge());
        log.info("Capacity tx: " + ct.getCharge());
        double ctq = ct.getKWh() / 1000.0;
        double ctp = ct.getCharge() / Math.abs(ctq);
        if ((Math.abs(ctq) > 0.0) && (ct.getCharge() < 0.0)) 
          wholesaleMarketInformation.setMeanMarketPrice(ctp, ctq);
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
        System.out.println("CashPosition : " + cash);
    }

    /**
     * BankTransaction represents an interest payment. Value is positive for
     * credit, negative for debit.
     */
    public void handleMessage (BankTransaction btx)
    {
        cashPositionInformation.setBankInterest(btx.getPostedTimeslotIndex(), btx.getAmount());
        // System.out.println("Bank Interest : " + btx.getAmount());
    }

    /**
     * MarketPosition information
     */

    public void handleMessage(MarketPosition mp)
    {
        marketPositionInformation.setMarketPosition(mp.getTimeslotIndex(), mp.getOverallBalance());
        brokerContext.getBroker().addMarketPosition(mp, mp.getTimeslotIndex());
        if((timeslotRepo.currentSerialNumber()+1 == mp.getTimeslotIndex()) || (timeslotRepo.currentSerialNumber() == mp.getTimeslotIndex()))
          System.out.println("Timeslot : " + mp.getTimeslotIndex() + ", MarketPosition : " + mp.getOverallBalance());
    }

    /**
     * MarketTransaction information
     */

    public void handleMessage(MarketTransaction mt)
    {
        //if(Math.abs(mt.getMWh()) > 0.01)
        // System.out.println("MarketTransaction Message :: Timeslot : " + mt.getTimeslotIndex() + ", MCP : " + mt.getPrice() + ", Cleared Quantity : " + mt.getMWh());

        Integer messageTimeslot = mt.getPostedTimeslotIndex();
        Integer executionTimeslot = mt.getTimeslotIndex();
        marketTransactionInformation.setMarketTransactionInformationbyExectionTimeslot(executionTimeslot, messageTimeslot, mt.getPrice(), mt.getMWh());
        marketTransactionInformation.setMarketTransactionInformationbyMessageTimeslot(messageTimeslot, executionTimeslot, mt.getPrice(), mt.getMWh());
        marketTransactionInformation.setBrokerWholesaleCostMap(executionTimeslot, mt.getPrice(), mt.getMWh());
        wholesaleMarketInformation.setAvgMCP(executionTimeslot, Math.abs(mt.getPrice()), Math.abs(mt.getMWh()));
        wholesaleMarketInformation.setTotalClearedQuantity(executionTimeslot, mt.getMWh());
        // wholesaleMarketInformation.setMeanMarketPrice(mt.getPrice(), mt.getMWh());
    }

    /**
     * Cleared Trade information
     */
    public void handleMessage(ClearedTrade ct)
    {
        //System.out.println("ClearedTrade Message :: Timeslot : " + ct.getTimeslotIndex() + ", MCP : " + ct.getExecutionPrice() + ", Cleared Quantity : " + ct.getExecutionMWh());
        Integer messageTimeslot = timeslotRepo.getTimeslotIndex(ct.getDateExecuted());
        Integer executionTimeslot = ct.getTimeslotIndex();
        clearedTradeInformation.setClearedTradebyMessageTimeslot(messageTimeslot, executionTimeslot, ct.getExecutionPrice(), ct.getExecutionMWh());
        clearedTradeInformation.setClearedTradebyExecutionTimeslot(executionTimeslot, messageTimeslot, ct.getExecutionPrice(), ct.getExecutionMWh());
        wholesaleMarketInformation.setAvgMCP(executionTimeslot, ct.getExecutionPrice(), ct.getExecutionMWh());
        if (Math.abs(ct.getExecutionMWh()) > 0.0)   // price should be negative
          wholesaleMarketInformation.setMeanMarketPrice(-Math.abs(ct.getExecutionPrice()), Math.abs(ct.getExecutionMWh())); 
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
      String bootFile = gameInformation.getName();

      /* try
      {
        if(currentTimeslot == 362)
        {
          String col0 = "Participant_Brokers_Info";
          MongoCollection<Document> collection0 = mongoDatabase.getCollection(col0);

          Document document0 = new Document();

          document0.put("Game_Name", bootFile);
          document0.put("isAgentUDE17", brokers.contains("AgentUDE17") ? 1 : 0);
          document0.put("isBunnie", brokers.contains("Bunnie") ? 1 : 0);
          document0.put("isMaxon16", brokers.contains("maxon16") ? 1 : 0);
          document0.put("isSampleBroker", brokers.contains("Sample") ? 1 : 0);
          document0.put("isSPOT17", brokers.contains("SPOT") ? 1 : 0);
          document0.put("isTacTex", brokers.contains("TacTex") ? 1 : 0);
          document0.put("isVidyutVanika", brokers.contains("VidyutVanika") ? 1 : 0);

          collection0.insertOne(document0);
        }
      }
      catch(Exception e){} */
      ////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        String col1 = "Calendar_Info";
        MongoCollection<Document> collection1 = mongoDatabase.getCollection(col1);

        DateTime currentDate = timeslotRepo.getDateTimeForIndex(currentTimeslot);

        Integer monthOfYearCurrent = currentDate.getMonthOfYear();
        Integer dayOfMonthCurrent = currentDate.getDayOfMonth();
        Integer dayOfWeekCurrent = currentDate.getDayOfWeek();
        Integer hourOfDayCurrent = currentDate.getHourOfDay();

        Document document1 = new Document();

        document1.put("Game_Name", bootFile);
        document1.put("Date", currentDate.toString());
        document1.put("Timeslot", currentTimeslot);
        document1.put("Month_of_Year", monthOfYearCurrent);
        document1.put("Day_of_Month", dayOfMonthCurrent);
        document1.put("Day_of_Week", dayOfWeekCurrent);
        document1.put("Hour_of_Day", hourOfDayCurrent);

        collection1.insertOne(document1);
      }
      catch(Exception e){} */
      //////////////////////////////////////////////////////////////////////////////////////////////////////

      int actualBiddingTimeslot = currentTimeslot - 1;
      DateTime actualBiddingDate = timeslotRepo.getDateTimeForIndex(actualBiddingTimeslot);

      /* try
      {
        String col2 = "Calendar_Forecast_Info";
        MongoCollection<Document> collection2 = mongoDatabase.getCollection(col2);

        for(int proximity = 1; proximity <= 24; proximity++)
        {
          int executionTimeslot = actualBiddingTimeslot + proximity;
          DateTime executionDate = timeslotRepo.getDateTimeForIndex(executionTimeslot);

          Integer monthOfYear = executionDate.getMonthOfYear();
          Integer dayOfMonth = executionDate.getDayOfMonth();
          Integer dayOfWeek = executionDate.getDayOfWeek();
          Integer hourOfDay = executionDate.getHourOfDay();

          Document document2 = new Document();

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

          collection2.insertOne(document2);
        }
      }
      catch(Exception e){} */
      ////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        String col3 = "WeatherReport_Info";
        MongoCollection<Document> collection3 = mongoDatabase.getCollection(col3);

        Double temperatureCurrent = weatherInformation.getWeatherReport(currentTimeslot).getTemperature();
        Double cloudCoverCurrent = weatherInformation.getWeatherReport(currentTimeslot).getCloudCover();
        Double windSpeedCurrent = weatherInformation.getWeatherReport(currentTimeslot).getWindSpeed();
        Double windDirectionCurrent = weatherInformation.getWeatherReport(currentTimeslot).getWindDirection();

        Document document3 = new Document();

        document3.put("Game_Name", bootFile);
        document3.put("Timeslot", currentTimeslot);
        document3.put("Temperature", temperatureCurrent);
        document3.put("Cloud_Cover", cloudCoverCurrent);
        document3.put("Wind_Direction", windDirectionCurrent);
        document3.put("Wind_Speed", windSpeedCurrent);

        collection3.insertOne(document3);
      }
      catch(Exception e){} */
      ////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        String col4 = "WeatherForecast_Info";
        MongoCollection<Document> collection4 = mongoDatabase.getCollection(col4);

        for(int proximity = 1; proximity <= 24; proximity++)
        {
          int executionTimeslot = actualBiddingTimeslot + proximity;

          Double temperatureForecasted = weatherInformation.getWeatherForecast(actualBiddingTimeslot).getPredictions().get(proximity-1).getTemperature();
          Double cloudCoverForecasted = weatherInformation.getWeatherForecast(actualBiddingTimeslot).getPredictions().get(proximity-1).getCloudCover();
          Double windSpeedForecasted = weatherInformation.getWeatherForecast(actualBiddingTimeslot).getPredictions().get(proximity-1).getWindSpeed();
          Double windDirectionForecasted = weatherInformation.getWeatherForecast(actualBiddingTimeslot).getPredictions().get(proximity-1).getWindDirection();

          Document document4 = new Document();

          document4.put("Game_Name", bootFile);
          document4.put("Bidding_Timeslot", actualBiddingTimeslot);
          document4.put("Execution_Timeslot", executionTimeslot);
          document4.put("Temperature_FT", temperatureForecasted);
          document4.put("Cloud_Cover_FT", cloudCoverForecasted);
          document4.put("Wind_Direction_FT", windDirectionForecasted);
          document4.put("Wind_Speed_FT", windSpeedForecasted);

          collection4.insertOne(document4);
        }
      }
      catch(Exception e){} */
      /////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        String col5 = "ClearedTrade_Info";
        MongoCollection<Document> collection5 = mongoDatabase.getCollection(col5);

        for(int proximity = 1; proximity <= 24; proximity++)
        {
          int executionTimeslot = actualBiddingTimeslot + proximity;

          Double MCP = clearedTradeInformation.getClearedTradebyMessageTimeslot(currentTimeslot).get(executionTimeslot).getKey();
          Double netClearedQuantiy = clearedTradeInformation.getClearedTradebyMessageTimeslot(currentTimeslot).get(executionTimeslot).getValue();

          Document document5 = new Document();

          document5.put("Game_Name", bootFile);
          document5.put("Bidding_Timeslot", actualBiddingTimeslot);
          document5.put("Execution_Timeslot", executionTimeslot);
          document5.put("MCP", MCP);
          document5.put("Net_Cleared_Quantity", netClearedQuantiy);

          collection5.insertOne(document5);
        }
      }
      catch(Exception e){}  */
      /////////////////////////////////////////////////////////////////////////////////////////////////////

      try
      {
        String col6 = "MarketTransaction_Info";
        MongoCollection<Document> collection6 = mongoDatabase.getCollection(col6);

        Map<Integer, Pair<Double, Double>> MTI = marketTransactionInformation.getMarketTransactionInformationbyMessageTimeslot(currentTimeslot);

        for(Map.Entry<Integer, Pair<Double, Double>> message : MTI.entrySet())
        {
          Document document6 = new Document();

          document6.put("Game_Name", bootFile);
          document6.put("Bidding_Timeslot", currentTimeslot-1);
          document6.put("Execution_Timeslot", message.getKey());
          document6.put("MCP", message.getValue().getKey());
          document6.put("Broker's_Cleared_Quantity", message.getValue().getValue());

          collection6.insertOne(document6);
        }
      }
      catch(Exception e){}
      //////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        String col7 = "FirstUnclearedBidAsk_Info";
        MongoCollection<Document> collection7 = mongoDatabase.getCollection(col7);

        Map<Integer, Pair<Double, Double>> FUA = orderBookInformation.getFirstUnclearedAskInformation(currentTimeslot);
        Map<Integer, Pair<Double, Double>> FUB = orderBookInformation.getFirstUnclearedBidInformation(currentTimeslot);

        for(int proximity = 1; proximity <= 24; proximity++)
        {
          int executionTimeslot = actualBiddingTimeslot + proximity;

          if((FUA.get(executionTimeslot) != null) && (FUB.get(executionTimeslot) != null))
          {
            Document document7 = new Document();

            document7.put("Game_Name", bootFile);
            document7.put("Bidding_Timeslot", actualBiddingTimeslot);
            document7.put("Execution_Timeslot", executionTimeslot);
            document7.put("First_Uncleared_Ask_Price", FUA.get(executionTimeslot).getKey());
            document7.put("First_Uncleared_Ask_Quantity", FUA.get(executionTimeslot).getValue());
            document7.put("First_Uncleared_Bid_Price", FUB.get(executionTimeslot).getKey());
            document7.put("First_Uncleared_Bid_Quantity", FUB.get(executionTimeslot).getValue());

            collection7.insertOne(document7);
          }
        }
      }
      catch(Exception e){} */
      /////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        String col8 = "BalancingTransaction_and_Report_Info";
        MongoCollection<Document> collection8 = mongoDatabase.getCollection(col8);

        Double netImbalance = balancingMarketInformation.getNetImbalance(currentTimeslot);
        Double brokerImbalance = balancingMarketInformation.getBalancingTransaction(currentTimeslot).getKey();
        Double brokerImbalancePenalty = balancingMarketInformation.getBalancingTransaction(currentTimeslot).getValue();

        Document document8 = new Document();

        document8.put("Game_Name", bootFile);
        document8.put("Timeslot", currentTimeslot);
        document8.put("Broker's_Imbalance", brokerImbalance);
        document8.put("Broker's_Imbalance_Charge", brokerImbalancePenalty);
        document8.put("Net_Imbalance", netImbalance);

        collection8.insertOne(document8);
      }
      catch(Exception e){} */
      ///////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        String col9 = "DistributionTransaction_and_Report_Info";
        MongoCollection<Document> collection9 = mongoDatabase.getCollection(col9);

        Double totalProduction = distributionInformation.getTotalProduction(currentTimeslot);
        Double totalConsumption = distributionInformation.getTotalConsumption(currentTimeslot);
        Double distriubutionKWh = distributionInformation.getDistributionTransaction(currentTimeslot).getKey();
        Double distriubutionCharge = distributionInformation.getDistributionTransaction(currentTimeslot).getValue();

        Document document9 = new Document();

        document9.put("Game_Name", bootFile);
        document9.put("Timeslot", currentTimeslot);
        document9.put("Total_Production", totalProduction);
        document9.put("Total_Consumption", totalConsumption);
        document9.put("Distribution_KWh", distriubutionKWh);
        document9.put("Distribution_Charge", distriubutionCharge);

        collection9.insertOne(document9);
      }
      catch(Exception e){} */
      /////////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        String col10 = "Aggregated_ClearedTrade_Info";
        MongoCollection<Document> collection10 = mongoDatabase.getCollection(col10);

        Double avgMCP = wholesaleMarketInformation.getAvgMCP(currentTimeslot);
        Double totalClearedQuantity = wholesaleMarketInformation.getTotalClearedQuantity(currentTimeslot);

        Document document10 = new Document();

        document10.put("Game_Name", bootFile);
        document10.put("Timeslot", currentTimeslot);
        document10.put("Avg_MCP", avgMCP);
        document10.put("Total_Cleared_Quantity", totalClearedQuantity);

        collection10.insertOne(document10);
      }
      catch(Exception e){} */
      ///////////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        String col11 = "Cash_and_Market_Position_Info";
        MongoCollection<Document> collection11 = mongoDatabase.getCollection(col11);

        Double cashPosition = cashPositionInformation.getCashPosition(currentTimeslot);
        Double bankInterest = cashPositionInformation.getBankInterest(currentTimeslot);
        Double marketPosition = marketPositionInformation.getMarketPosition(currentTimeslot);

        Document document11 = new Document();

        document11.put("Game_Name", bootFile);
        document11.put("Timeslot", currentTimeslot);
        document11.put("Cash_Position", cashPosition);
        document11.put("Bank_Interest", bankInterest);
        document11.put("Market_Position", marketPosition);

        collection11.insertOne(document11);
      }
      catch(Exception e){} */
      ///////////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        String col12 = "CapacityTransaction_Info";
        MongoCollection<Document> collection12 = mongoDatabase.getCollection(col12);

        List<CapacityTransactionMessage> capacityTransaction = capacityTransactionInformation.getCapacityTransaction(currentTimeslot);

        if(capacityTransaction != null)
        {
          for(CapacityTransactionMessage message : capacityTransaction)
          {
            Document document12 = new Document();

            document12.put("Game_Name", bootFile);
            document12.put("Timeslot", currentTimeslot);
            document12.put("Peak_Timeslot", message.peakTimeslot);
            document12.put("Threshold", message.threshold);
            document12.put("Exceeded_MWh", message.exceededKWh);
            document12.put("Penalty", message.charge);

            collection12.insertOne(document12);
          }
        }
      }
      catch(Exception e){} */
      ///////////////////////////////////////////////////////////////////////////////////////////////////////////

      /* try
      {
        Map<String, Map<Integer, UsageRecord>> consumptionInfoMap = custUsageInfo.getCustomerAcutalUsageMap();

        for (Map.Entry<String, Map<Integer, UsageRecord>> om: consumptionInfoMap.entrySet())
        {
            String customerName = om.getKey();

            if(listOfTargetedConsumers.contains(customerName) || listOfTargetedProducers.contains(customerName))
            {
              Map<Integer, UsageRecord> im = om.getValue();

              Double maxUsage = custUsageInfo.getCustomerMaxUsage(customerName);
              Double minUsage = custUsageInfo.getCustomerMinUsage(customerName);
              Double avgUsage = custUsageInfo.getCustomerAvgUsageMap(customerName);
              Double usage = im.get(currentTimeslot).getConsumptionPerPopulation();

              Double tariff = im.get(currentTimeslot).getUnitTariff();

              MongoCollection<Document> collection13 = mongoDatabase.getCollection(customerName);

              Document document13 = new Document();

              document13.put("Game Name", bootFile);
              document13.put("Timeslot", currentTimeslot);
              document13.put("Max_Usage", maxUsage);
              document13.put("Min_Usage", minUsage);
              document13.put("Avg_Usage", avgUsage);
              document13.put("Tariff", tariff);
              document13.put("Usage Per Population", usage);

              collection13.insertOne(document13);
            }
        }
      }
      catch(Exception e)
      {
      } */
    } 

    @Override // from Activatable
    public synchronized void activate (int timeslotIndex)
    {
      log.info(" Activate from Message Manager " + timeslotIndex);

      if(timeslotIndex == 360)
      {
        for(Map.Entry<Integer, Double> item: netDemand.entrySet())
        {
          netDemandRecord.localProduceConsumeNetDemand(item.getValue(), item.getKey());
        }
      }
    }

    // Net Demand Predictor (Sample Broker's way)
    @Override
    public Double collectNetDemand(Integer timeslot)
    {
      int index = timeslot % brokerContext.getUsageRecordLength();
      return netDemandRecord.getNetDemand(index);
    }

    /**
     * Calculate Capacity Transaction Threshold
     * @param timeslot
     * @return
     */
    public Double calculateThreshold()
    {
      List<Double> listOfNetDemands = getListOfNetDemands();

      Double mean = calculateMean(listOfNetDemands);
      Double stdev = calculateStdev(listOfNetDemands, mean);

      Double threshold = mean + CAPACITY_TRANSACTION_GAMMA * stdev;

      return threshold;
    }

    /**
     *
     * @return Calculates avg of a list
     */
    public Double calculateMean(List<Double> list)
    {
      Double mean = 0.0;

      for(Double item: list)
        mean += item;

      mean /= list.size();

      return mean;
    }

    /**
     *
     * @return Calculates s.d. of a list
     */
    public Double calculateStdev(List<Double> list, Double mean)
    {
      Double stdev = 0.0;

      for(Double item: list)
        stdev += Math.pow((item - mean), 2);

      stdev = Math.sqrt(stdev / list.size());

      return stdev;
    }

    /**
     *
     * @return List of net demands
     */
    public List<Double> getListOfNetDemands()
    {
      List<Double> listOfNetDemand = netDemand.entrySet().stream().map(x -> x.getValue()).collect(Collectors.toList());

      return listOfNetDemand;
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

    class NetDemandRecord
    {
      double[] netDemand;
      double alpha = 0.3;

      /**
       * Creates an empty record
       */
      NetDemandRecord ()
      {
        super();
        this.netDemand = new double[brokerContext.getUsageRecordLength()];
      }

      private void localProduceConsumeNetDemand (double kwh, int rawIndex)
      {
        int index = getIndex(rawIndex);
        double kwhPerCustomer = kwh;

        double oldUsage = netDemand[index];
        if (oldUsage == 0.0) {
          // assume this is the first time
          netDemand[index] = kwhPerCustomer;
        }
        else {
          // exponential smoothing
          netDemand[index] = alpha * kwhPerCustomer + (1.0 - alpha) * oldUsage;
        }
        //PortfolioManagerService.log.debug("consume {} at {}, customer {}", kwh, index, customer.getName());
      }

      double getNetDemand (int index)
      {
        if (index < 0) {
          PortfolioManagerService.log.warn("usage requested for negative index " + index);
          index = 0;
        }
        return netDemand[getIndex(index)];
      }

      private int getIndex (int rawIndex)
      {
        return rawIndex % netDemand.length;
      }
    }
}
