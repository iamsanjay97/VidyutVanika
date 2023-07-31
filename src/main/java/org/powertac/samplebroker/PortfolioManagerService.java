/*
 * Copyright (c) 2012-2013 by the original author
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

import java.io.FileWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import javafx.util.Pair;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.joda.time.Instant;
import org.powertac.common.Broker;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TimeService;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.TariffRevoke;
import org.powertac.common.msg.TariffStatus;
import org.powertac.common.msg.SimEnd;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.bson.Document;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;

import org.powertac.samplebroker.messages.BalancingMarketInformation;
import org.powertac.samplebroker.util.Helper;
import org.powertac.samplebroker.tariffmarket.adjustmarketsharerl.*;
import org.powertac.samplebroker.tariffmarket.TariffHeuristics;
import org.powertac.samplebroker.information.TariffMarketInformation;
import org.powertac.samplebroker.information.UsageRecord;
import org.powertac.samplebroker.information.CustomerUsageInformation;
import org.powertac.samplebroker.information.CustomerSubscriptionInformation;
import org.powertac.samplebroker.information.WholesaleMarketInformation;
import org.powertac.samplebroker.messages.CapacityTransactionInformation;
import org.powertac.samplebroker.messages.MarketTransactionInformation;
import org.powertac.samplebroker.messages.DistributionInformation;
import org.powertac.samplebroker.messages.CashPositionInformation;
import org.powertac.samplebroker.messages.GameInformation;

/**
 * Handles portfolio-management responsibilities for the broker. This
 * includes composing and offering tariffs, keeping track of customers and their
 * usage, monitoring tariff offerings from competing brokers.
 *
 * A more complete broker implementation might split this class into two or
 * more classes; the keys are to decide which messages each class handles,
 * what each class does on the activate() method, and what data needs to be
 * managed and shared.
 *
 * @author John Collins
 */
