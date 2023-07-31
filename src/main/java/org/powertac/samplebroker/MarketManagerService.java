/*
 * Copyright (c) 2012-2014 by the original author
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

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import javafx.util.Pair;

import org.json.simple.JSONObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.powertac.common.BalancingTransaction;
import org.powertac.common.ClearedTrade;
import org.powertac.common.Competition;
import org.powertac.common.MarketPosition;
import org.powertac.common.MarketTransaction;
import org.powertac.common.Order;
import org.powertac.common.Orderbook;
import org.powertac.common.OrderbookOrder;
import org.powertac.common.Timeslot;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.msg.BalanceReport;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.DistributionReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.msg.SimEnd;
import org.powertac.common.msg.TimeslotComplete;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Initializable;
import org.powertac.samplebroker.interfaces.MarketManager;
import org.powertac.samplebroker.interfaces.MessageManager;
import org.powertac.samplebroker.interfaces.PortfolioManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.powertac.samplebroker.wholesalemarket.MDP_DDPG;
import org.powertac.samplebroker.wholesalemarket.MDP_DDPG_State;
import org.powertac.samplebroker.wholesalemarket.OrderBook;
import org.powertac.samplebroker.wholesalemarket.MDP_DDPG.Exeperience;
import org.powertac.samplebroker.helpers.MarketManagerInformation;
import org.powertac.samplebroker.util.*;
import org.powertac.samplebroker.messages.*;
import org.powertac.samplebroker.information.*;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;

/**
 * Handles market interactions on behalf of the broker.
 *
 * @author John Collins
 */
@Service
public class MarketManagerService implements MarketManager, Initializable, Activatable {
  static private Logger log = LogManager.getLogger(MarketManagerService.class);

  private BrokerContext broker; // broker

  // @Autowired
  // private MessageManager messageManager;

  // Spring fills in Autowired dependencies through a naming convention
  @Autowired
  private BrokerPropertiesService propertiesService;

  @Autowired
  private TimeslotRepo timeslotRepo;

  @Autowired
  private MessageManager messageManager;

  @Autowired
  private PortfolioManager portfolioManager;

  private WholesaleMarketInformation wholesaleMarketInformation;
  private MarketTransactionInformation marketTransactionInformation;
  private ClearedTradeInformation clearedTradeInformation;
  private MarketPositionInformation marketPositionInformation;
  private BalancingMarketInformation balancingMarketInformation;
  private HelperForNormalization helperForNormalization;
  private GameInformation gameInformation;
  private MarketManagerInformation mminfo;

  private SubmittedBidInformation submittedBidInformation;
  private MDP_DDPG mdp_ddpg;
  private OrderBook agent;

  // ------------ Configurable parameters --------------
  // max and min offer prices. Max means "sure to trade"

  @ConfigurableValue(valueType = "Double", description = "Upper end (least negative) of bid price range")
  private final double buyLimitPriceMax = -1.0; // broker pays

  @ConfigurableValue(valueType = "Double", description = "Lower end (most negative) of bid price range")
  private final double buyLimitPriceMin = -100.0; // broker pays

  @ConfigurableValue(valueType = "Double", description = "Upper end (most positive) of ask price range")
  private final double sellLimitPriceMax = 100.0; // other broker pays

  @ConfigurableValue(valueType = "Double", description = "Lower end (least positive) of ask price range")
  private final double sellLimitPriceMin = 0.5; // other broker pays

  @ConfigurableValue(valueType = "Double", description = "Minimum bid/ask quantity in MWh")
  private double minMWh = 0.001; // don't worry about 1 KWh or less

  @ConfigurableValue(valueType = "Integer", description = "If set, seed the random generator")
  private final Integer seedNumber = null;

  private final Boolean isTraining = false;

  // ---------------- local state ------------------
  private Random randomGen; // to randomize bid/ask prices

  // MongoDB client and Database
  MongoClient mongoClient;

  // MongoDB database
  DB mongoDatabase;

  String dbname;

  // Bid recording
  private HashMap<Integer, Order> lastOrder;
  private double[] marketMWh;
  private double[] marketPrice;
  private double meanMarketPrice = 0.0;

  public Double ea_miso_demand = 0.0;

