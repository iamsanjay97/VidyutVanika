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

import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Vector;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Random;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.apache.logging.log4j.Logger;
import org.apache.activemq.command.IntegerResponse;
import org.apache.activemq.filter.FunctionCallExpression.invalidFunctionExpressionException;
import org.apache.commons.io.output.ProxyOutputStream;
import org.apache.commons.pool2.proxy.ProxiedKeyedObjectPool;
import org.apache.logging.log4j.LogManager;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.powertac.common.*;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.BalancingControlEvent;
import org.powertac.common.msg.BalanceReport;
import org.powertac.common.msg.BalancingOrder;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.EconomicControlEvent;
import org.powertac.common.msg.TariffRevoke;
import org.powertac.common.msg.TariffStatus;
import org.powertac.common.msg.TimeslotComplete;
import org.powertac.common.msg.SimEnd;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javafx.util.Pair;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;

import org.powertac.samplebroker.messages.BalancingMarketInformation;
import org.powertac.samplebroker.util.Helper;
import org.powertac.samplebroker.tariffmarket.TariffHeuristics;
import com.mongodb.client.MongoDatabase;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoCollection;
import org.bson.Document; 

import org.powertac.samplebroker.messages.BalancingMarketInformation;
import org.powertac.samplebroker.messages.WeatherInformation;
import org.powertac.samplebroker.util.Helper;
import org.powertac.samplebroker.util.JSON_API;
import org.powertac.samplebroker.information.TariffMarketInformation;
import org.powertac.samplebroker.information.UsageRecord;
import org.powertac.samplebroker.information.CustomerUsageInformation;
import org.powertac.samplebroker.information.CustomerSubscriptionInformation;
import org.powertac.samplebroker.information.WholesaleMarketInformation;
import org.powertac.samplebroker.messages.ClearedTradeInformation;
import org.powertac.samplebroker.messages.CapacityTransactionInformation;
import org.powertac.samplebroker.messages.MarketTransactionInformation;
import org.powertac.samplebroker.messages.DistributionInformation;
import org.powertac.samplebroker.messages.OrderBookInformation;
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
  private TimeService timeService;

  // ---- Portfolio records -----
  // Customer records indexed by power type and by tariff. Note that the
  // CustomerRecord instances are NOT shared between these structures, because
  // we need to keep track of subscriptions by tariff.
  private Map<PowerType, Map<CustomerInfo, CustomerRecord>> customerProfiles;
  private Map<TariffSpecification, Map<CustomerInfo, CustomerRecord>> customerSubscriptions;
  private Map<PowerType, List<TariffSpecification>> competingTariffs;
  private Map<PowerType, List<TariffSpecification>> ownTariffs;
  private Map<Integer, Double> predConsumptionMap;
  private Map<Integer, Double> predProductionMap;
  private Integer[] predictedPeaksMap;

  // Keep track of a benchmark price to allow for comparisons between
  // tariff evaluations
  private double benchmarkPrice = 0.0;

  // These customer records need to be notified on activation
  private List<CustomerRecord> notifyOnActivation = new ArrayList<>();

  // Configurable parameters for tariff composition
  // Override defaults in src/main/resources/config/broker.config
  // or in top-level config file
  @ConfigurableValue(valueType = "Double", description = "target profit margin")
  private double defaultMargin = 0.5;

  @ConfigurableValue(valueType = "Double", description = "Fixed cost/kWh")
  private double fixedPerKwh = -0.06;

  @ConfigurableValue(valueType = "Double", description = "Default daily meter charge")
  private double defaultPeriodicPayment = -1.0;

  @ConfigurableValue(valueType = "Double", description = "Fixed cost/kWh for distribution")
  private double distributionCharge = -0.02;

  @ConfigurableValue(valueType = "Double", description = "Initial production tariff rate")
  private double prodRate = 0.0136;

  // *********** Beolw are Configurable Parameters for Tariff Heuristic **************
  
  @ConfigurableValue(valueType = "Integer", description = "periodic update interval")
  private Integer UPDATE_INTERVAL = 84;

  @ConfigurableValue(valueType = "Integer", description = "modify update interval after this timeslot")
  private Integer SECOND_LAG = 1032;         // 360 + 4 weeks (672) = 1032

  @ConfigurableValue(valueType = "Double", description = "Discount rate to compare current tariff revenue with previous tariff")
  private Double DISCOUNT = 0.9;       
  
  @ConfigurableValue(valueType = "Boolean", description = "MongoDB Storage Flag")
  private Boolean MONGO_FLAG = true;

  // *********** Beolw are Configurable Parameters for Peak Detection ************************************************************/

  @ConfigurableValue(valueType = "Integer", description = "Number of timeslots before the predicted timeslot")
  private Integer skip = 4;

  @ConfigurableValue(valueType = "Integer", description = "Number of timeslots to jump over between two NDP calls")
  private Integer jump = 4;

  @ConfigurableValue(valueType = "Double", description = "Exponential Smoothing parameter")
  private Double alpha = 0.4;

  @ConfigurableValue(valueType = "Double", description = "Resistace that needs to be crossed to classify any timeslot as peak")
  private Double resistance = 0.80;

  @ConfigurableValue(valueType = "Double", description = "Upper bound of the net demand that needs to be crossed to consider any timeslot as possible peak")
  private Double tolerance = 1.3;

  // *******************************************************************************

  int OFFSET = 24;

  int alphaLength = OFFSET / jump;

  int[] blocks = {5, 9, 10, 16, 17, 22, 23, 5};

  double[] alphaList = new double[alphaLength];

  /*********************************************************************************************************************** */

  double dfrate=-0.5;
  double dfrateProd=-0.5;

  CustomerUsageInformation custUsageInfo = null;

  private BalancingMarketInformation balancingMarketInformation;
  private CashPositionInformation cashPositionInformation;
  private WeatherInformation weatherInformation;
  private WholesaleMarketInformation wholesaleMarketInformation;
  private ClearedTradeInformation clearedTradeInformation;
  private OrderBookInformation orderBookInformation;
  private GameInformation gameInformation;
  private MarketTransactionInformation marketTransactionInformation;
  private TariffMarketInformation tariffMarketInformation;
  private DistributionInformation distributionInformation;
  private CapacityTransactionInformation capacityTransactionInformation;
  private TariffHeuristics tariffHeuristics;

  //MongoDB client and Database
  MongoClient mongoClient;

  //MongoDB database
  DB mongoDatabase;

  String dbname; 

  FileWriter accountingInformation;

  Random rand;

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
    predConsumptionMap = new LinkedHashMap<>();
    predProductionMap = new LinkedHashMap<>();
    predictedPeaksMap = new Integer[2500];
    Arrays.fill(predictedPeaksMap, 0);

    balancingMarketInformation = messageManager.getBalancingMarketInformation();
    weatherInformation = messageManager.getWeatherInformation();
    wholesaleMarketInformation = messageManager.getWholesaleMarketInformation();
    clearedTradeInformation = messageManager.getClearTradeInformation();
    orderBookInformation = messageManager.getOrderBookInformation();
    gameInformation = messageManager.getGameInformation();
    cashPositionInformation = messageManager.getCashPositionInformation();

    custUsageInfo = messageManager.getCustomerUsageInformation();
    dfrate=0.0;
    dfrateProd = 0.0;

    tariffMarketInformation = new TariffMarketInformation();

    rand = new Random();

    /********************************************************************************* */
    // Generate the list for Exponential Smoothing weights 
    // alpha, alpha*(1-alpha), alpha*(1-alpha)^2, ... (in decreasing order)
    alphaList[0] = alpha;
    for(int i = 1; i < alphaLength; i++)
      alphaList[i] = alphaList[i-1]*(1 - alpha);  
    /********************************************************************************* */

    if(MONGO_FLAG)
    {
      dbname = "PowerTAC2021_Trials2";
      try
      {
          mongoClient = new MongoClient("localhost", 27017);
          mongoDatabase = mongoClient.getDB(dbname);
      }
      catch(Exception e)
      {
          log.warn("Mongo DB connection Exception " + e.toString());
      }
      log.info(" Connected to Database " + dbname + " -- Broker Initialize");
      System.out.println("Connected to Database " + dbname + " from Initialize in PortfolioManager");
    }
    else
    {
      try
      {
          accountingInformation = new FileWriter("AccountingInformationFile.csv", true);
          // accountingInformation.write("Game, Timeslot, IncomeToCost, MarketShare, TariffRevenue, WholesaleCost, BalancingCost, DistributionCost, Capacity Transaction, CashPosition");
      }
      catch(Exception e) {e.printStackTrace();}
    }

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

  public double[] getHourlyTariff(TariffSpecification specification)
  {
    // Generate tariff series
    List<Rate> rates = specification.getRates();

    double arr[] = new double[24];
    int flag1 = 0;

    for(Rate rate : rates)
    {
      int begin = rate.getDailyBegin();
      int end = rate.getDailyEnd() + 1;

      if(begin != -1)
      {
        flag1 = 1;
        while(begin != end)
        {
          arr[begin] = Math.abs(rate.getMinValue());
          begin = (begin + 1) % 24;
        }
      }
    }

    if(flag1 == 0)
      Arrays.fill(arr, Math.abs(rates.get(0).getMinValue()));

    return arr;
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

  // Production is very random, focus only on Consumption
  public double[] predictNetDemandNaive(int currentTimeslot)
  {
    double netDemandPred[] = new double[24];

    Double dailyWeight = 0.50;
    Double weeklyWeight = 1 - dailyWeight;

    for(int index = 24; index >= 1; index--)
    {
      int prevDayTimeslot = currentTimeslot - index + 1;          // Daily Pattern
      int prevWeekTimeslot = currentTimeslot - (index + 144) + 1;         // Weekly Pattern

      Double totalDayConsumption = distributionInformation.getTotalConsumption(prevDayTimeslot);
      Double totalDayProduction = distributionInformation.getTotalProduction(prevDayTimeslot);

      Double totalWeekConsumption = distributionInformation.getTotalConsumption(prevWeekTimeslot);
      Double totalWeekProduction = distributionInformation.getTotalProduction(prevWeekTimeslot);

      Double totalConsumption = dailyWeight*totalDayConsumption + weeklyWeight*totalWeekConsumption;
      Double totalProduction = dailyWeight*totalDayProduction + weeklyWeight*totalWeekProduction;

      predConsumptionMap.put(currentTimeslot+24-index+1, totalConsumption);
      predProductionMap.put(currentTimeslot+24-index+1, totalProduction);

      // netDemandPred[24-index] = totalConsumption - totalProduction;
      netDemandPred[24-index] = totalConsumption;
    }

    return netDemandPred;
  }

  public double[] predictNetDemand(int currentTimeslot)
  {
    double netDemandPred[] = new double[24];

    JSONObject[] object = new JSONObject[168];
    DistributionInformation distributionInformation = messageManager.getDistributionInformation();

    for(int index = 168; index >= 1; index--)
    {
      int prevTimeslot = currentTimeslot - index + 1;

      Double temperature = weatherInformation.getWeatherReport(prevTimeslot).getTemperature();
      Double windSpeed = weatherInformation.getWeatherReport(prevTimeslot).getWindSpeed();
      Double total_consumption = distributionInformation.getTotalConsumption(prevTimeslot);
      Double total_production = distributionInformation.getTotalProduction(prevTimeslot);

      JSONObject obj = new JSONObject();

      obj.put("Temperature", temperature);
      obj.put("Wind_Speed", windSpeed);
      obj.put("Total_Consumption", total_consumption);
      obj.put("Total_Production", total_production);

      object[168-index] = obj;
    }

    String responseString = JSON_API.communicateWithPython("http://localhost:5000/NDPredictionLSTM", object);

    Object obj = JSONValue.parse(responseString);  
    JSONObject jsonObject = (JSONObject) obj;  

    String consResponse = (String)jsonObject.get("consumption");
    String prodResponse = (String)jsonObject.get("production");

    ArrayList<Double> consPredictions = JSON_API.decodeJSON(consResponse);
    ArrayList<Double> prodPredictions = JSON_API.decodeJSON(prodResponse);

    for(int proximity = 0; proximity < 24; proximity++)
    {
      netDemandPred[proximity] = consPredictions.get(proximity) - prodPredictions.get(proximity);
      predConsumptionMap.put(currentTimeslot+proximity+1, consPredictions.get(proximity));
      predProductionMap.put(currentTimeslot+proximity+1, prodPredictions.get(proximity));
    }

    return netDemandPred;
  }

  // -------------- Message handlers -------------------
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
    System.out.println("Broker : " + spec.getBroker().getUsername() + " :: Spec : " + spec.getPowerType() + " :: " + spec.getRates());

    if((spec.getBroker().getUsername().equals("default broker")) && (spec.getPowerType()==PowerType.CONSUMPTION))
    {
        dfrate=spec.getRates().get(0).getValue();
    }
    if((spec.getBroker().getUsername().equals("default broker")) && (spec.getPowerType()==PowerType.PRODUCTION))
    {
        dfrateProd=spec.getRates().get(0).getValue();
    }

    Broker theBroker = spec.getBroker();
    Integer currentTimeslot = timeslotRepo.currentTimeslot().getSerialNumber();

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
       int dayOfWeek = 0;
       int day = 0;
       Double usagePerPopulation = 0.0;
       Double chargePerUnit = 0.0;

       String customerName = ttx.getCustomerInfo().getName();
       PowerType pType = ttx.getCustomerInfo().getPowerType();
       Integer subscribedPopulation = ttx.getCustomerCount();
       Integer timeslot = ttx.getPostedTimeslotIndex();
       hour = timeslotRepo.findBySerialNumber(timeslot).getStartInstant().toDateTime().getHourOfDay();
       day = timeslotRepo.findBySerialNumber(timeslot).getStartInstant().toDateTime().getDayOfWeek();
       int blockNumber = Helper.getBlockNumber(hour, blocks);

       if(ttx.getKWh() != 0.0) {
         usagePerPopulation = Math.abs(ttx.getKWh() / subscribedPopulation);
         chargePerUnit =  Math.abs(ttx.getCharge() / ttx.getKWh());
       }
       UsageRecord usageRecord = new UsageRecord(hour, day, blockNumber, subscribedPopulation, chargePerUnit, usagePerPopulation);

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

  public synchronized void handleMessage (SimEnd se)
  {
    if(!MONGO_FLAG)
    {
      try
      {
          accountingInformation.close();
      }
      catch(Exception e) {}
    }
  }

  @Override // from Activatable
  public synchronized void activate (int timeslotIndex)
  {
    if(timeslotIndex != 360)
      updateCurrentTariffStats(timeslotIndex);

    /**
     * Heuristic Based Tariff Strategy
     */
    if (customerSubscriptions.size() == 0)       // The first tariff from our broker
    {
      tariffHeuristics = new TariffHeuristics(this.brokerContext, gameInformation.getNumberOfBroker());
      createInitialTariffs();
    }      
    else if((timeslotIndex-360) % UPDATE_INTERVAL == 0)
    {
      improveTariffs(timeslotIndex);
    }

    if((timeslotIndex != 360) && ((timeslotIndex-360) % 48 == 0))
        updateProductionTariff();

    updateMarketShare(timeslotIndex);       // Update broker's market share (volume)

    if(timeslotIndex != 360)
      storetoMongoDB(timeslotIndex);

    if(timeslotIndex%4 == 0) 
    {
      double[] predictions = predictNetDemandNaive(timeslotIndex);
      detectPeaks(timeslotIndex, predictions);
    }

    updatePeakPredictedInMongoDB(timeslotIndex);

    // Gap of skip-1 timeslots, after which that timeslot prediction freezes
    if(predictedPeaksMap[timeslotIndex+skip-1] == 1)
      publishECEvent(timeslotIndex+skip-1);

    for (CustomerRecord record: notifyOnActivation)
      record.activate();
  }

  public void updateCurrentTariffStats(Integer timeslot)
  {
    marketTransactionInformation = messageManager.getMarketTransactionInformation();
    distributionInformation = messageManager.getDistributionInformation();

    TariffSpecification currentSpec = tariffHeuristics.getCurrentTariff();

    double tariffRevenue = tariffMarketInformation.getTariffRevenue(timeslot, currentSpec);
    double wholesaleCost = marketTransactionInformation.getBrokerWholesaleCost(timeslot);

    double brokerNetDemand = Math.abs(tariffMarketInformation.getTariffUsage(timeslot));
    double netDemand = Math.abs(distributionInformation.getTotalConsumption(timeslot) - distributionInformation.getTotalProduction(timeslot));
    double percBrokerDemand = brokerNetDemand / netDemand;

    System.out.println("\nTariff Revenue: " + tariffRevenue + " :: Wholesale Cost: " + wholesaleCost + " :: Broker Net Demand: " + brokerNetDemand + " :: Net Demand: " + netDemand);

    tariffHeuristics.updateTariffStats(tariffRevenue, wholesaleCost, netDemand, percBrokerDemand);

    double threshold = messageManager.calculateThreshold();
    Double tariffOverallRevenue = tariffHeuristics.getTariffRevenuePerTimeslot(threshold, false);
    System.out.println("\nCurrent Tariff Index:" + tariffHeuristics.getCurrentTariffIndex());
  }

  private void createInitialTariffs ()
  {
    TariffSpecification spec = tariffHeuristics.getInitialTariff();

    addOwnTariff(spec);
    customerSubscriptions.put(spec, new LinkedHashMap<>());
    tariffRepo.addSpecification(spec);
    brokerContext.sendMessage(spec);

    // Production Tariff similar to EWIIS 
    TariffSpecification prodSpec = new TariffSpecification(brokerContext.getBroker(), PowerType.PRODUCTION);
    Rate prodRates = new Rate().withValue(prodRate);
    prodSpec.addRate(prodRates);

    addOwnTariff(prodSpec);
    customerSubscriptions.put(prodSpec, new LinkedHashMap<>());
    tariffRepo.addSpecification(prodSpec);
    brokerContext.sendMessage(prodSpec);

    // BatteryStorage Tariff similar to Crocodile and EWIIS 
    TariffSpecification storSpec = new TariffSpecification(brokerContext.getBroker(), PowerType.BATTERY_STORAGE)
    .withMinDuration(302400000).withSignupPayment(10.1).withEarlyWithdrawPayment(-19);
    Rate storRates = new Rate().withValue(-0.1391);
    RegulationRate storRR = new RegulationRate().withUpRegulationPayment(0.25).withDownRegulationPayment(-0.0643);
    storSpec.addRate(storRates);
    storSpec.addRate(storRR);

    addOwnTariff(storSpec);
    customerSubscriptions.put(storSpec, new LinkedHashMap<>());
    tariffRepo.addSpecification(storSpec);
    brokerContext.sendMessage(storSpec);
  }

  private void improveTariffs(Integer timeslot)
  {
    TariffSpecification oldc = null;
    List<TariffSpecification> candidates = ownTariffs.get(PowerType.CONSUMPTION);

    if (null == candidates || 0 == candidates.size())
      log.error("No tariffs found for broker");
    else 
    {
      oldc = candidates.get(0);

      if (null == oldc) 
      {
        log.warn("No CONSUMPTION tariffs found");
      }
      else 
      {
        double threshold = messageManager.calculateThreshold();
        Double tariffOverallRevenue = tariffHeuristics.getTariffRevenuePerTimeslot(threshold, true);     // Pass 'true' if we are changing the tariff to save the revenue of the existing tariff
        Double curTariffOverallRevenue = tariffHeuristics.getAvgTariffRevenuePerTimeslotOfCurrentTariff();
        Double prevTariffOverallRevenue = tariffHeuristics.getAvgTariffRevenuePerTimeslotOfPrevTariff();

        TariffSpecification spec;

        // Condition check to decide new tariff
        if(timeslot == (360 + UPDATE_INTERVAL))   // the very first update
        {
          if(curTariffOverallRevenue > 1500.0)
            spec = tariffHeuristics.getNextTariff();
          else
            spec = tariffHeuristics.getPrevTariff();
        }
        else if(timeslot >= SECOND_LAG)           // stop exploration and publish best tariff
        {
          spec = tariffHeuristics.getTheBestTariff();

          if(spec == null)               // Current tariff is the best tariff, don't change anything
            return;

          // if(timeslot == SECOND_LAG)
          //   UPDATE_INTERVAL *= 2;
        }
        else if((curTariffOverallRevenue > 0.0) && (prevTariffOverallRevenue != 0.0 || curTariffOverallRevenue > 3500.0) && (curTariffOverallRevenue > (DISCOUNT*prevTariffOverallRevenue)))
        {
          spec = tariffHeuristics.getNextTariff();

          if(spec == null)               // Current tariff is the best (costliest) available, don't change anything
            return;
        }
        else
        {
          spec = tariffHeuristics.getPrevTariff();
        }

        addOwnTariff(spec);
        customerSubscriptions.put(spec, new LinkedHashMap<>());
        tariffRepo.addSpecification(spec);
        brokerContext.sendMessage(spec);

        // revoke the old one
        removeOwnTariff(oldc);
        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), oldc);
        tariffRepo.removeSpecification(revoke.getId());
        brokerContext.sendMessage(revoke);
      }
    }
  }

  public void updateProductionTariff()
  {
    // Update PRODUCTION Tariff
    wholesaleMarketInformation = messageManager.getWholesaleMarketInformation();

    List<TariffSpecification> opponents1 = getCompetingTariffs(PowerType.PRODUCTION);
    List<TariffSpecification> opponents2 = getCompetingTariffs(PowerType.WIND_PRODUCTION);
    List<TariffSpecification> opponents3 = getCompetingTariffs(PowerType.SOLAR_PRODUCTION);

    // double meanMarketPrice = wholesaleMarketInformation.getMeanMarketPrice() / 1000.0;   // to convert to per KWh
    double meanMarketPrice = 0.035;

    double highest = 0.0;

    for(TariffSpecification item: opponents1)
    {
      if(item.getRates().get(0).isFixed())
        highest = Math.max(highest, item.getRates().get(0).getValue());
    }

    for(TariffSpecification item: opponents2)
    {
      if(item.getRates().get(0).isFixed())
        highest = Math.max(highest, item.getRates().get(0).getValue());
    }

    for(TariffSpecification item: opponents3)
    {
      if(item.getRates().get(0).isFixed())
        highest = Math.max(highest, item.getRates().get(0).getValue());
    }

    if((highest > prodRate) && (highest < meanMarketPrice))
      prodRate = highest + 0.0005;
    else if((highest > prodRate) && (highest > meanMarketPrice))
      prodRate = meanMarketPrice*0.7;
    else 
      return;

    TariffSpecification prodSpec = new TariffSpecification(brokerContext.getBroker(), PowerType.PRODUCTION);
    Rate prodRates = new Rate().withValue(prodRate);
    prodSpec.addRate(prodRates);

    addOwnTariff(prodSpec);
    customerSubscriptions.put(prodSpec, new LinkedHashMap<>());
    tariffRepo.addSpecification(prodSpec);
    brokerContext.sendMessage(prodSpec);
  }
  
  public void updateMarketShare(Integer timeslot)
  {
    distributionInformation = messageManager.getDistributionInformation();

    Pair<Double, Double> item = tariffMarketInformation.getConsProdTariffUsage(timeslot);
    Double brokerNetUsageC = Math.abs(item.getKey());
    Double brokerNetUsageP = Math.abs(item.getValue());

    Double marketShareC = -1.0;
    Double marketShareP = -1.0;

    if(distributionInformation.getTotalConsumption(timeslot) != 0.0)
      marketShareC = Math.min(1.0, Math.abs(brokerNetUsageC / distributionInformation.getTotalConsumption(timeslot)));

    if(distributionInformation.getTotalProduction(timeslot) != 0.0)
      marketShareP = Math.min(1.0, Math.abs(brokerNetUsageP / distributionInformation.getTotalProduction(timeslot)));

    tariffMarketInformation.setMarketShareVolumeMapC(timeslot, marketShareC);
    tariffMarketInformation.setMarketShareVolumeMapP(timeslot, marketShareP);
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
      if(MONGO_FLAG)
      {
        String col1 = "AccountingInformation_VV20";
        DBCollection collection1 = mongoDatabase.getCollection(col1);

        DBObject document1 = new BasicDBObject();

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

        collection1.insert(document1);
      }
      else
      {
        String out = gameInformation.getName() + ", " + timeslot + ", " + incomeToCostRatio + ", " + marketShareC + ", " + marketShareP + ", " + tariffRevenue + ", " + 
                     wholesaleCost + ", " + balancingCost + ", " + distributionCost + ", " + capacityTransactionPenalty + ", " + profit + ", " + cashBalance + "\n";
        // System.out.println(out);
        accountingInformation.write(out);
      }
    }
    catch(Exception e){e.printStackTrace();}
  }

  // Capacity Transaction Peak Prediction
  public void detectPeaks(Integer timeslot, double[] predictions)
  {
    distributionInformation = messageManager.getDistributionInformation();
    capacityTransactionInformation = messageManager.getCapacityTransactionInformation();

    /**
     * upperBound should be higher than threshold of capacity transaction (to reduce false positives, may still miss true positives)
     * setting tolerance (1.5) higher than gamma (1.22) would achieve that  
     */
    Double upperBound = messageManager.calculateTolerance(timeslot, tolerance);

    /**
     * starting from proximity = 3 (skipping 0,1 & 2), to give customers enough time (they need atleast 3 timeslots)
     * to react to any broker's action, which means we are not using latest 3 predictions for a given timeslot
     */
    for(int proximity = (skip-1); proximity < 24; proximity++)
    {
      Integer futureTimeslot = timeslot + proximity + 1;
      double predNetDemand = predictions[proximity];
      // System.out.println("Future Timeslot: " + futureTimeslot + " :: Prediction: " + predNetDemand + " :: Before: " + capacityTransactionInformation.getCapacityTransactionPrediction(futureTimeslot));

      if(predNetDemand > upperBound)
      {
        Double weight = alphaList[((proximity + 1) / jump) - 1];
        capacityTransactionInformation.setCapacityTransactionPrediction(futureTimeslot, weight);
      }
      // System.out.println("Future Timeslot: " + futureTimeslot + " :: Before: " + capacityTransactionInformation.getCapacityTransactionPrediction(futureTimeslot));
    }
  }

  public void updatePeakPredictedInMongoDB(Integer timeslot)
  {
    /** 
     * To Detect Peaks 
     * If capacityTransactionInformation.getCapacityTransactionPrediction(timeslot+4) > resistance, then classify it as a peak
     * and take appropriate action to mitigate capacity transaction penalty.
     * Also peaks occur in group, so classify +1/-1 timeslots as peaks as well.
     */
    if(capacityTransactionInformation.getCapacityTransactionPrediction(timeslot+skip) > resistance)
    {
      predictedPeaksMap[timeslot+skip-1] = 1;
      predictedPeaksMap[timeslot+skip] = 1;
      predictedPeaksMap[timeslot+skip+1] = 1;
    }

    try
    {
      String col = "Predicted_Peaks";
      DBCollection collection = mongoDatabase.getCollection(col);

      DBObject document = new BasicDBObject();

      document.put("Game_Name", gameInformation.getName());
      document.put("Timeslot", timeslot+skip-1);          // Gap of skip-1 timeslots, after which that timeslot prediction freezes, so safe to write to mongodb
      document.put("Prediction", predictedPeaksMap[timeslot+skip-1]);
      document.put("Weight", capacityTransactionInformation.getCapacityTransactionPrediction(timeslot+skip-1));

      collection.insert(document);
    }
    catch(Exception e){e.printStackTrace();}

    try
    {
      String col = "Predicted_demands";
      DBCollection collection = mongoDatabase.getCollection(col);

      DBObject document = new BasicDBObject();

      document.put("Game_Name", gameInformation.getName());
      document.put("Timeslot", timeslot);

      if(predConsumptionMap.get(timeslot) != null)
        document.put("Predicted Consumption", predConsumptionMap.get(timeslot));
      else
        document.put("Predicted Consumption", -1);
      
      if(predProductionMap.get(timeslot) != null)
        document.put("Predicted Production", predProductionMap.get(timeslot));
      else
        document.put("Predicted Production", -1);

      collection.insert(document);
    }
    catch(Exception e){e.printStackTrace();}
  }

  public void publishECEvent(Integer timeslot)
  {
    // List<TariffSpecification> candidates = tariffRepo.findTariffSpecificationsByPowerType(PowerType.INTERRUPTIBLE_CONSUMPTION);
    // for (TariffSpecification spec: candidates) 
    // {
    //   EconomicControlEvent ece = new EconomicControlEvent(spec, 0.25, timeslot);
    //   brokerContext.sendMessage(ece);
    // }

    // Use storage customers to supply energy during peaks, threfore reducing capacity transaction
    List<TariffSpecification> candidateStorage = tariffRepo.findTariffSpecificationsByPowerType(PowerType.BATTERY_STORAGE);
    for (TariffSpecification spec: candidateStorage) 
    {
      EconomicControlEvent ece = new EconomicControlEvent(spec, 1.25, timeslot);
      brokerContext.sendMessage(ece);
    }
  }

  // Creates initial tariffs for the main power types. These are simple
  // fixed-rate two-part tariffs that give the broker a fixed margin.
  /* private void createInitialTariffsOrg ()
  {
    // remember that market prices are per mwh, but tariffs are by kwh
    double marketPrice = marketManager.getMeanMarketPrice() / 1000.0;
    // for each power type representing a customer population,
    // create a tariff that's better than what's available
    for (PowerType pt : customerProfiles.keySet()) {
      if(!pt.isStorage()){
      // we'll just do fixed-rate tariffs for now
      benchmarkPrice = ((marketPrice + fixedPerKwh) * (1.0 + defaultMargin));
      double rateValue = benchmarkPrice;
      double periodicValue = defaultPeriodicPayment;
      if (pt.isProduction()) {
        rateValue = -2.0 * marketPrice;
        periodicValue /= 2.0;
      }
      if (pt.isStorage()) {
       rateValue *= 0.9; // Magic number
       periodicValue = 0.0;
      }
      if (pt.isInterruptible()) {
        rateValue *= 0.7; // Magic number!! price break for interruptible
      }
      //log.info("rateValue = {} for pt {}", rateValue, pt);
      log.info("Tariff {}: rate={}, periodic={}", pt, rateValue, periodicValue);
      TariffSpecification spec =
          new TariffSpecification(brokerContext.getBroker(), pt)
              .withPeriodicPayment(periodicValue);
      Rate rate = new Rate().withValue(rateValue);
      if (pt.isInterruptible() && !pt.isStorage()) {
        // set max curtailment
        rate.withMaxCurtailment(0.4);
      }
      if (pt.isStorage()) {
        // add a RegulationRate
        RegulationRate rr = new RegulationRate();
        rr.withUpRegulationPayment(-rateValue * 1.45)
            .withDownRegulationPayment(rateValue * 0.5); // magic numbers
        spec.addRate(rr);
      }
      spec.addRate(rate);
      customerSubscriptions.put(spec, new LinkedHashMap<>());
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
    }}
  } */

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