@Service // Spring creates a single instance at startup
public class PortfolioManagerService
implements PortfolioManager, Initializable, Activatable
{
  static Logger log = LogManager.getLogger(PortfolioManagerService.class);

  private BrokerContext brokerContext; // master

  // Spring fills in Autowired dependencies through a naming convention
  @Autowired
  private BrokerPropertiesService propertiesService;

  @Autowired
  private TimeslotRepo timeslotRepo;

  @Autowired
  private TariffRepo tariffRepo;

  @Autowired
  private CustomerRepo customerRepo;

  @Autowired
  private MarketManager marketManager;

  @Autowired
  private MessageManager messageManager;

  @Autowired
  private PortfolioManager portfolioManager;

  @Autowired
  private TimeService timeService;

  // ---- Portfolio records -----
  // Customer records indexed by power type and by tariff. Note that the
  // CustomerRecord instances are NOT shared between these structures, because
  // we need to keep track of subscriptions by tariff.
  private Map<PowerType, Map<CustomerInfo, CustomerRecord>> customerProfiles;
  private Map<TariffSpecification, Map<CustomerInfo, CustomerRecord>> customerSubscriptions;
  private Map<PowerType, List<TariffSpecification>> competingTariffs;
  private Map<PowerType, List<TariffSpecification>> ownTariffs;
  private List<Double> cashFlowInfo;

  // Keep track of a benchmark price to allow for comparisons between tariff evaluations
  private double benchmarkPrice = 0.0;

  // These customer records need to be notified on activation
  private List<CustomerRecord> notifyOnActivation = new ArrayList<>();

  // Configurable parameters for tariff composition --> Override defaults in src/main/resources/config/broker.config or in top-level config file
  @ConfigurableValue(valueType = "Double", description = "target profit margin")
  private double defaultMargin = 1.0;

  @ConfigurableValue(valueType = "Double", description = "Fixed cost/kWh")
  private double fixedPerKwh = -0.06;

  @ConfigurableValue(valueType = "Double", description = "Default daily meter charge")
  private double defaultPeriodicPayment = -1.0;

  @ConfigurableValue(valueType = "Double", description = "Fixed cost/kWh for distribution")
  private double distributionCharge = -0.02;

  @ConfigurableValue(valueType = "Double", description = "profit margin in min viable cost")
  private Double PROFIT_MULTIPLIER = 1.5;

  // *********** Below are Configurable Parameters for Tariff Heuristic **************
  
  @ConfigurableValue(valueType = "Integer", description = "periodic update interval for consumption tariffs")
  private Integer UPDATE_INTERVAL_C = 24;

  @ConfigurableValue(valueType = "Integer", description = "periodic update interval for production tariffs")
  private Integer UPDATE_INTERVAL_P = 24;

  @ConfigurableValue(valueType = "Integer", description = "periodic check interval for tariff's health")
  private Integer TARIFF_HEALTH_CHECK = UPDATE_INTERVAL_C;
  
  @ConfigurableValue(valueType = "Double", description = "lowest desirable market-share of a tariff")
  private Double TARIFF_LOWER_BOUND = 0.05;

  @ConfigurableValue(valueType = "Double", description = "highest desirable market-share of a tariff")
  private Double TARIFF_UPPER_BOUND = 0.95;  

  @ConfigurableValue(valueType = "Double", description = "highest desirable cumulative market-share of all own production tariff")
  private Double PRODUCTION_HIGHER_BOUND = 0.80;

  // *********** Below are Configurable Parameters for Game Termination **************
  
  @ConfigurableValue(valueType = "Integer", description = "waiting days brfore terminating the game")
  private Integer WAIT_INTERVAL = 10;

  @ConfigurableValue(valueType = "Double", description = "worst cash-position brfore terminating the game")
  private Double WORST_CASH_POSITION = -350000.0;

  @ConfigurableValue(valueType = "Boolean", description = "whether the game is profitable and worth continue playing or not")
  private Boolean GAME_PROFITABLE_FLAG = true;

  @ConfigurableValue(valueType = "Boolean", description = "whether the MISO type game is profitable and worth continue playing or not")
  private Boolean MISO_GAME_PROFITABLE_FLAG = true;

  @ConfigurableValue(valueType = "Double", description = "Desirable Difference between two tariff-rates")
  private Double MIN_DIFF_BW_TARIFFS = 0.005; 

  @ConfigurableValue(valueType = "Double", description = "Discount rate for Production customers")
  private Double DISCOUNT = 0.7;

  private boolean utilityFlag = false;
  private boolean isTraining = true;

  Double misoCaseCashGradient = null;
  Double MISO_CASE_CASH_GRADIENT_THRESHOLD = -15000.0;

  CustomerUsageInformation custUsageInfo = null;

  int OFFSET = 24;

  private BalancingMarketInformation balancingMarketInformation;
  private CashPositionInformation cashPositionInformation;
  private GameInformation gameInformation;
  private MarketTransactionInformation marketTransactionInformation;
  private TariffMarketInformation tariffMarketInformation;
  private DistributionInformation distributionInformation;
  private CapacityTransactionInformation capacityTransactionInformation;
  private WholesaleMarketInformation wholesaleMarketInformation;
  private AdjustMarketShareMDP adjustMarketShareMDP;
  private TariffHeuristics tariffHeuristics;
  public String gamename;
  public int numberOfBroker;

  private Double HIGHER_BOUND, MIDDLE_BOUND, LOWER_BOUND;

  //MongoDB client and Database
  MongoClient mongoClient;

  //MongoDB database
  MongoDatabase mongoDatabase;

  String dbname; 

  FileWriter accountingInformation;

  Random rand;

  double totalTariffRevenue = 0.0;
  double totalWholesaleCost = 0.0;

  /**
   * Default constructor.
   */
  public PortfolioManagerService ()
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
    customerProfiles = new HashMap<>();
    customerSubscriptions = new HashMap<>();
    competingTariffs = new HashMap<>();
    ownTariffs = new HashMap<>();

    custUsageInfo = messageManager.getCustomerUsageInformation();
    cashFlowInfo = new ArrayList<>();
    tariffMarketInformation = new TariffMarketInformation();

    rand = new Random();

    dbname = "PowerTAC2022_Qualifiers";
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
    System.out.println("Connected to Database " + dbname + " from Initialize in PortfolioManager");

    notifyOnActivation.clear();
  }

  // -------------- data access ------------------

  /**
   * Returns the CustomerRecord for the given type and customer, creating it
   * if necessary.
   */
  CustomerRecord getCustomerRecordByPowerType (PowerType type, CustomerInfo customer)
  {
    Map<CustomerInfo, CustomerRecord> customerMap = customerProfiles.get(type);
    if (customerMap == null)
    {
      customerMap = new HashMap<>();
      customerProfiles.put(type, customerMap);
    }
    CustomerRecord record = customerMap.get(customer);
    if (record == null)
    {
      record = new CustomerRecord(customer);
      customerMap.put(customer, record);
    }
    return record;
  }

  /**
   * Returns the customer record for the given tariff spec and customer,
   * creating it if necessary.
   */
  CustomerRecord getCustomerRecordByTariff (TariffSpecification spec, CustomerInfo customer)
  {
    Map<CustomerInfo, CustomerRecord> customerMap = customerSubscriptions.get(spec);
    if (customerMap == null)
    {
      customerMap = new HashMap<>();
      customerSubscriptions.put(spec, customerMap);
    }
    CustomerRecord record = customerMap.get(customer);
    if (record == null)
    {
      // seed with the generic record for this customer
      record = new CustomerRecord(getCustomerRecordByPowerType(spec.getPowerType(), customer));
      customerMap.put(customer, record);
      // set up deferred activation in case this customer might do regulation
      record.setDeferredActivation();
    }
    return record;
  }

  /**
   * Finds the list of competing tariffs for the given PowerType.
   */
  List<TariffSpecification> getCompetingTariffs (PowerType powerType)
  {
    List<TariffSpecification> result = competingTariffs.get(powerType);
    if (result == null)
    {
      result = new ArrayList<TariffSpecification>();
      competingTariffs.put(powerType, result);
    }
    return result;
  }

  /**
   * Adds a new competing tariff to the list.
   */
  private void addCompetingTariff (TariffSpecification spec)
  {
    getCompetingTariffs(spec.getPowerType()).add(spec);
  }

  /**
   * Adds a new own tariff to the list.
   */
  private void addOwnTariff(TariffSpecification spec)
  {
    List<TariffSpecification> tariffs = ownTariffs.get(spec.getPowerType());

    if(tariffs == null)
      tariffs = new ArrayList<>();

    tariffs.add(spec);
    ownTariffs.put(spec.getPowerType(), tariffs);
  }

  /**
   * Removes a old own tariff from the list.
   */
  private void removeOwnTariff(TariffSpecification spec)
  {
    List<TariffSpecification> tariffs = ownTariffs.get(spec.getPowerType());

    if(tariffs == null)
      return;

    tariffs.remove(spec);
    ownTariffs.put(spec.getPowerType(), tariffs);
  }

  /**
   * Returns total usage for a given timeslot (represented as a simple index).
   */
  @Override
  public double collectUsage (int index)
  {
    double result = 0.0;
    for (Map<CustomerInfo, CustomerRecord> customerMap : customerSubscriptions.values())
    {
      for (CustomerRecord record : customerMap.values())
      {
        try
        {
          double usage = record.getUsage(index);
          result += usage;
        }
        catch(Exception e) {}
      }
    }
    return -result; // convert to needed energy account balance
  }

  // -------------- Message handlers -------------------

  public synchronized void handleMessage(Competition comp) {
    this.gamename = comp.getName();
    numberOfBroker = comp.getBrokers().size() - 1; 
  }

  /**
   * Handles CustomerBootstrapData by populating the customer model
   * corresponding to the given customer and power type. This gives the
   * broker a running start.
   */
   public synchronized void handleMessage (CustomerBootstrapData cbd)
   {
     CustomerInfo customer = customerRepo.findByNameAndPowerType(cbd.getCustomerName(), cbd.getPowerType());
     CustomerRecord record = getCustomerRecordByPowerType(cbd.getPowerType(), customer);
     int subs = record.subscribedPopulation;
     record.subscribedPopulation = customer.getPopulation();

     String custName = cbd.getCustomerName();

     for (int i = 0; i < cbd.getNetUsage().length; i++) {
       record.produceConsume(cbd.getNetUsage()[i], i);
       CustomerSubscriptionInformation customerSubscription = new CustomerSubscriptionInformation(custName,cbd.getPowerType(),customer.getPopulation(),customer.getPopulation());
       custUsageInfo.setCustomerSubscriptionList(i+this.OFFSET,customerSubscription);
       custUsageInfo.setCustomerSubscriptionMap(custName, i+this.OFFSET, customerSubscription);
     }
     record.subscribedPopulation = subs;
   }

  /**
   * Handles a TariffSpecification. These are sent by the server when new tariffs are
   * published. If it's not ours, then it's a competitor's tariff. We keep track of
   * competing tariffs locally, and we also store them in the tariffRepo.
   */
  public synchronized void handleMessage (TariffSpecification spec)
  {
    // System.out.println("Broker : " + spec.getBroker().getUsername() + " :: Spec : " + spec.getPowerType() + " :: " + spec.getId() + " :: " + Helper.evaluateCost(spec, utilityFlag));
    Broker theBroker = spec.getBroker();

    if (brokerContext.getBrokerUsername().equals(theBroker.getUsername()))
    {
      if (theBroker != brokerContext.getBroker())
        // strange bug, seems harmless for now
        log.info("Resolution failed for broker " + theBroker.getUsername());
      // if it's ours, just log it, because we already put it in the repo
      TariffSpecification original = tariffRepo.findSpecificationById(spec.getId());
      if (null == original)
        log.error("Spec " + spec.getId() + " not in local repo");
      log.info("published " + spec);
    }
    else
    {
      // otherwise, keep track of competing tariffs, and record in the repo
      addCompetingTariff(spec);
      tariffRepo.addSpecification(spec);
    }
  }

  /**
   * Handles a TariffStatus message. This should do something when the status
   * is not SUCCESS.
   */
  public synchronized void handleMessage (TariffStatus ts)
  {
    log.info("TariffStatus: " + ts.getStatus());
  }

  /**
   * Handles a TariffTransaction. We only care about certain types: PRODUCE,
   * CONSUME, SIGNUP, and WITHDRAW.
   */
   public synchronized void handleMessage(TariffTransaction ttx)
   {
    //  if(ttx.getCustomerInfo().getName().equals("CentervilleHomes"))
    //   System.out.println("Charge: " + (ttx.getCharge()/ttx.getKWh()));

     int currentTimeslot = timeslotRepo.currentTimeslot().getSerialNumber();
     tariffMarketInformation.updateBrokerTariffRepo(currentTimeslot, ttx.getTariffSpec(), ttx.getCharge(), ttx.getKWh());

     if((TariffTransaction.Type.CONSUME == ttx.getTxType()) || (TariffTransaction.Type.PRODUCE == ttx.getTxType()))
     {
       int hour = 0;
       int day = 0;
       Double usagePerPopulation = 0.0;
       Double chargePerUnit = 0.0;

       String customerName = ttx.getCustomerInfo().getName();
       PowerType pType = ttx.getCustomerInfo().getPowerType();
       Integer subscribedPopulation = ttx.getCustomerCount();
       Integer timeslot = ttx.getPostedTimeslotIndex();
       hour = timeslotRepo.findBySerialNumber(timeslot).getStartInstant().toDateTime().getHourOfDay();
       day = timeslotRepo.findBySerialNumber(timeslot).getStartInstant().toDateTime().getDayOfWeek();

       if(ttx.getKWh() != 0.0) {
         usagePerPopulation = Math.abs(ttx.getKWh() / subscribedPopulation);
         chargePerUnit =  Math.abs(ttx.getCharge() / ttx.getKWh());
       }
       UsageRecord usageRecord = new UsageRecord(hour, day, subscribedPopulation, chargePerUnit, usagePerPopulation);

       custUsageInfo.setCustomerActualUsage(customerName, timeslot, usageRecord);

       int index = (ttx.getPostedTimeslotIndex() - 24) % brokerContext.getUsageRecordLength();
       custUsageInfo.setCustomerUsageProjectionMap(ttx.getCustomerInfo().getName(),index, usagePerPopulation);

       CustomerSubscriptionInformation customerSubscription = new CustomerSubscriptionInformation(customerName,pType, subscribedPopulation,ttx.getCustomerInfo().getPopulation());
       custUsageInfo.setCustomerSubscriptionList(timeslot,customerSubscription);
       custUsageInfo.setCustomerSubscriptionMap(customerName, timeslot, customerSubscription);

       custUsageInfo.setCustomerUsageMap(customerName, ttx.getPostedTimeslotIndex(), usagePerPopulation);
     }

     // make sure we have this tariff

     TariffSpecification newSpec = ttx.getTariffSpec();
     if (newSpec == null) {
       log.error("TariffTransaction type=" + ttx.getTxType() + " for unknown spec");
     }
     else {
       TariffSpecification oldSpec = tariffRepo.findSpecificationById(newSpec.getId());
       if (oldSpec != newSpec) {
         log.error("Incoming spec " + newSpec.getId() + " not matched in repo");
       }
     }
     TariffTransaction.Type txType = ttx.getTxType();
     CustomerRecord record = getCustomerRecordByTariff(ttx.getTariffSpec(), ttx.getCustomerInfo());

     if (TariffTransaction.Type.SIGNUP == txType) {
       // keep track of customer counts
       record.signup(ttx.getCustomerCount());
     }
     else if (TariffTransaction.Type.WITHDRAW == txType) {
       // customers presumably found a better deal
       record.withdraw(ttx.getCustomerCount());
     }
     else if (ttx.isRegulation()) {
       // Regulation transaction -- we record it as production/consumption
       // to avoid distorting the customer record.
       log.debug("Regulation transaction from {}, {} kWh for {}",
                 ttx.getCustomerInfo().getName(),
                 ttx.getKWh(), ttx.getCharge());
       record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
     }
     else if (TariffTransaction.Type.PRODUCE == txType) {
       // if ttx count and subscribe population don't match, it will be hard
       // to estimate per-individual production
       if (ttx.getCustomerCount() != record.subscribedPopulation) {
         log.warn("production by subset {}  of subscribed population {}",
                  ttx.getCustomerCount(), record.subscribedPopulation);
       }

       record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
     }
     else if (TariffTransaction.Type.CONSUME == txType) {
       if (ttx.getCustomerCount() != record.subscribedPopulation) {
         log.warn("consumption by subset {} of subscribed population {}",
                  ttx.getCustomerCount(), record.subscribedPopulation);
       }

       record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
     }
   }

  /**
   * Handles a TariffRevoke message from the server, indicating that some
   * tariff has been revoked.
   */
  public synchronized void handleMessage (TariffRevoke tr)
  {
    Broker source = tr.getBroker();
    log.info("Revoke tariff " + tr.getTariffId() + " from " + tr.getBroker().getUsername());

    // if it's from some other broker, we need to remove it from the
    // tariffRepo, and from the competingTariffs list
    if (!(source.getUsername().equals(brokerContext.getBrokerUsername()))) {
      log.info("clear out competing tariff");
      TariffSpecification original = tariffRepo.findSpecificationById(tr.getTariffId());
      if (null == original) {
        log.warn("Original tariff " + tr.getTariffId() + " not found");
        return;
      }
      tariffRepo.removeSpecification(original.getId());
      List<TariffSpecification> candidates = competingTariffs.get(original.getPowerType());
      if (null == candidates) {
        log.warn("Candidate list is null");
        return;
      }
      candidates.remove(original);
    }
  }

  @Override // from Activatable
  public synchronized void activate (int timeslotIndex)
  {
    Double misoDemand = marketManager.get_exponentialaverage_misodemand();

    if(timeslotIndex == 360)
    {
      tariffHeuristics = new TariffHeuristics(this.brokerContext);
      adjustMarketShareMDP = new AdjustMarketShareMDP(this.gamename, numberOfBroker);

      HIGHER_BOUND = adjustMarketShareMDP.getHigherOptimalMarketshareUsingNumBrokers(numberOfBroker);
      MIDDLE_BOUND = adjustMarketShareMDP.getMiddleOptimalMarketshareUsingNumBrokers(numberOfBroker);
      // Double LOWER_BOUND = adjustMarketShareMDP.getLowerOptimalMarketshareUsingNumbrokers(numberOfBroker);

      createInitialTariffs(); 
    }
    else
    {
      try
      {
        // System.out.println(tariffHeuristics.toString());
        updateCurrentTariffStats(timeslotIndex); 

        if((timeslotIndex - 360) % 24 == 0) 
        {
          updateCashFlowInfo(timeslotIndex);
          if(GAME_PROFITABLE_FLAG && !isGameProfitable(timeslotIndex, misoDemand))
            removeAllTariffs();
        }

        if((timeslotIndex != 360) && ((timeslotIndex-360) % TARIFF_HEALTH_CHECK == 0))               
          tariffHeathCheck(timeslotIndex);

        if(!GAME_PROFITABLE_FLAG && MISO_GAME_PROFITABLE_FLAG && (timeslotIndex != 360) && ((timeslotIndex-360) % UPDATE_INTERVAL_P == 0))
          improveMISOProductionTariffs(timeslotIndex);

        if(GAME_PROFITABLE_FLAG && (timeslotIndex != 360) && ((timeslotIndex-360) % UPDATE_INTERVAL_C == 0))
          improveConsumptionTariffs(timeslotIndex);   

        try
        {
          if(GAME_PROFITABLE_FLAG && (timeslotIndex != 360) && ((timeslotIndex-360) % UPDATE_INTERVAL_P == 0))
            improveProductionTariffs(timeslotIndex);
        }
        catch(Exception e){}
         
        try
        {
          if(GAME_PROFITABLE_FLAG && (timeslotIndex != 360) && ((timeslotIndex-360) % (UPDATE_INTERVAL_C) == 0))
          {
            improveThermalStorageConsumptionTariffs(timeslotIndex);  
            // improveInteruptibleConsumptionTariffs(timeslotIndex);
            improveBatteryStorageTariffs(timeslotIndex);  
          }
        }
        catch(Exception e){}

        // Publish new tariff and reset market-share
        if((timeslotIndex != 360) && ((timeslotIndex-360) % UPDATE_INTERVAL_C == 0)) {
          List<TariffSpecification> candidates1 = ownTariffs.get(PowerType.CONSUMPTION);
          List<TariffSpecification> candidates2 = ownTariffs.get(PowerType.THERMAL_STORAGE_CONSUMPTION);
          List<TariffSpecification> candidates3 = ownTariffs.get(PowerType.INTERRUPTIBLE_CONSUMPTION);

          List<TariffSpecification> consCandidates;

          if((candidates1 != null) && (candidates1.size() != 0))
              consCandidates = candidates1;
          else
              consCandidates = new ArrayList<>();

          if((candidates2 != null) && (candidates2.size() != 0))
              consCandidates = Stream.concat(consCandidates.stream(), candidates2.stream()).collect(Collectors.toList());
          if((candidates3 != null) && (candidates3.size() != 0))
              consCandidates = Stream.concat(consCandidates.stream(), candidates3.stream()).collect(Collectors.toList());

          for(TariffSpecification spec: consCandidates)
            tariffHeuristics.resetMarketShareinStatBook(spec);

          List<TariffSpecification> storCandidates = ownTariffs.get(PowerType.BATTERY_STORAGE);
          if((storCandidates != null) && (storCandidates.size() != 0))
          {
            for(TariffSpecification spec: storCandidates)
              tariffHeuristics.resetMarketShareinStatBook(spec);
          }
        }

        if((timeslotIndex != 360) && ((timeslotIndex-360) % UPDATE_INTERVAL_P == 0)) 
        {
          List<TariffSpecification> prodCandidates = ownTariffs.get(PowerType.PRODUCTION);
          if((prodCandidates != null) && (prodCandidates.size() != 0))
          {
            for(TariffSpecification spec: prodCandidates)
              tariffHeuristics.resetMarketShareinStatBook(spec);
          }
        }
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
    }

    try
    {
      updateMarketShare(timeslotIndex);       // Update broker's market share (volume)
    }
    catch(Exception e)
    {
      e.printStackTrace();
    }

    // if(timeslotIndex != 360)
    //   storetoMongoDB(timeslotIndex);

    for (CustomerRecord record: notifyOnActivation)
      record.activate();
  }

  private Double findCheapestOppositionConsumptionTariff()
  {
      List<TariffSpecification> consCandidates = getCompetingTariffs(PowerType.CONSUMPTION);
      double cheapest = -Double.MAX_VALUE;
      for(TariffSpecification item: consCandidates) {
          Double cost = Helper.evaluateCost(item, utilityFlag);
          if(cost != -1.0)
              cheapest = Math.max(cheapest, cost);
      }
      return cheapest; 
  }

  public void updateCashFlowInfo(Integer timeslot)
  {
    cashPositionInformation = messageManager.getCashPositionInformation();

    Double currentCashPosition = cashPositionInformation.getCashPosition(timeslot);
    Double prevDayCashPosition = cashPositionInformation.getCashPosition(timeslot-24);

    Double delta = (currentCashPosition - prevDayCashPosition) / Math.abs(currentCashPosition);

    if(cashFlowInfo.size() < WAIT_INTERVAL)
        cashFlowInfo.add(delta);
    else 
    {
        cashFlowInfo.remove(0);
        cashFlowInfo.add(delta);
    }
  }

  public Boolean isGameProfitable(Integer timeslot, Double misoDemand)
  {        
    // Boolean hope = false;
    // for(Double item: cashFlowInfo)
    // {
    //   if(item > 0.0)
    //   {
    //     hope = true;
    //     break;
    //   }
    // }
    cashPositionInformation = messageManager.getCashPositionInformation();
    Double currentCashPosition = cashPositionInformation.getCashPosition(timeslot);

    if((currentCashPosition > WORST_CASH_POSITION) && (misoDemand < 1400.0))
        return true;
    else
    {
        GAME_PROFITABLE_FLAG = false;
        return false;
    }
  }

  public void removeAllTariffs()
  {
      List<TariffSpecification> tariffsToBeRemoved = new ArrayList<>();

      for(List<TariffSpecification> outer: ownTariffs.values())
      {
          for(TariffSpecification spec: outer)
          {
              tariffsToBeRemoved.add(spec);
          }
      }

      for(TariffSpecification spec: tariffsToBeRemoved)
      {
        removeOwnTariff(spec);
        tariffHeuristics.removeFromStatBook(spec);
        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
        customerSubscriptions.remove(spec);
        tariffRepo.removeSpecification(spec.getId());
        brokerContext.sendMessage(revoke);
      }
  }

  public void updateCurrentTariffStats(Integer timeslot)
  {
    wholesaleMarketInformation = messageManager.getWholesaleMarketInformation();
    marketTransactionInformation = messageManager.getMarketTransactionInformation();
    distributionInformation = messageManager.getDistributionInformation();

    // Update accounting details of each tariff for the current timeslot
    for(Map.Entry<PowerType, List<TariffSpecification>> outer: ownTariffs.entrySet())
    {
      for(TariffSpecification spec: outer.getValue())
      {
        double tariffRevenue = tariffMarketInformation.getTariffRevenue(timeslot, spec);
        double tariffUsage = tariffMarketInformation.getTariffUsage(timeslot, spec);
        double wholesaleCost = tariffUsage * wholesaleMarketInformation.getAvgMCP(timeslot) / 1000.0;   // convert to per KWh

        double tariffNetDemand = Math.abs(tariffMarketInformation.getTariffUsage(timeslot));
        double netConsumptionDemand = Math.abs(distributionInformation.getTotalConsumption(timeslot));
        double netProductionDemand = Math.abs(distributionInformation.getTotalProduction(timeslot));

        double tariffMarketShare = 0.0;

        if(spec.getPowerType().isConsumption() && (netConsumptionDemand != 0.0))
          tariffMarketShare = Math.abs(tariffUsage / netConsumptionDemand);
        else if(spec.getPowerType().isProduction() && (netProductionDemand != 0.0))
          tariffMarketShare = Math.abs(tariffUsage / netProductionDemand);

        double netDemand = netConsumptionDemand - netProductionDemand;
        double percTariffDemand = tariffNetDemand / netDemand;

        tariffHeuristics.updateTariffStats(spec.getId(), tariffMarketShare, tariffUsage, tariffRevenue, wholesaleCost, netDemand, percTariffDemand);
      }
    }
  }

  private void createInitialTariffs()
  {
    List<TariffSpecification> specs = tariffHeuristics.getInitialTariffs();

    for(TariffSpecification spec: specs)
    {
      addOwnTariff(spec);

      // Start recording accounting details of this tariff
      tariffHeuristics.createTariffStateBook(spec);

      customerSubscriptions.put(spec, new LinkedHashMap<>());
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
    }
  }

  private void tariffHeathCheck(Integer timeslot)
  {
    List<TariffSpecification> candidates1 = ownTariffs.get(PowerType.CONSUMPTION);
    List<TariffSpecification> candidates2 = ownTariffs.get(PowerType.THERMAL_STORAGE_CONSUMPTION);
    List<TariffSpecification> candidates3 = ownTariffs.get(PowerType.INTERRUPTIBLE_CONSUMPTION);
    
    List<TariffSpecification> consCandidates;

    if((candidates1 != null) && (candidates1.size() != 0))
      consCandidates = candidates1;
    else
      consCandidates = new ArrayList<>();

    if((candidates2 != null) && (candidates2.size() != 0))
      consCandidates = Stream.concat(consCandidates.stream(), candidates2.stream()).collect(Collectors.toList());
    if((candidates3 != null) && (candidates3.size() != 0))
      consCandidates = Stream.concat(consCandidates.stream(), candidates3.stream()).collect(Collectors.toList());

    // Check if all the current consumption tariffs are healthy, if not remove unhealthy tariff
    List<TariffSpecification> removeConsTariffs = new ArrayList<>();
    for(TariffSpecification spec: consCandidates) 
    {
      if(!tariffHeuristics.isTariffHealthy(spec, TARIFF_LOWER_BOUND, TARIFF_UPPER_BOUND, false)) // 'false' for CONSUMPTION customers
          removeConsTariffs.add(spec);
    }

    for(TariffSpecification spec: removeConsTariffs) 
    {
      removeOwnTariff(spec);
      tariffHeuristics.removeFromStatBook(spec);
      TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
      customerSubscriptions.remove(spec);
      tariffRepo.removeSpecification(spec.getId());
      brokerContext.sendMessage(revoke);
    }

    // Check if all the current production tariffs are healthy, if not remove that tariff
    List<TariffSpecification> prodCandidates = ownTariffs.get(PowerType.PRODUCTION);

    if((prodCandidates != null) && (prodCandidates.size() != 0))
    {
      List<TariffSpecification> removeProdTariffs = new ArrayList<>();
      for(TariffSpecification spec: prodCandidates) 
      {
        if(!tariffHeuristics.isTariffHealthy(spec, TARIFF_LOWER_BOUND, TARIFF_UPPER_BOUND, true)) // 'true' for PRODUCTION customers
          removeProdTariffs.add(spec);
      }

      for(TariffSpecification spec: removeProdTariffs) 
      {
        removeOwnTariff(spec);
        tariffHeuristics.removeFromStatBook(spec);
        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
        customerSubscriptions.remove(spec);
        tariffRepo.removeSpecification(spec.getId());
        brokerContext.sendMessage(revoke);
      }
    }

    // Check if all the current storage tariffs are healthy, if not remove that tariff
    List<TariffSpecification> storCandidates = ownTariffs.get(PowerType.BATTERY_STORAGE);

    if((storCandidates != null) && (storCandidates.size() != 0))
    {
      List<TariffSpecification> removeStorTariffs = new ArrayList<>();
      for(TariffSpecification spec: storCandidates) 
      {
        if(!tariffHeuristics.isTariffHealthy(spec, TARIFF_LOWER_BOUND, TARIFF_UPPER_BOUND, false)) // 'false' for STORAGE customers
          removeStorTariffs.add(spec);
      }

      for(TariffSpecification spec: removeStorTariffs) 
      {
        removeOwnTariff(spec);
        tariffHeuristics.removeFromStatBook(spec);
        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
        customerSubscriptions.remove(spec);
        tariffRepo.removeSpecification(spec.getId());
        brokerContext.sendMessage(revoke);
      }
    }
  }

  private void improveConsumptionTariffs(Integer timeslot)
  {
    wholesaleMarketInformation = messageManager.getWholesaleMarketInformation();

    List<TariffSpecification> candidates1 = ownTariffs.get(PowerType.CONSUMPTION);
    List<TariffSpecification> candidates2 = ownTariffs.get(PowerType.THERMAL_STORAGE_CONSUMPTION);
    List<TariffSpecification> candidates3 = ownTariffs.get(PowerType.INTERRUPTIBLE_CONSUMPTION);

    List<TariffSpecification> consCandidates;
    Double currentAvgRate = findCheapestOppConsTariff();

    if((candidates1 != null) && (candidates1.size() != 0))
    {
        consCandidates = candidates1;
        currentAvgRate = 0.0;

        for(TariffSpecification t: candidates1)
          currentAvgRate += Helper.evaluateCost(t, utilityFlag);

        currentAvgRate /= candidates1.size();
    }
    else
        consCandidates = new ArrayList<>();

    if((candidates2 != null) && (candidates2.size() != 0))
        consCandidates = Stream.concat(consCandidates.stream(), candidates2.stream()).collect(Collectors.toList());
    if((candidates3 != null) && (candidates3.size() != 0))
        consCandidates = Stream.concat(consCandidates.stream(), candidates3.stream()).collect(Collectors.toList());

    // System.out.println("At Timeslot " + timeslot + " ...");
    // System.out.println("Current Average Rate: " + currentAvgRate);

    try 
    {
      Double currentMarketShare = tariffHeuristics.getCumulativeMarketShare(consCandidates);
      Double optimalMarketShare = (HIGHER_BOUND + MIDDLE_BOUND) / 2;
      Double cheapestOppConsTariffAvgRate = findCheapestOppositionConsumptionTariff();

      // System.out.println("Current Market-share: " + currentMarketShare + " :: -- :: Optimal Market-share: " + optimalMarketShare);
      // System.out.println("Cheapest Opponent Tariff: " + cheapestOppConsTariffAvgRate);


      if(isTraining && (timeslot != (UPDATE_INTERVAL_C + 360)))  // Reward calculation ... should start from 408
      {
        adjustMarketShareMDP.updateQTable(optimalMarketShare, currentMarketShare);
        adjustMarketShareMDP.saveMDP();
        
        adjustMarketShareMDP.resetMDP();
        System.out.println("MDP is reset !!!\n\n");
      }

      adjustMarketShareMDP.setState(optimalMarketShare, currentMarketShare);
      List<Double> newTariffsAvgRatesTemp = Arrays.asList(adjustMarketShareMDP.takeAction(currentAvgRate, cheapestOppConsTariffAvgRate));

      // Add midpoint heuristics here
      List<Double> newTariffsAvgRates = new ArrayList<>();
      for(Double item: newTariffsAvgRatesTemp)
        newTariffsAvgRates.add(item);
        
      Double midPointRate = (currentAvgRate + cheapestOppConsTariffAvgRate) / 2;
      newTariffsAvgRates.add(midPointRate);

      Integer actionIndex = adjustMarketShareMDP.getAction();

      if(actionIndex != 0)  // Only change if action is not Maintain tariff
      {
        List<TariffSpecification> tariffsToBeRemoved = new ArrayList<>();
        for(TariffSpecification spec: consCandidates)
            tariffsToBeRemoved.add(spec);

        for(TariffSpecification spec: tariffsToBeRemoved) // need to do after action selection, if not maintain, then revoke tariff
        {
          removeOwnTariff(spec);
          tariffHeuristics.removeFromStatBook(spec);
          TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
          customerSubscriptions.remove(spec);
          tariffRepo.removeSpecification(spec.getId());
          brokerContext.sendMessage(revoke);
        }

        // Check exististance of similar tariffs 
        List<TariffSpecification> existingConsTariffs = ownTariffs.get(PowerType.CONSUMPTION);
        List<Double> repeatedTariffs = new ArrayList<>();
        for(Double newAvgRate: newTariffsAvgRates) 
        {
          for(TariffSpecification exisSpec: existingConsTariffs) 
          {
            Double exisAvgRate = Helper.evaluateCost(exisSpec, utilityFlag);
            if((exisAvgRate != -1.0) && ((Math.abs(newAvgRate - exisAvgRate) <= MIN_DIFF_BW_TARIFFS))) 
            {
                // System.out.println("New rate " + newAvgRate + " is close to existing / revoked tariff " + exisAvgRate + " rates");
                repeatedTariffs.add(newAvgRate);
                break;
            }
          }
        }
        newTariffsAvgRates.removeAll(repeatedTariffs);
        // ##############################################################################################

        Double minViablePrice = wholesaleMarketInformation.getMeanMarketPrice();
        Boolean viable = false;
        for(Double avgRate: newTariffsAvgRates) 
        {
          // Ensure viability of tariffs
          if((avgRate != null) && (Math.abs(avgRate) > minViablePrice) && (avgRate < 0.0)) 
          {
            viable = true;
            TariffSpecification newSpec = tariffHeuristics.improveConsTariffs(avgRate, PowerType.CONSUMPTION);
            addOwnTariff(newSpec);
            tariffHeuristics.createTariffStateBook(newSpec);
            customerSubscriptions.put(newSpec, new LinkedHashMap<>());
            tariffRepo.addSpecification(newSpec);
            brokerContext.sendMessage(newSpec);
          }
        }

        // corner-case when none of the new tariff is viable 
        if((!viable) && ((ownTariffs.get(PowerType.CONSUMPTION) == null) || (ownTariffs.get(PowerType.CONSUMPTION).size() == 0))) 
        {
            // System.out.println("Lowest possible tariff");
            // use minViablePrice as new avgRate for CONSUMPTION tariff 
            Double newCandAvgRate = -minViablePrice;
            for(TariffSpecification exisSpec: existingConsTariffs) 
            {
                Double exisAvgRate = Helper.evaluateCost(exisSpec, utilityFlag);
                if((exisAvgRate != -1.0) && ((Math.abs(newCandAvgRate - exisAvgRate) <= MIN_DIFF_BW_TARIFFS)))
                return;
            }
            TariffSpecification viableTariff = tariffHeuristics.improveConsTariffs(newCandAvgRate, PowerType.CONSUMPTION);
            addOwnTariff(viableTariff);
            tariffHeuristics.createTariffStateBook(viableTariff);
            customerSubscriptions.put(viableTariff, new LinkedHashMap<>());
            tariffRepo.addSpecification(viableTariff);
            brokerContext.sendMessage(viableTariff);
        }
      }
    } 
    catch (Exception e)
    {
      System.out.println("error in adjustmarketshare mdp strategy");
    }
  }

  private void improveThermalStorageConsumptionTariffs(Integer timeslot)
  {
    List<TariffSpecification> TSCtariffs = ownTariffs.get(PowerType.THERMAL_STORAGE_CONSUMPTION);

    if(TSCtariffs != null)
    {
      List<TariffSpecification> tariffsToBeRemoved = new ArrayList<>();
      for(TariffSpecification spec: TSCtariffs)
        tariffsToBeRemoved.add(spec);
          
      for(TariffSpecification spec: tariffsToBeRemoved) 
      {
        removeOwnTariff(spec);
        tariffHeuristics.removeFromStatBook(spec);
        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
        customerSubscriptions.remove(spec);
        tariffRepo.removeSpecification(spec.getId());
        brokerContext.sendMessage(revoke);
      }
    }

    List<TariffSpecification> candidates = ownTariffs.get(PowerType.CONSUMPTION);
    Double newCandAvgRate = tariffHeuristics.getMostSubscribedTariffAvgRate(candidates);

    if(newCandAvgRate != null)
    {
      TariffSpecification spec = tariffHeuristics.improveConsTariffs(newCandAvgRate, PowerType.THERMAL_STORAGE_CONSUMPTION);
      addOwnTariff(spec);
      tariffHeuristics.createTariffStateBook(spec);
      customerSubscriptions.put(spec, new LinkedHashMap<>());
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
    }
  }

  public void improveMISOProductionTariffs(Integer timeslot)
  {
    Double currentCashPosition = cashPositionInformation.getCashPosition(timeslot);

    if(misoCaseCashGradient == null)
      misoCaseCashGradient = cashPositionInformation.getCashPosition(timeslot) - cashPositionInformation.getCashPosition(timeslot-1);  // for the first instance, don't look back 24 timeslots
    else
      misoCaseCashGradient = 0.6*misoCaseCashGradient + 0.4*(cashPositionInformation.getCashPosition(timeslot) - cashPositionInformation.getCashPosition(timeslot-24));

    if((misoCaseCashGradient < MISO_CASE_CASH_GRADIENT_THRESHOLD) || (currentCashPosition < 2.25*WORST_CASH_POSITION)) 
    {
      // Remove all tariffs
      MISO_GAME_PROFITABLE_FLAG = false;
      // System.out.println("Removing all the tariffs from the market");
      removeAllTariffs();
      return;
    }
    
    // System.out.println("Min Viable Price in KWh: " + wholesaleMarketInformation.getMeanMarketPrice()/1.5);
    List<TariffSpecification> tariffsToBeRemoved = new ArrayList<>();
    List<TariffSpecification> prodCandidates = ownTariffs.get(PowerType.PRODUCTION);

    if((prodCandidates != null) && (prodCandidates.size() != 0))
    {
      Double curRate = 0.0;
      for(TariffSpecification spec: prodCandidates)
      {
        if(spec.getPowerType() == PowerType.PRODUCTION)
        {
          curRate = Math.abs(Helper.evaluateCost(spec, utilityFlag));
          tariffsToBeRemoved.add(spec);
        }
      }

      Double minViablePrice = wholesaleMarketInformation.getMeanMarketPrice()/1.5;

      if((curRate != 0.0) && (Math.abs(curRate - minViablePrice) > 0.015))
      {
        for(TariffSpecification spec: tariffsToBeRemoved)
        {
          removeOwnTariff(spec);
          tariffHeuristics.removeFromStatBook(spec);
          TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
          customerSubscriptions.remove(spec);
          tariffRepo.removeSpecification(spec.getId());
          brokerContext.sendMessage(revoke);
        }

        List<TariffSpecification> specs = tariffHeuristics.getDefaultBrokerTariffs(minViablePrice);
        for(TariffSpecification spec: specs) 
        {
          addOwnTariff(spec);
          tariffHeuristics.createTariffStateBook(spec);
          customerSubscriptions.put(spec, new LinkedHashMap<>());
          tariffRepo.addSpecification(spec);
          brokerContext.sendMessage(spec);
        }
      }
    }
    else
    {   
      Double minViablePrice = wholesaleMarketInformation.getMeanMarketPrice()/1.5;
      List<TariffSpecification> specs = tariffHeuristics.getDefaultBrokerTariffs(minViablePrice);
      for(TariffSpecification spec: specs) 
      {
        addOwnTariff(spec);
        tariffHeuristics.createTariffStateBook(spec);
        customerSubscriptions.put(spec, new LinkedHashMap<>());
        tariffRepo.addSpecification(spec);
        brokerContext.sendMessage(spec);
      }
    }
  }

  private void improveProductionTariffs(Integer timeslot)
  {
    wholesaleMarketInformation = messageManager.getWholesaleMarketInformation();

    // System.out.println("Min Viable Price: " + wholesaleMarketInformation.getMeanMarketPrice()/1.5);
    List<TariffSpecification> candidates1 = ownTariffs.get(PowerType.CONSUMPTION);
    List<TariffSpecification> candidates2 = ownTariffs.get(PowerType.THERMAL_STORAGE_CONSUMPTION);
    List<TariffSpecification> consCandidates = candidates1;
    if((candidates2 != null) && (candidates2.size() != 0))
      consCandidates = Stream.concat(candidates1.stream(), candidates2.stream()).collect(Collectors.toList());

    Double cMarketShareCons = tariffHeuristics.getCumulativeMarketShare(consCandidates);

    List<TariffSpecification> prodCandidates = ownTariffs.get(PowerType.PRODUCTION);
    Double minViablePrice = wholesaleMarketInformation.getMeanMarketPrice() / (1.0 + defaultMargin);

    // Remove worser than min viable price tariffs 
    List<TariffSpecification> notViableTariffs = new ArrayList<>();
    for(TariffSpecification exisSpec: prodCandidates) 
    {
      Double exisAvgRate = Helper.evaluateCost(exisSpec, utilityFlag);
      if((exisAvgRate != -1.0) && (Math.abs(exisAvgRate) > minViablePrice*DISCOUNT)) 
        notViableTariffs.add(exisSpec);
    }

    for(TariffSpecification spec: notViableTariffs)
    {
      removeOwnTariff(spec);
      tariffHeuristics.removeFromStatBook(spec);
      TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
      customerSubscriptions.remove(spec);
      tariffRepo.removeSpecification(spec.getId());
      brokerContext.sendMessage(revoke);
    }

    prodCandidates.removeAll(notViableTariffs);

    Double cMarketShare = tariffHeuristics.getCumulativeMarketShare(prodCandidates);
    Double costliestOppProdTariffAvgRate = findCostliestOppProdTariff();
    // System.out.println("Cumulative Market-share: " + cMarketShare + " :: OppBest: " + costliestOppProdTariffAvgRate);
    List<Double> newTariffsAvgRates = new ArrayList<>();
    TreeMap<Double, TariffSpecification> sortedCandidates = tariffHeuristics.sortTariffsByCost(prodCandidates, "PRODUCTION");
    Map.Entry<Double, TariffSpecification> cheapestEntry = sortedCandidates.pollLastEntry();
    Double ownCheapestRate = tariffHeuristics.decreseTariffAvgRate(costliestOppProdTariffAvgRate);   // a place-holder in case of no own tariff
    TariffSpecification ownCheapestTariff = null;

    if(cheapestEntry != null)
    {
      ownCheapestRate = cheapestEntry.getKey();
      ownCheapestTariff = cheapestEntry.getValue();
    }

    if(cMarketShare > PRODUCTION_HIGHER_BOUND)
    { 
      // Remove my cheapest tariff of all the types
      // System.out.println("Removing " + ownCheapestTariff.getId() + " ... ");
      removeOwnTariff(ownCheapestTariff);
      tariffHeuristics.removeFromStatBook(ownCheapestTariff);
      TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), ownCheapestTariff);
      customerSubscriptions.remove(ownCheapestTariff);
      tariffRepo.removeSpecification(ownCheapestTariff.getId());
      brokerContext.sendMessage(revoke);

      Double newAvgRate1 = tariffHeuristics.decreseTariffAvgRate(ownCheapestRate);            // make tariff slighly costilier than removed tariff
      Double newAvgRate2 = (ownCheapestRate + costliestOppProdTariffAvgRate) / 2;              // make tariff based on oppBest tariff

      newTariffsAvgRates.add(newAvgRate1);
      newTariffsAvgRates.add(newAvgRate2);
    }
    else if(cMarketShare < LOWER_BOUND)
    {
      Double newAvgRate1 = (ownCheapestRate + costliestOppProdTariffAvgRate) / 2;            // make tariff based on oppBest tariff
      Double newAvgRate2 = tariffHeuristics.increseTariffAvgRate(ownCheapestRate);          // make tariff slighly cheaper than our cheapest tariff

      newTariffsAvgRates.add(newAvgRate1);
      newTariffsAvgRates.add(newAvgRate2);  
    }
    else if(cMarketShare < MIDDLE_BOUND)
    {
      // make the least subscribed tariff's avgRate slighly cheaper and use that as new avgRate for CONSUMPTION tariff 
      Double newCandAvgRate = tariffHeuristics.getLeastSubscribedTariffAvgRate(prodCandidates);

      if(newCandAvgRate != null)
      {
          newCandAvgRate = tariffHeuristics.increseTariffAvgRate(newCandAvgRate);               // make tariff slighly cheaper than our cheapest tariff
          newTariffsAvgRates.add(newCandAvgRate);
      }
    }

    // Check exististance of similar tariffs 
    List<TariffSpecification> existingConsTariffs = ownTariffs.get(PowerType.PRODUCTION);
    List<Double> repeatedTariffs = new ArrayList<>();

    for(Double newAvgRate: newTariffsAvgRates)
    {
      for(TariffSpecification exisSpec: existingConsTariffs)
      {
        Double exisAvgRate = Helper.evaluateCost(exisSpec, utilityFlag);

        if((exisAvgRate != -1.0) && (Math.abs(newAvgRate - exisAvgRate) <= MIN_DIFF_BW_TARIFFS))
        {
          // System.out.println("New rate " + newAvgRate + " is close to existing tariff " + exisSpec.getId() + " rates");
          repeatedTariffs.add(newAvgRate);
          break;
        }
      }
    }
    newTariffsAvgRates.removeAll(repeatedTariffs);

    if(cMarketShareCons > MIDDLE_BOUND)
      minViablePrice *= (1.0 + defaultMargin);   // 1.5 times of wholesale market cost, so paying higher than wholesale market price

    Boolean viable = false;

    for(Double avgRate: newTariffsAvgRates)
    {
      // Ensure viability of tariffs
      if((avgRate > 0.0) && (Math.abs(avgRate) < minViablePrice))         
      {
        viable = true;
        List<TariffSpecification> newTariffs = new ArrayList<>();
        newTariffs.add(tariffHeuristics.improveProdTariffs(avgRate, PowerType.PRODUCTION));
        for(TariffSpecification spec: newTariffs) 
        {
          addOwnTariff(spec);
          tariffHeuristics.createTariffStateBook(spec); 
          customerSubscriptions.put(spec, new LinkedHashMap<>());
          tariffRepo.addSpecification(spec);
          brokerContext.sendMessage(spec);
        }
      }
    }

    // corner-case when none of the new tariff is viable 
    if((newTariffsAvgRates.size() != 0) && (!viable))
    {
      // System.out.println("Lowest possible tariff");

      // use minViablePrice as new avgRate for PRODUCTION tariff 
      Double newCandAvgRate = minViablePrice;

      for(TariffSpecification exisSpec: existingConsTariffs)
      {
        Double exisAvgRate = Helper.evaluateCost(exisSpec, utilityFlag);
        if((exisAvgRate != -1.0) && (Math.abs(newCandAvgRate - exisAvgRate) <= MIN_DIFF_BW_TARIFFS))
          return;
      }

      TariffSpecification viableTariff = tariffHeuristics.improveProdTariffs(newCandAvgRate, PowerType.PRODUCTION);
      addOwnTariff(viableTariff);
      tariffHeuristics.createTariffStateBook(viableTariff);
      customerSubscriptions.put(viableTariff, new LinkedHashMap<>());
      tariffRepo.addSpecification(viableTariff);
      brokerContext.sendMessage(viableTariff);
    }
  }
 
  private double findCheapestOppConsTariff()
  {
    List<TariffSpecification> consCandidates = getCompetingTariffs(PowerType.CONSUMPTION);

    double cheapest = -Double.MAX_VALUE;

    for(TariffSpecification item: consCandidates)
    {
      double cost = Helper.evaluateCost(item, utilityFlag);

      if(cost != -1.0)
        cheapest = Math.max(cheapest, cost);
    }

    return cheapest; 
  }

  private Double findCostliestOppProdTariff()
  {
    List<TariffSpecification> candidates1 = getCompetingTariffs(PowerType.PRODUCTION);
    List<TariffSpecification> candidates2 = getCompetingTariffs(PowerType.WIND_PRODUCTION);
    List<TariffSpecification> candidates3 = getCompetingTariffs(PowerType.SOLAR_PRODUCTION);

    List<TariffSpecification> newList = Stream.concat(candidates1.stream(), candidates2.stream()).collect(Collectors.toList());
    List<TariffSpecification> prodCandidates = Stream.concat(newList.stream(), candidates3.stream()).collect(Collectors.toList());

    double costliest = -Double.MAX_VALUE;

    for(TariffSpecification item: prodCandidates)
    {
      Double cost = Helper.evaluateCost(item, utilityFlag);

      if(cost != -1.0)
        costliest = Math.max(costliest, cost);
    }

    return costliest; 
  }

  public void updateMarketShare(Integer timeslot)
  {
    distributionInformation = messageManager.getDistributionInformation();

    Pair<Double, Double> item = tariffMarketInformation.getConsProdTariffUsage(timeslot);
    Double brokerNetUsageC = Math.abs(item.getKey());
    Double brokerNetUsageP = Math.abs(item.getValue());

    // System.out.println("Broker's Net Usage: " + brokerNetUsageC + " at timeslot " + timeslot);

    Double marketShareC = -1.0;
    Double marketShareP = -1.0;

    if(distributionInformation.getTotalConsumption(timeslot) != 0.0)
      marketShareC = Math.min(1.0, Math.abs(brokerNetUsageC / distributionInformation.getTotalConsumption(timeslot)));

    if(distributionInformation.getTotalProduction(timeslot) != 0.0)
      marketShareP = Math.min(1.0, Math.abs(brokerNetUsageP / distributionInformation.getTotalProduction(timeslot)));

    // System.out.println("Consumption Market-share: " + marketShareC);
    // System.out.println("Production Market-share: " + marketShareP);

    tariffMarketInformation.setMarketShareVolumeMapC(timeslot, marketShareC);
    tariffMarketInformation.setMarketShareVolumeMapP(timeslot, marketShareP);
  }

  public void improveInteruptibleConsumptionTariffs(Integer timeslot)
  {
    List<TariffSpecification> ICtariffs = ownTariffs.get(PowerType.INTERRUPTIBLE_CONSUMPTION);

    if(ICtariffs != null)
    {
      List<TariffSpecification> tariffsToBeRemoved = new ArrayList<>();
      for(TariffSpecification spec: ICtariffs)
        tariffsToBeRemoved.add(spec);

      for(TariffSpecification spec: tariffsToBeRemoved) 
      {
        removeOwnTariff(spec);
        tariffHeuristics.removeFromStatBook(spec);
        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
        customerSubscriptions.remove(spec);
        tariffRepo.removeSpecification(spec.getId());
        brokerContext.sendMessage(revoke);
      }
    }

    List<TariffSpecification> candidates = ownTariffs.get(PowerType.CONSUMPTION);
    Double newCandAvgRate = tariffHeuristics.getMostSubscribedTariffAvgRate(candidates);

    if(newCandAvgRate != null)
    {
      TariffSpecification spec = tariffHeuristics.improveConsTariffs(newCandAvgRate, PowerType.INTERRUPTIBLE_CONSUMPTION);
      addOwnTariff(spec);
      tariffHeuristics.createTariffStateBook(spec);
      customerSubscriptions.put(spec, new LinkedHashMap<>());
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
    }
  }

  public void improveBatteryStorageTariffs(Integer timeslot)
  {
    List<TariffSpecification> BStariffs = ownTariffs.get(PowerType.BATTERY_STORAGE);
    if((BStariffs.size() == 0) || (BStariffs == null))
    {
      List<TariffSpecification> candidates = ownTariffs.get(PowerType.CONSUMPTION);
      Double newCandAvgRate = tariffHeuristics.getMostSubscribedTariffAvgRate(candidates);

      if(newCandAvgRate != null)
      {
          TariffSpecification spec = tariffHeuristics.improveProdTariffs(newCandAvgRate, PowerType.BATTERY_STORAGE);
          addOwnTariff(spec);
          tariffHeuristics.createTariffStateBook(spec);
          customerSubscriptions.put(spec, new LinkedHashMap<>());
          tariffRepo.addSpecification(spec);
          brokerContext.sendMessage(spec);
      }
    }
  }

  public void storetoMongoDB(Integer timeslot)
  {
    balancingMarketInformation = messageManager.getBalancingMarketInformation();
    marketTransactionInformation = messageManager.getMarketTransactionInformation();
    distributionInformation = messageManager.getDistributionInformation();
    cashPositionInformation = messageManager.getCashPositionInformation();
    gameInformation = messageManager.getGameInformation();
    capacityTransactionInformation = messageManager.getCapacityTransactionInformation();

    /*
    PROBLEM: With STORAGE tariffs, accounting numbers do not match
             Without STORAGE tariff, all numbers match exactly   (NEED TO BE SOLVED)
    */

    Double tariffRevenue = tariffMarketInformation.getTariffRevenue(timeslot);

    Double wholesaleCost = marketTransactionInformation.getBrokerWholesaleCost(timeslot);
    Double capacityTransactionPenalty = capacityTransactionInformation.getCapacityTransactionCharge(timeslot);

    Double balancingCost = 0.0;
    Double distributionCost = 0.0;

    try
    {
      distributionCost = distributionInformation.getDistributionTransaction(timeslot).getValue();
      balancingCost = balancingMarketInformation.getBalancingTransaction(timeslot).getValue();
    }
    catch(Exception e){}

    Double cashBalance = cashPositionInformation.getCashPosition(timeslot);
    Double bankInterest = cashPositionInformation.getBankInterest(timeslot);

    Double profit = tariffRevenue + wholesaleCost + balancingCost + distributionCost + capacityTransactionPenalty + bankInterest;

    Double incomeToCostRatio = -1.0;

    if(wholesaleCost != 0.0)
        incomeToCostRatio = Math.abs(tariffRevenue/ wholesaleCost);

    Double marketShareC = tariffMarketInformation.getMarketShareVolumeMapC(timeslot);
    Double marketShareP = tariffMarketInformation.getMarketShareVolumeMapP(timeslot);

    try
    {
      String col1 = "AccountingInformation_VV20";
      MongoCollection<Document> collection1 = mongoDatabase.getCollection(col1);

      Document document1 = new Document();

      document1.put("Game_Name", gameInformation.getName());
      document1.put("Timeslot", timeslot);
      document1.put("Income_to_Cost_Ratio", incomeToCostRatio);
      document1.put("Market_ShareC", marketShareC);
      document1.put("Market_ShareP", marketShareP);
      document1.put("Tariff_Revenue", tariffRevenue);
      document1.put("Wholesale_Cost", wholesaleCost);
      document1.put("Balancing_Cost", balancingCost);
      document1.put("Distribution_Cost", distributionCost);
      document1.put("Capacity_Transaction", capacityTransactionPenalty);
      document1.put("Profit", profit);
      document1.put("Cash_Position", cashBalance);

      collection1.insertOne(document1);
    }
    catch(Exception e){e.printStackTrace();}
  }

  // ------------- test-support methods ----------------
  double getUsageForCustomer (CustomerInfo customer,
                              TariffSpecification tariffSpec,
                              int index)
  {
    CustomerRecord record = getCustomerRecordByTariff(tariffSpec, customer);
    return record.getUsage(index);
  }

  // test-support method
  HashMap<PowerType, double[]> getRawUsageForCustomer (CustomerInfo customer)
  {
    HashMap<PowerType, double[]> result = new HashMap<>();
    for (PowerType type : customerProfiles.keySet()) {
      CustomerRecord record = customerProfiles.get(type).get(customer);
      if (record != null) {
        result.put(type, record.usage);
      }
    }
    return result;
  }

  // test-support method
  HashMap<String, Integer> getCustomerCounts()
  {
    HashMap<String, Integer> result = new HashMap<>();
    for (TariffSpecification spec : customerSubscriptions.keySet()) {
      Map<CustomerInfo, CustomerRecord> customerMap = customerSubscriptions.get(spec);
      for (CustomerRecord record : customerMap.values()) {
        result.put(record.customer.getName() + spec.getPowerType(),
                    record.subscribedPopulation);
      }
    }
    return result;
  }

  //-------------------- Customer-model recording ---------------------
  /**
   * Keeps track of customer status and usage. Usage is stored
   * per-customer-unit, but reported as the product of the per-customer
   * quantity and the subscribed population. This allows the broker to use
   * historical usage data as the subscribed population shifts.
   */
  class CustomerRecord
  {
    CustomerInfo customer;
    int subscribedPopulation = 0;
    double[] usage;
    double alpha = 0.3;
    boolean deferredActivation = false;
    double deferredUsage = 0.0;
    int savedIndex = 0;

    /**
     * Creates an empty record
     */
    CustomerRecord (CustomerInfo customer)
    {
      super();
      this.customer = customer;
      this.usage = new double[brokerContext.getUsageRecordLength()];
    }

    CustomerRecord (CustomerRecord oldRecord)
    {
      super();
      this.customer = oldRecord.customer;
      this.usage = Arrays.copyOf(oldRecord.usage, brokerContext.getUsageRecordLength());
    }

    // Returns the CustomerInfo for this record
    CustomerInfo getCustomerInfo ()
    {
      return customer;
    }

    // Adds new individuals to the count
    void signup (int population)
    {
      subscribedPopulation = Math.min(customer.getPopulation(), subscribedPopulation + population);
    }

    // Removes individuals from the count
    void withdraw (int population)
    {
      subscribedPopulation -= population;
    }

    // Sets up deferred activation
    void setDeferredActivation ()
    {
      deferredActivation = true;
      notifyOnActivation.add(this);
    }

    // Customer produces or consumes power. We assume the kwh value is negative
    // for production, positive for consumption
    void produceConsume (double kwh, Instant when)
    {
      int index = getIndex(when);
      produceConsume(kwh, index);
    }

    // stores profile data at the given index
    void produceConsume (double kwh, int rawIndex)
    {
      if (deferredActivation) {
        deferredUsage += kwh;
        savedIndex = rawIndex;
      }
      else
        localProduceConsume(kwh, rawIndex);
    }

    // processes deferred recording to accomodate regulation
    void activate ()
    {
      //PortfolioManagerService.log.info("activate {}", customer.getName());
      localProduceConsume(deferredUsage, savedIndex);
      deferredUsage = 0.0;
    }

    private void localProduceConsume (double kwh, int rawIndex)
    {
      int index = getIndex(rawIndex);
      double kwhPerCustomer = 0.0;
      if (subscribedPopulation > 0) {
        kwhPerCustomer = kwh / (double)subscribedPopulation;
      }
      double oldUsage = usage[index];
      if (oldUsage == 0.0) {
        // assume this is the first time
        usage[index] = kwhPerCustomer;
      }
      else {
        // exponential smoothing
        usage[index] = alpha * kwhPerCustomer + (1.0 - alpha) * oldUsage;
      }
      //PortfolioManagerService.log.debug("consume {} at {}, customer {}", kwh, index, customer.getName());
    }

    double getUsage (int index)
    {
      if (index < 0) {
        PortfolioManagerService.log.warn("usage requested for negative index " + index);
        index = 0;
      }
      return (usage[getIndex(index)] * (double)subscribedPopulation);
    }

    // we assume here that timeslot index always matches the number of
    // timeslots that have passed since the beginning of the simulation.
    int getIndex (Instant when)
    {
      int result = (int)((when.getMillis() - timeService.getBase()) /
                         (Competition.currentCompetition().getTimeslotDuration()));
      return result;
    }

    private int getIndex (int rawIndex)
    {
      return rawIndex % usage.length;
    }
  }
}