  public MarketManagerService() {
    super();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.powertac.samplebroker.MarketManager#init(org.powertac.samplebroker.
   * SampleBroker)
   */
  @Override
  public void initialize(BrokerContext broker) {
    this.broker = broker;
    lastOrder = new HashMap<>();
    propertiesService.configureMe(this);
    System.out.println("  name=" + broker.getBrokerUsername());
    if (seedNumber != null) {
      System.out.println("  seeding=" + seedNumber);
      log.info("Seeding with : " + seedNumber);
      randomGen = new Random(seedNumber);
    } else {
      randomGen = new Random();
    }

    mdp_ddpg = new MDP_DDPG();
    helperForNormalization = new HelperForNormalization();
    submittedBidInformation = new SubmittedBidInformation();
    mminfo = new MarketManagerInformation(this.broker.getBrokerUsername());
    agent = new OrderBook(broker, mminfo); 

    dbname = "PowerTAC2022_Qualifiers";

    try {
      mongoClient = new MongoClient("localhost", 27017);
      mongoDatabase = mongoClient.getDB(dbname);
    } catch (Exception e) {
      log.warn("Mongo DB connection Exception " + e.toString());
    }
    log.info(" Connected to Database " + dbname + " -- Broker Initialize");
    System.out.println("Connected to Database " + dbname + " from Initialize in MarketManagerService");
  }

  // ----------------- data access -------------------
  /**
   * Returns the mean price observed in the market
   */
  @Override
  public double getMeanMarketPrice() {
    return meanMarketPrice;
  }

  @Override
  public SubmittedBidInformation getSubmittedBidInformation() {
    return submittedBidInformation;
  }

  // --------------- message handling -----------------
  /**
   * Handles the Competition instance that arrives at beginning of game. Here we
   * capture minimum order size to avoid running into the limit and generating
   * unhelpful error messages.
   */
  public synchronized void handleMessage(Competition comp) {
    minMWh = Math.max(minMWh, comp.getMinimumOrderQuantity());
  }

  /**
   * Receives a MarketBootstrapData message, reporting usage and prices for the
   * bootstrap period. We record the overall weighted mean price, as well as the
   * mean price and usage for a week.
   */
  public synchronized void handleMessage(MarketBootstrapData data) {
    marketMWh = new double[broker.getUsageRecordLength()];
    marketPrice = new double[broker.getUsageRecordLength()];
    double totalUsage = 0.0;
    double totalValue = 0.0;

    for (int i = 0; i < data.getMwh().length; i++) {
      totalUsage += data.getMwh()[i];
      totalValue += data.getMarketPrice()[i] * data.getMwh()[i];
      if (i < broker.getUsageRecordLength()) {
        // first pass, just copy the data
        marketMWh[i] = data.getMwh()[i];
        marketPrice[i] = data.getMarketPrice()[i];
      } else {
        // subsequent passes, accumulate mean values
        int pass = i / broker.getUsageRecordLength();
        int index = i % broker.getUsageRecordLength();
        marketMWh[index] = (marketMWh[index] * pass + data.getMwh()[i]) / (pass + 1);
        marketPrice[index] = (marketPrice[index] * pass + data.getMarketPrice()[i]) / (pass + 1);
      }
    }
    meanMarketPrice = totalValue / totalUsage;
  }

  public synchronized void handleMessage (CustomerBootstrapData cbd)
  {
    for (int i = 0; i < cbd.getNetUsage().length; i++) {
      if (cbd.getPowerType().isConsumption()){
        mminfo.set_powertac_energy_information(i+24, Math.abs(cbd.getNetUsage()[i])/1e3, 0.0);
      }
      else if (cbd.getPowerType().isProduction()) {
        mminfo.set_powertac_energy_information(i+24, 0.0, Math.abs(cbd.getNetUsage()[i])/1e3);
      }
    }
  }

  public synchronized void handleMessage (ClearedTrade ct)
  {
    Integer bidding_timeslot = timeslotRepo.getTimeslotIndex(ct.getDateExecuted()) - 1;
    Integer delivery_timeslot = ct.getTimeslotIndex();
    Integer proximity = delivery_timeslot-bidding_timeslot;
    Double wmp = ct.getExecutionPrice();
    Double wmq = ct.getExecutionMWh();
    mminfo.set_wholesale_market_information(delivery_timeslot, proximity, wmp, wmq);
  }

  public synchronized void handleMessage (Orderbook orderbook)
  {
    Integer bidding_timeslot = timeslotRepo.getTimeslotIndex(orderbook.getDateExecuted()) - 1;
    Integer delivery_timeslot = orderbook.getTimeslotIndex();
    Integer proximity = delivery_timeslot-bidding_timeslot;
    int index = (delivery_timeslot) % broker.getUsageRecordLength();
    Double totalquantity = portfolioManager.collectUsage(index) / 1000.0;

    Double neededquantity = totalquantity - broker.getBroker().findMarketPositionByTimeslot(delivery_timeslot).getOverallBalance();
    Double sellneeded = (neededquantity>0) ? 0.0 : neededquantity;
    Double buyneeded = (neededquantity>0) ? neededquantity : 0.0;

    // BIDS
    // List<Double> bidp = new ArrayList<>();
    // List<Double> bidq = new ArrayList<>();
    Double bidp_required = 0.0;
    Double bidq_required = 0.0;
    for (OrderbookOrder bid : orderbook.getBids()) {
      bidp_required = bid.getLimitPrice();
      bidq_required += bid.getMWh();
      if (Math.abs(bidq_required)>=Math.abs(sellneeded)){
        break;
      }
    }
    mminfo.set_orderbook_information(delivery_timeslot, proximity, bidp_required, bidq_required, "bid");

    // ASKS
    // List<Double> askp = new ArrayList<>();
    // List<Double> askq = new ArrayList<>();
    Double askp_required = 0.0;
    Double askq_required = 0.0;
    for (OrderbookOrder ask : orderbook.getAsks()) {
      askp_required = ask.getLimitPrice();
      askq_required += ask.getMWh();
      if (Math.abs(askq_required)>=Math.abs(buyneeded)){
        break;
      }
    }
    mminfo.set_orderbook_information(delivery_timeslot, proximity, askp_required, askq_required, "ask");
  }

  /**
   * Receives a new MarketTransaction. We look to see whether an order we have
   * placed has cleared.
   */
  public synchronized void handleMessage(MarketTransaction tx) {
    // reset price escalation when a trade fully clears.
    Order lastTry = lastOrder.get(tx.getTimeslotIndex());
    if (lastTry == null) // should not happen
      log.error("order corresponding to market tx " + tx + " is null");
    else if (tx.getMWh() == lastTry.getMWh()) // fully cleared
      lastOrder.put(tx.getTimeslotIndex(), null);

    Integer bidding_timeslot = tx.getPostedTimeslotIndex() - 1;
    Integer delivery_timeslot = tx.getTimeslotIndex();
    Integer proximity = delivery_timeslot-bidding_timeslot;
    Double wbp = tx.getPrice();
    Double wbq = tx.getMWh();

    if (Math.abs(wbq)>0.0 && tx.getPrice()<0.0) {
      mminfo.set_wholesale_broker_information(delivery_timeslot, proximity, wbp, wbq);
    }
  }

  public synchronized void handleMessage(DistributionReport dr)
  {
    Integer timeslot = dr.getTimeslot();
    Double consumption = dr.getTotalConsumption() / 1e3;
    Double production = dr.getTotalProduction() / 1e3;
    mminfo.set_powertac_energy_information(timeslot, consumption, production);
  }

  public synchronized void handleMessage (BalanceReport report)
  {
    Integer timeslot = report.getTimeslotIndex();
    Double bmq = report.getNetImbalance() / 1e3;
    mminfo.set_balancing_market_information(timeslot, bmq);
  }

  public synchronized void handleMessage (BalancingTransaction tx)
  {
    log.info("Balancing tx: " + tx.getCharge());
    Integer timeslot = tx.getPostedTimeslotIndex();
    Double bbq = tx.getKWh() / 1e3;
    Double bbp = tx.getCharge() / Math.abs(bbq);

    if (Math.abs(bbq)>0.0 && tx.getCharge()<0.0) {
      mminfo.set_balancing_broker_information(timeslot, bbp, bbq);
    }
  }

  @Override
  public Double get_exponentialaverage_misodemand() {
    return ea_miso_demand;
  }

  public synchronized void handleMessage(TimeslotComplete ts)
  {
    int currentTimeslot = ts.getTimeslotIndex();
    gameInformation = messageManager.getGameInformation();

    /* try
    {
      String col6 = "Submitted_Bid_Information";
      DBCollection collection6 = mongoDatabase.getCollection(col6);

      Map<Integer, List<Pair<Double, Double>>> MTI = submittedBidInformation.getSubmittedBidInformationbyMessageTimeslot(currentTimeslot - 1);

      for (Map.Entry<Integer, List<Pair<Double, Double>>> message : MTI.entrySet())
      {
        DBObject document6 = new BasicDBObject();

        Double avgLimitPrice = 0.0;
        Double totalQuantity = 0.0;

        for(Pair<Double, Double> item : message.getValue())
        {
          if(item.getKey() < 0.0)
          {
              avgLimitPrice += item.getKey();
              totalQuantity += item.getValue();   
          }
        }  

        if(message.getValue().size() != 0)
            avgLimitPrice /= message.getValue().size();

        document6.put("Game_Name", gameInformation.getName());
        document6.put("Bidding_Timeslot", currentTimeslot - 1);
        document6.put("Execution_Timeslot", message.getKey());
        document6.put("LimitPrice", avgLimitPrice);
        document6.put("Broker's_Bidded_Quantity", totalQuantity);
        
        collection6.insert(document6);
      }
    }
    catch (Exception e)
    {
    } */
  }

  public synchronized void handleMessage(SimEnd se)     // Call after every 100 timeslots PENDING
  {
    if(isTraining)
    {
      // helperForNormalization.storeToFile();
      JSON_API.communicateWithPython("http://localhost:5000/SaveDDPGModels", null);
    }
  }

  // ----------- per-timeslot activation ---------------

  /**
   * Compute needed quantities for each open timeslot, then submit orders for
   * those quantities.
   *
   * @see org.powertac.samplebroker.interfaces.Activatable#activate(int)
   */
  @Override
  public synchronized void activate (int timeslotIndex)
  {
    int currentTimeslot = timeslotRepo.currentTimeslot().getSerialNumber();
    marketPositionInformation = messageManager.getMarketPositionInformation();
    balancingMarketInformation = messageManager.getBalancingMarketInformation();

    log.debug("Current timeslot is " + timeslotRepo.currentTimeslot().getSerialNumber());
    System.out.println("Current Timeslot: " + timeslotIndex);

    double neededMWh[] = new double[24];

    if (timeslotIndex >= 364) 
    {
      try 
      {
        if(ea_miso_demand==0.0) 
          ea_miso_demand = clearedTradeInformation.getClearedTradebyMessageTimeslot(timeslotIndex).get(timeslotIndex+23).getValue();
        else 
          ea_miso_demand = 0.6*ea_miso_demand + 0.4*clearedTradeInformation.getClearedTradebyMessageTimeslot(timeslotIndex).get(timeslotIndex+23).getValue();
      }
      catch (Exception e) {} 
    }

    for (int proximity = 1; proximity <= 24; proximity++)
    {
      int deliveryTimeslot = timeslotIndex + proximity;
      int index = (deliveryTimeslot) % broker.getUsageRecordLength();
      Double neededKWh = portfolioManager.collectUsage(index);

      neededMWh[proximity-1] = neededKWh / 1000.0;

      // if(isTraining)
      // {
      //   helperForNormalization.addToProximityRecord(proximity);
      //   helperForNormalization.addToBalancingPriceRecord(avgBuyBalancingPrice);
      //   helperForNormalization.addToQuantityRecord(Math.abs(neededMWh[proximity-1]));
      // }

      Double posn = marketPositionInformation.getMarketPosition(deliveryTimeslot);
      if (posn != null)
      {
        neededMWh[proximity-1] = neededMWh[proximity-1] - posn;
      }
    }

    if(isTraining && timeslotIndex != 360)
      calculateReward(timeslotIndex, neededMWh);

    if(isTraining)
      mdp_ddpg.resetExperienceMap();
    
    try
    {
      submitOrderDDPG(neededMWh);           // DDPG strategy for 24th proximity
      // submitOrdersSupplyCurve(neededMWh);   // supply-curve strategy for 1 to 23rd proximity
    }
    catch(Exception e)
    {
      e.printStackTrace();
    }

    // /** SubmitOrder method of Sample broker */
    // double neededKWh = 0.0;
    // for (Timeslot timeslot : timeslotRepo.enabledTimeslots()) 
    // {
    //   int index = (timeslot.getSerialNumber()) % broker.getUsageRecordLength();
    //   neededKWh = portfolioManager.collectUsage(index);
    //   // System.out.println("Needed Quantity for " + timeslot.getSerialNumber() + " is " + neededKWh/1000.0);
    //   submitOrderSample(neededKWh, timeslot.getSerialNumber());
    // }
  }

   /**
   * Composes and submits the appropriate order for the given timeslot.
   */
  private void submitOrderSample(double neededKWh, int timeslot)
  {
    double neededMWh = neededKWh / 1000.0;

    MarketPosition posn = broker.getBroker().findMarketPositionByTimeslot(timeslot);
    if (posn != null)
      neededMWh -= posn.getOverallBalance();
    if (Math.abs(neededMWh) <= minMWh) 
    {
      log.info("no power required in timeslot " + timeslot);
      return;
    }
    Double limitPrice = computeLimitPrice(timeslot, neededMWh);
    // System.out.println("new order for " + neededMWh + " at " + limitPrice + " in timeslot " + timeslot);
    log.info("new order for " + neededMWh + " at " + limitPrice + " in timeslot " + timeslot);
    Order order = new Order(broker.getBroker(), timeslot, neededMWh, limitPrice);
    lastOrder.put(timeslot, order);
    broker.sendMessage(order);
  }

  public void submitOrdersSupplyCurve(double[] neededMWhTotal) 
  {
    int currentTimeslot = timeslotRepo.currentSerialNumber();

    // for(int proximity = 1; proximity <= 24; proximity++)
    for(int proximity = 1; proximity <= 21; proximity++)
    {
      Integer deliveryTimeslot = currentTimeslot + proximity;
      Double neededMWh = neededMWhTotal[proximity-1];

      List<Order> brokerorders = agent.generateOrders(deliveryTimeslot, neededMWh, proximity);

      if (brokerorders.size() > 0){
        for (Order brokerorder : brokerorders) 
        {
          mminfo.set_bidding_broker_information(deliveryTimeslot, proximity, brokerorder.getLimitPrice(), brokerorder.getMWh());
          Order order = new Order(broker.getBroker(), deliveryTimeslot, brokerorder.getMWh(), brokerorder.getLimitPrice());
  
          // System.out.println("Current Timeslot : " + currentTimeslot + ", Execution Timeslot :  " + (currentTimeslot+proximity) + ", Price : " + brokerorder.getLimitPrice() + " , Quantity : " + brokerorder.getMWh());

          submittedBidInformation.setSubmittedBidInformationbyExecutionTimeslot(deliveryTimeslot, currentTimeslot, brokerorder.getLimitPrice(), brokerorder.getMWh());
          submittedBidInformation.setSubmittedBidInformationbyMessageTimeslot(currentTimeslot, deliveryTimeslot, brokerorder.getLimitPrice(), brokerorder.getMWh());
  
          broker.sendMessage(order);
        }
      } 
    }
  }

  private void submitOrderDDPG(double[] neededMWh)
  {
    int currentTimeslot = timeslotRepo.currentSerialNumber();
    balancingMarketInformation = messageManager.getBalancingMarketInformation();

    for(int proximity = 1; proximity <= 24; proximity++)
    // for(int proximity = 22; proximity <= 24; proximity++)
    {
      Integer timeslot = currentTimeslot + proximity;

      if (Math.abs(neededMWh[proximity-1]) <= minMWh) {
        log.info("no power required in timeslot " + timeslot);
        continue;
      }

      Pair<Double, Double> action = getDDPGAction(proximity, neededMWh[proximity-1]);        // Random action for training 

      Double avgBuyBalancingPrice = balancingMarketInformation.getAvgBuyBalancingPrice();
      // System.out.println("Proximity : " + proximity + " :: Actions : (" + action.get(proximity-1).getKey() + ", " + action.get(proximity-1).getValue() + ")");
      // System.out.println("Balancing Price: " + avgBuyBalancingPrice);

      Double l1 = action.getKey();  
      Double l2 = action.getValue();        

      if(neededMWh[proximity-1] > 0.0)      
      {    
        Double limitPrice1 = -l1*avgBuyBalancingPrice;
        Double limitPrice2 = -l2*avgBuyBalancingPrice;

        if(!isTraining && (proximity == 1))
        {
          limitPrice1 = null;        // Market-order
          limitPrice2 = null;        // Market-order
        }

        log.info("new order for " + neededMWh[proximity-1]/2 + " at " + limitPrice1 + " in timeslot " + timeslot);
        log.info("new order for " + neededMWh[proximity-1]/2 + " at " + limitPrice2 + " in timeslot " + timeslot);

        Order order1 = new Order(broker.getBroker(), timeslot, neededMWh[proximity-1]/2, limitPrice1);
        Order order2 = new Order(broker.getBroker(), timeslot, neededMWh[proximity-1]/2, limitPrice2);

        // System.out.println("Current Timeslot : " + currentTimeslot + ", Execution Timeslot :  " + (currentTimeslot+proximity) + ", Price : (" + limitPrice1 + ", " + limitPrice2 + ") , Quantity : " + neededMWh[proximity-1]);

        submittedBidInformation.setSubmittedBidInformationbyExecutionTimeslot(timeslot, timeslotRepo.currentTimeslot().getSerialNumber(), limitPrice1, neededMWh[proximity-1]/2);
        submittedBidInformation.setSubmittedBidInformationbyMessageTimeslot(timeslotRepo.currentTimeslot().getSerialNumber(), timeslot, limitPrice1, neededMWh[proximity-1]/2);
        submittedBidInformation.setSubmittedBidInformationbyExecutionTimeslot(timeslot, timeslotRepo.currentTimeslot().getSerialNumber(), limitPrice2, neededMWh[proximity-1]/2);
        submittedBidInformation.setSubmittedBidInformationbyMessageTimeslot(timeslotRepo.currentTimeslot().getSerialNumber(), timeslot, limitPrice2, neededMWh[proximity-1]/2);

        broker.sendMessage(order1);
        broker.sendMessage(order2);
      }
      else
      {
        Double limitPrice = computeLimitPrice(timeslot, neededMWh[proximity-1]);
        log.info("new order for " + neededMWh + " at " + limitPrice + " in timeslot " + timeslot);
        Order order = new Order(broker.getBroker(), timeslot, neededMWh[proximity-1], limitPrice);
        lastOrder.put(timeslot, order);
        broker.sendMessage(order);
      }
    }
  }

  private Pair<Double, Double> getDDPGAction(Integer proximity, double neededMWh)
  {
    /*DDPG Calling and Training*/
    Pair<Double, Double> action;
    balancingMarketInformation = messageManager.getBalancingMarketInformation();
    Double avgBuyBalancingPrice = balancingMarketInformation.getAvgBuyBalancingPrice();

    if(isTraining)
    {

      Double temp1 = randomGen.nextDouble();
      Double temp2 = randomGen.nextDouble();

      Double alpha_1 = Math.max(temp1, temp2);
      Double alpha_2 = Math.min(temp1, temp2);

      action = new Pair<Double, Double> (alpha_1, alpha_2);                                    // Keep all lps positive for normalization

      if(neededMWh > this.minMWh)
      {
        // System.out.println("Proximity: " + proximity + ", Quantity: " + neededMWh[proximity-1] + ", Norm_Quantity: " + helperForNormalization.normalize(Math.abs(neededMWh[proximity-1]), "Quantity") +
        //                    ", Action: (" + (avgBuyBalancingPrice * action.get(proximity-1).getKey()) + ", " + (avgBuyBalancingPrice * action.get(proximity-1).getValue())  + 
        //                    ") , Norm_Action: (" + action.get(proximity-1).getKey() + ", " + action.get(proximity-1).getValue() + ")");
        Double normProximity = helperForNormalization.normalize(proximity, "Proximity");
        Double balancingPrice = helperForNormalization.normalize(avgBuyBalancingPrice, "BalancingPrice");
        Double quan = helperForNormalization.normalize(Math.abs(neededMWh), "Quantity");

        mdp_ddpg.setStateAction(proximity, normProximity, balancingPrice, quan, action);  // Normalized values 
      }
    }
    else
    {
      JSONObject[] object = new JSONObject[24];

      JSONObject obj = new JSONObject();

      Double normProximity = helperForNormalization.normalize(proximity, "Proximity");
      Double balancingPrice = helperForNormalization.normalize(avgBuyBalancingPrice, "BalancingPrice");
      Double quan = helperForNormalization.normalize(Math.abs(neededMWh), "Quantity");
      
      obj.put("state", new MDP_DDPG_State(normProximity, balancingPrice, quan));        // Normalized values (dummy value for ratio)
      object[0] = obj;

      try
      {
        String responseString = JSON_API.communicateWithPython("http://localhost:5000/DDPGActionPicker", object);
        action = JSON_API.decodeJSON(responseString);
      }
      catch(Exception e)    // else use below random bidding
      {
       e.printStackTrace();

        Double temp1 = randomGen.nextDouble();
        Double temp2 = randomGen.nextDouble();

        Double alpha_1 = Math.max(temp1, temp2);
        Double alpha_2 = Math.min(temp1, temp2);

        action = new Pair<Double, Double> (alpha_1, alpha_2);  
      }
    }    

    // System.out.println(action);
    return action;
  }

  private void calculateReward(Integer timeslotIndex, double[] neededMWh)
  {
    marketTransactionInformation = messageManager.getMarketTransactionInformation();
    balancingMarketInformation = messageManager.getBalancingMarketInformation();

    Map<Integer, List<Pair<Double, Double>>> SBIM = submittedBidInformation.getSubmittedBidInformationbyMessageTimeslot(timeslotIndex-1);
    Map<Integer, Pair<Double, Double>> MTIM = marketTransactionInformation.getMarketTransactionInformationbyMessageTimeslot(timeslotIndex);  
    Double avgBuyBalancingPrice = balancingMarketInformation.getAvgBuyBalancingPrice();

    if(SBIM != null)
    {
      for(Map.Entry<Integer, List<Pair<Double, Double>>> item : SBIM.entrySet())
      {
        Double reward = 0.0;
        Double clearedQuantity = 0.0 ;
        Double clearingPrice = -1.0;

        Integer executionTimeslot = item.getKey();
        
        if((MTIM != null) && (MTIM.get(executionTimeslot) != null))
        {
          clearingPrice = Math.abs(MTIM.get(executionTimeslot).getKey());
          clearedQuantity = Math.max(0.0, MTIM.get(executionTimeslot).getValue());
        }

        Double normClearingPrice = helperForNormalization.normalize(clearingPrice, "BalancingPrice");
        Double normQuantity = helperForNormalization.normalize(clearedQuantity, "Quantity");
        reward = -normClearingPrice*normQuantity;

        double bidQuantity = 0.0;
        for(Pair<Double, Double> bids : item.getValue())          // Takes care of both bids and asks
        {
          bidQuantity += bids.getValue();
        }

        Integer transitionProximity = executionTimeslot - timeslotIndex;

        if(transitionProximity == 0)
        {
          Double normBalancing = helperForNormalization.normalize(avgBuyBalancingPrice, "BalancingPrice");
          Double normRemQuantity = helperForNormalization.normalize(Math.max(0.0, (bidQuantity - clearedQuantity)), "Quantity");;
          reward -= normBalancing*normRemQuantity;      // negative

          // System.out.println("At 0: " + "Proximity: " + (transitionProximity+1) + ", Trans_Proximity: " + transitionProximity + ", BalancingPrice: " + avgBuyBalancingPrice  + ", ClearingPrice: " + clearingPrice + ", ClearedQuantity : " + clearedQuantity + ", Rem Quantity: " + Math.max(0.0, (bidQuantity - clearedQuantity)) + 
                            //  ", Norm_Quantity: " + normRemQuantity+ ", Reward: " + reward);
         
          Double normProximity = helperForNormalization.normalize(transitionProximity, "Proximity");
          Double balancingPrice = helperForNormalization.normalize(avgBuyBalancingPrice, "BalancingPrice");
          Double quan = helperForNormalization.normalize(0.0, "Quantity");
          mdp_ddpg.setRewardNextState(reward, transitionProximity, normProximity, balancingPrice, quan, 1);                           // Normalized values
        }
        else
        {
          // System.out.println("At " + transitionProximity + "Proximity: " + (transitionProximity+1) + ", Trans_Proximity: " + transitionProximity + ", BalancingPrice: " + avgBuyBalancingPrice  + ", ClearingPrice: " + clearingPrice + ", ClearedQuantity : " + clearedQuantity + ", Req Quantity: " + neededMWh[transitionProximity-1] + 
          //                    ", Norm_Quantity: " + helperForNormalization.normalize(Math.abs(neededMWh[transitionProximity-1]), "Quantity") + ", Reward: " + reward);

          Double normProximity = helperForNormalization.normalize(transitionProximity, "Proximity");
          Double balancingPrice = helperForNormalization.normalize(avgBuyBalancingPrice, "BalancingPrice");
          Double quan = helperForNormalization.normalize(Math.abs(neededMWh[transitionProximity-1]), "Quantity");

          mdp_ddpg.setRewardNextState(reward, transitionProximity, normProximity, balancingPrice, quan, 0);  // Normalized values
        }
      }
    }

    Map<Integer, Exeperience> exeperiences = mdp_ddpg.getExperiences();
    JSONObject[] object = new JSONObject[24];
    int index = 0;

    for(Map.Entry<Integer, Exeperience> exe : exeperiences.entrySet())
    {
      Exeperience e = exe.getValue();

      if((e.state != null) && (e.action != null) && (e.reward != null) 
          && (e.nextState != null) && (e.terminal != null))
      {
        JSONObject obj = new JSONObject();
        obj.put("state", e.state);
        obj.put("action", e.action);
        obj.put("reward", e.reward);
        obj.put("next_state", e.nextState);
        obj.put("terminal", e.terminal);

        object[index++] = obj;

        // System.out.println(obj);
      }
    }

    JSON_API.communicateWithPython("http://localhost:5000/DDPGUpdateReplayBuffer", object);
    JSON_API.communicateWithPython("http://localhost:5000/DDPGTraining", null);
  }

  /**
  * Computes a limit price with a random element.
  */
  private Double computeLimitPrice (int timeslot, double amountNeeded)
  {
    log.debug("Compute limit for " + amountNeeded + ", timeslot " + timeslot);
    // start with default limits
    Double oldLimitPrice;
    double minPrice;
    if (amountNeeded > 0.0) {
      // buying
      oldLimitPrice = buyLimitPriceMax;
      minPrice = buyLimitPriceMin;
    }
    else {
      // selling
      oldLimitPrice = sellLimitPriceMax;
      minPrice = sellLimitPriceMin;
    }
    // check for escalation
    Order lastTry = lastOrder.get(timeslot);
    if (lastTry != null)
      log.debug("lastTry: " + lastTry.getMWh() +
                " at " + lastTry.getLimitPrice());
    if (lastTry != null
        && Math.signum(amountNeeded) == Math.signum(lastTry.getMWh())) {
      oldLimitPrice = lastTry.getLimitPrice();
      log.debug("old limit price: " + oldLimitPrice);
    }

    // set price between oldLimitPrice and maxPrice, according to number of
    // remaining chances we have to get what we need.
    double newLimitPrice = minPrice; // default value
    int current = timeslotRepo.currentSerialNumber();
    int remainingTries = (timeslot - current
                          - Competition.currentCompetition().getDeactivateTimeslotsAhead());
    log.debug("remainingTries: " + remainingTries);
    if (remainingTries > 0) {
      double range = (minPrice - oldLimitPrice) * 2.0 / (double)remainingTries;
      log.debug("oldLimitPrice=" + oldLimitPrice + ", range=" + range);
      double computedPrice = oldLimitPrice + randomGen.nextDouble() * range; 
      return Math.max(newLimitPrice, computedPrice);
    }
    else
      return null; // market order
  }
 }
