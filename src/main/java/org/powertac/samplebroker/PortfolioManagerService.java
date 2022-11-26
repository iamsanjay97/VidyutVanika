package org.powertac.samplebroker;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Random;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.powertac.common.Broker;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.RegulationRate;
import org.powertac.common.RegulationRate.ResponseTime;
import org.powertac.common.Tariff;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TimeService;
import org.powertac.common.ClearedTrade;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.BalancingControlEvent;
import org.powertac.common.msg.BalanceReport;
import org.powertac.common.msg.BalancingOrder;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.EconomicControlEvent;
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

import org.powertac.samplebroker.messages.BalancingMarketInformation;
import org.powertac.samplebroker.messages.WeatherInformation;
import org.powertac.samplebroker.util.Helper;
import org.powertac.samplebroker.util.HelperForJSON;
import org.powertac.samplebroker.information.UsageRecord;
import org.powertac.samplebroker.information.CustomerUsageInformation;
import org.powertac.samplebroker.information.CustomerMigration;
import org.powertac.samplebroker.information.CustomerMigration.MigrationInfo;
import org.powertac.samplebroker.information.CustomerAndTariffInformation;
import org.powertac.samplebroker.information.CustomerSubscriptionInformation;
import org.powertac.samplebroker.information.WholesaleMarketInformation;
import org.powertac.samplebroker.messages.ClearedTradeInformation;
import org.powertac.samplebroker.messages.OrderBookInformation;
import org.powertac.samplebroker.messages.GameInformation;
import org.powertac.samplebroker.validation.*;

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

  private Map<String, LinkedList<Double>> customerSubscriptionPredictionMap;

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

  CustomerUsageInformation custUsageInfo = null;

  int[] blocks = {5, 9, 10, 16, 17, 22, 23, 5};

  int OFFSET = 24;

  double minforrand=0.0;
  double maxforrand=1.0;
  double dfrate=-0.5;
  private TariffSpecification newTariff, prevTariff;

  private CustomerMigration customerMigration;
  private CustomerAndTariffInformation customerAndTariffInformation;

  private BalancingMarketInformation balancingMarketInformation;
  private WeatherInformation weatherInformation;
  private WholesaleMarketInformation wholesaleMarketInformation;
  private ClearedTradeInformation clearedTradeInformation;
  private OrderBookInformation orderBookInformation;
  private GameInformation gameInformation;

  private CustomerUsageValidation customerUsageValidation;
  private MCPValidation mcpValidation;
  private NetImbalanceValidation netImbalanceValidation;

  private TariffSpecification ownBestConsTariff;
  private TariffSpecification competitorBestConsTariff;
  private Integer lastConsTariffPublicationTimeslot;

  //MongoDB client and Database
  MongoClient mongoClient;

  //MongoDB database
  DB mongoDatabase;

  String dbname;

  //Game Name
  String bootFile;

  Random rand;

  Double totalUsage = 0.0;

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
    notifyOnActivation.clear();

    balancingMarketInformation = messageManager.getBalancingMarketInformation();
    weatherInformation = messageManager.getWeatherInformation();
    wholesaleMarketInformation = messageManager.getWholesaleMarketInformation();
    clearedTradeInformation = messageManager.getClearTradeInformation();
    orderBookInformation = messageManager.getOrderBookInformation();
    gameInformation = messageManager.getGameInformation();

    customerUsageValidation = new CustomerUsageValidation();
    mcpValidation = new MCPValidation();
    netImbalanceValidation = new NetImbalanceValidation();

    custUsageInfo = messageManager.getCustomerUsageInformation();
    newTariff = null;
    prevTariff = null;
    dfrate=0.0;

    customerSubscriptionPredictionMap = new HashMap<>();

    customerMigration = new CustomerMigration();
    customerAndTariffInformation = new CustomerAndTariffInformation();

    ownBestConsTariff = null;
    competitorBestConsTariff = null;
    lastConsTariffPublicationTimeslot = -1;

    rand = new Random();

    /*dbname = "PowerTAC2020_Repository";

    try
    {
      File file = new File("currentBootFile.txt");
      BufferedReader br = new BufferedReader(new FileReader(file));

      bootFile = br.readLine() + "_" + Integer.toString(rand.nextInt(1000));
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
    System.out.println("Connected to Database " + dbname + " from Initialize in PortfolioManagerService");*/
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
   * Finds the list of own tariffs for the given PowerType.
   */
  List<TariffSpecification> getOwnTariffs (PowerType powerType)
  {
    List<TariffSpecification> result = ownTariffs.get(powerType);
    if (result == null) {
      result = new ArrayList<TariffSpecification>();
      ownTariffs.put(powerType, result);
    }
    return result;
  }

  /**
   * Adds a new competing tariff to the list.
   */
  private void addOwnTariff (TariffSpecification spec)
  {
    getOwnTariffs(spec.getPowerType()).add(spec);
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
        result += record.getUsage(index);
      }
    }
    return -result; // convert to needed energy account balance
  }

  // MCP Predictor
  @Override
  public double[] MCPPredictorFFN (int currentTimeslot)
  {
    double result[] = new double[24];

    ArrayList<Double> predictions;

    List<Integer> listofBiddingTimeslot = new ArrayList<>();
    List<Integer> listOfBiddingMonthOfYear = new ArrayList<>();
    List<Integer> listOfBiddingDayOfWeek = new ArrayList<>();
    List<Integer> listOfBiddingDayOfMonth = new ArrayList<>();
    List<Integer> listOfBiddingHourOfDay = new ArrayList<>();

    List<Integer> listofExecutionTimeslot = new ArrayList<>();
    List<Integer> listOfExecutionMonthOfYear = new ArrayList<>();
    List<Integer> listOfExecutionDayOfWeek = new ArrayList<>();
    List<Integer> listOfExecutionDayOfMonth = new ArrayList<>();
    List<Integer> listOfExecutionHourOfDay = new ArrayList<>();

    List<Double> listOfTemperature = new ArrayList<>();
    List<Double> listOfCloudCover = new ArrayList<>();
    List<Double> listOfWindSpeed = new ArrayList<>();

    List<Double> listOfUnclearedAsk = new ArrayList<>();
    List<Double> listOfUnclearedBid = new ArrayList<>();
    List<Double> listofMCP = new ArrayList<>();

    List<Double> listOfUnclearedAsk1 = new ArrayList<>();
    List<Double> listOfUnclearedBid1 = new ArrayList<>();
    List<Double> listofMCP1 = new ArrayList<>();

    for(int proximity = 1; proximity <= 24; proximity++)
    {
      int futureTimeslot = currentTimeslot + proximity;
      DateTime currentDate = timeslotRepo.getDateTimeForIndex(currentTimeslot);
      DateTime futureDate = timeslotRepo.getDateTimeForIndex(futureTimeslot);

      Integer biddingMonthOfYear = currentDate.getMonthOfYear();
      Integer biddingDayOfWeek = currentDate.getDayOfWeek();
      Integer biddingDayOfMonth = currentDate.getDayOfMonth();
      Integer biddingHourOfDay = currentDate.getHourOfDay();

      Integer executionMonthOfYear = futureDate.getMonthOfYear();
      Integer executionDayOfWeek = futureDate.getDayOfWeek();
      Integer executionDayOfMonth = futureDate.getDayOfMonth();
      Integer executionHourOfDay = futureDate.getHourOfDay();

      Double temperature = weatherInformation.getWeatherForecast(currentTimeslot-1).getPredictions().get(proximity-1).getTemperature();
      Double cloudCover = weatherInformation.getWeatherForecast(currentTimeslot-1).getPredictions().get(proximity-1).getCloudCover();
      Double windSpeed = weatherInformation.getWeatherForecast(currentTimeslot-1).getPredictions().get(proximity-1).getWindSpeed();

      Double unclearedAsk = orderBookInformation.getFirstUnclearedAsk(currentTimeslot, proximity);
      Double unclearedBid = orderBookInformation.getFirstUnclearedBid(currentTimeslot, proximity);
      Double MCP = clearedTradeInformation.getLastMCPForProximity(currentTimeslot, proximity);

      Double unclearedAsk1 = orderBookInformation.getFirstUnclearedAskPrev(futureTimeslot, currentTimeslot);
      Double unclearedBid1 = orderBookInformation.getFirstUnclearedBidPrev(futureTimeslot, currentTimeslot);
      Double MCP1 = clearedTradeInformation.getLastMCPForProximityPrev(futureTimeslot, currentTimeslot);

      listofBiddingTimeslot.add(currentTimeslot-360);
      listOfBiddingMonthOfYear.add(biddingMonthOfYear);
      listOfBiddingDayOfWeek.add(biddingDayOfWeek);
      listOfBiddingDayOfMonth.add(biddingDayOfMonth);
      listOfBiddingHourOfDay.add(biddingHourOfDay);

      listofExecutionTimeslot.add(futureTimeslot-362);
      listOfExecutionMonthOfYear.add(executionMonthOfYear);
      listOfExecutionDayOfWeek.add(executionDayOfWeek);
      listOfExecutionDayOfMonth.add(executionDayOfMonth);
      listOfExecutionHourOfDay.add(executionHourOfDay);

      listOfTemperature.add(temperature);
      listOfCloudCover.add(cloudCover);
      listOfWindSpeed.add(windSpeed);

      listOfUnclearedAsk.add(unclearedAsk);
      listOfUnclearedBid.add(unclearedBid);
      listofMCP.add(MCP);

      listOfUnclearedAsk1.add(unclearedAsk);
      listOfUnclearedBid1.add(unclearedBid);
      listofMCP1.add(MCP);
    }

    try
    {
      predictions = HelperForJSON.communicateMCP("http://localhost:5000/MCPPredictionFFN",
                                      listofBiddingTimeslot, listOfBiddingMonthOfYear, listOfBiddingDayOfWeek, listOfBiddingDayOfMonth, listOfBiddingHourOfDay,
                                      listofExecutionTimeslot, listOfExecutionMonthOfYear, listOfExecutionDayOfWeek, listOfExecutionDayOfMonth, listOfExecutionHourOfDay,
                                      listOfTemperature, listOfCloudCover, listOfWindSpeed, listOfUnclearedAsk, listOfUnclearedBid, listofMCP, listOfUnclearedAsk1, listOfUnclearedBid1, listofMCP1);

      for(int proximity = 1; proximity <= 24; proximity++)
      {
        mcpValidation.updateMCPMap(currentTimeslot, (currentTimeslot+proximity), Math.abs(predictions.get(proximity-1)) ,false);
        result[proximity-1] = predictions.get(proximity-1);
      }
    }
    catch(Exception e)
    {
      System.out.println("\n\nError in Avg MCP Predictor\n\n");
    }

    return result;
  }

  // MCP Predictor
  @Override
  public double[] NetImbalancePredictorFFN (int currentTimeslot)
  {
    double result[] = new double[24];

    ArrayList<Double> predictions;

    List<Integer> listofBiddingTimeslot = new ArrayList<>();
    List<Integer> listOfBiddingMonthOfYear = new ArrayList<>();
    List<Integer> listOfBiddingDayOfWeek = new ArrayList<>();
    List<Integer> listOfBiddingDayOfMonth = new ArrayList<>();
    List<Integer> listOfBiddingHourOfDay = new ArrayList<>();

    List<Integer> listofExecutionTimeslot = new ArrayList<>();
    List<Integer> listOfExecutionMonthOfYear = new ArrayList<>();
    List<Integer> listOfExecutionDayOfWeek = new ArrayList<>();
    List<Integer> listOfExecutionDayOfMonth = new ArrayList<>();
    List<Integer> listOfExecutionHourOfDay = new ArrayList<>();

    List<Integer> listOfNumberOfPlayers = new ArrayList<>();

    Integer numberOfPlayers = gameInformation.getBrokers().size();
    listOfNumberOfPlayers.add(numberOfPlayers-1);

    List<Double> listOfTemperature = new ArrayList<>();
    List<Double> listOfCloudCover = new ArrayList<>();
    List<Double> listOfWindSpeed = new ArrayList<>();

    for(int proximity = 1; proximity <= 24; proximity++)
    {
      int futureTimeslot = currentTimeslot + proximity;
      DateTime currentDate = timeslotRepo.getDateTimeForIndex(currentTimeslot);
      DateTime futureDate = timeslotRepo.getDateTimeForIndex(futureTimeslot);

      Integer biddingMonthOfYear = currentDate.getMonthOfYear();
      Integer biddingDayOfWeek = currentDate.getDayOfWeek();
      Integer biddingDayOfMonth = currentDate.getDayOfMonth();
      Integer biddingHourOfDay = currentDate.getHourOfDay();

      Integer executionMonthOfYear = futureDate.getMonthOfYear();
      Integer executionDayOfWeek = futureDate.getDayOfWeek();
      Integer executionDayOfMonth = futureDate.getDayOfMonth();
      Integer executionHourOfDay = futureDate.getHourOfDay();

      Double temperature = weatherInformation.getWeatherForecast(currentTimeslot-1).getPredictions().get(proximity-1).getTemperature();
      Double cloudCover = weatherInformation.getWeatherForecast(currentTimeslot-1).getPredictions().get(proximity-1).getCloudCover();
      Double windSpeed = weatherInformation.getWeatherForecast(currentTimeslot-1).getPredictions().get(proximity-1).getWindSpeed();

      listofBiddingTimeslot.add(currentTimeslot-360);
      listOfBiddingMonthOfYear.add(biddingMonthOfYear);
      listOfBiddingDayOfWeek.add(biddingDayOfWeek);
      listOfBiddingDayOfMonth.add(biddingDayOfMonth);
      listOfBiddingHourOfDay.add(biddingHourOfDay);

      listofExecutionTimeslot.add(futureTimeslot-362);
      listOfExecutionMonthOfYear.add(executionMonthOfYear);
      listOfExecutionDayOfWeek.add(executionDayOfWeek);
      listOfExecutionDayOfMonth.add(executionDayOfMonth);
      listOfExecutionHourOfDay.add(executionHourOfDay);

      listOfTemperature.add(temperature);
      listOfCloudCover.add(cloudCover);
      listOfWindSpeed.add(windSpeed);
    }

    try
    {
      predictions = HelperForJSON.communicateNIP("http://localhost:5000/NetImbalancePredictionFFN",
                                      listofBiddingTimeslot, listOfBiddingMonthOfYear, listOfBiddingDayOfWeek, listOfBiddingDayOfMonth, listOfBiddingHourOfDay,
                                      listofExecutionTimeslot, listOfExecutionMonthOfYear, listOfExecutionDayOfWeek, listOfExecutionDayOfMonth, listOfExecutionHourOfDay,
                                      listOfNumberOfPlayers, listOfTemperature, listOfCloudCover, listOfWindSpeed);

      for(int proximity = 1; proximity <= 24; proximity++)
      {
        netImbalanceValidation.updateImbalanceMap((currentTimeslot+proximity), predictions.get(proximity-1),false);
        result[proximity-1] = predictions.get(proximity-1);
      }
    }
    catch(Exception e)
    {
      System.out.println("\n\nError in Avg MCP Predictor\n\n");
    }

    return result;
  }

  // Customer Usage Predictor LSTM
  @Override
  public double[] collectUsage (int currentTimeslot, boolean flag)
  {
    List<String> listOfTargetedConsumers = Helper.getListOfTargetedConsumers();
    List<String> listOfTargetedProducers = Helper.getListOfTargetedProducers();

    double result[] = new double[24];

    List<Integer> listOfDayOfMonth = new ArrayList<>();
    List<Integer> listOfDayOfWeek = new ArrayList<>();
    List<Integer> listOfHourOfDay = new ArrayList<>();

    List<Double> listOfTemperature = new ArrayList<>();
    List<Double> listOfWindSpeed = new ArrayList<>();
    List<Double> listOfWindDirection = new ArrayList<>();
    List<Double> listOfCloudCover = new ArrayList<>();

    List<Integer> listOfFutureDayOfMonth = new ArrayList<>();
    List<Integer> listOfFutureDayOfWeek = new ArrayList<>();
    List<Integer> listOfFutureHourOfDay = new ArrayList<>();

    List<Double> listOfForecastedTemperature = new ArrayList<>();
    List<Double> listOfForecastedCloudCover = new ArrayList<>();
    List<Double> listOfForecastedWindDirection = new ArrayList<>();
    List<Double> listOfForecastedWindSpeed = new ArrayList<>();

    for(int proximity = 24; proximity >= 1; proximity--)
    {
      int prevTimeslot = currentTimeslot - proximity + 1;
      DateTime prevDate = timeslotRepo.getDateTimeForIndex(prevTimeslot);

      Integer dayOfMonth = prevDate.getDayOfMonth();
      Integer dayOfWeek = prevDate.getDayOfWeek();
      Integer hourOfDay = prevDate.getHourOfDay();

      Double temperature = weatherInformation.getWeatherReport(prevTimeslot).getTemperature();
      Double windSpeed = weatherInformation.getWeatherReport(prevTimeslot).getWindSpeed();
      Double windDirection = weatherInformation.getWeatherReport(prevTimeslot).getWindDirection();
      Double cloudCover = weatherInformation.getWeatherReport(prevTimeslot).getCloudCover();

      listOfDayOfMonth.add(dayOfMonth);
      listOfDayOfWeek.add(dayOfWeek);
      listOfHourOfDay.add(hourOfDay);

      listOfTemperature.add(temperature);
      listOfWindSpeed.add(windSpeed);
      listOfWindDirection.add(windDirection);
      listOfCloudCover.add(cloudCover);
    }

    for(int proximity = 1; proximity <= 24; proximity++)
    {
      int ft = currentTimeslot + proximity;
      DateTime fd = timeslotRepo.getDateTimeForIndex(ft);

      Integer dayOfMonth = fd.getDayOfMonth();
      Integer dayOfWeek = fd.getDayOfWeek();
      Integer hourOfDay = fd.getHourOfDay();

      Double t = weatherInformation.getWeatherForecast(currentTimeslot).getPredictions().get(proximity-1).getTemperature();
      Double cc = weatherInformation.getWeatherForecast(currentTimeslot).getPredictions().get(proximity-1).getCloudCover();
      Double ws = weatherInformation.getWeatherForecast(currentTimeslot).getPredictions().get(proximity-1).getWindSpeed();
      Double wd = weatherInformation.getWeatherForecast(currentTimeslot).getPredictions().get(proximity-1).getWindDirection();

      listOfFutureDayOfMonth.add(dayOfMonth);
      listOfFutureDayOfWeek.add(dayOfWeek);
      listOfFutureHourOfDay.add(hourOfDay);

      listOfForecastedTemperature.add(t);
      listOfForecastedCloudCover.add(cc);
      listOfForecastedWindDirection.add(ws);
      listOfForecastedWindSpeed.add(wd);
    }

    for (Map.Entry<TariffSpecification, Map<CustomerInfo, CustomerRecord>> tariffMap : customerSubscriptions.entrySet())
    {
      TariffSpecification specification = tariffMap.getKey();
      Map<CustomerInfo, CustomerRecord> customerMap = tariffMap.getValue();

      try
      {
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }

      try
      {
        List<TariffSpecification> candidate = ownTariffs.get(specification.getPowerType());

        if(candidate.contains(specification))
        {
          for (CustomerRecord record : customerMap.values())
          {
            try
            {
              List<Double> listOfAvgUsage = new ArrayList<>();
              List<Double> listOfMinUsage = new ArrayList<>();
              List<Double> listOfMaxUsage = new ArrayList<>();
              List<Double> listOfTariff = new ArrayList<>();

              List<Double> listOfUsagePerPopulation = new ArrayList<>();
              List<Double> listOfUsagePerPopulation1 = new ArrayList<>();

              String customer = record.getCustomerInfo().getName();

              Double maxUsage = custUsageInfo.getCustomerMaxUsage(customer);
              Double minUsage = custUsageInfo.getCustomerMinUsage(customer);
              Double avgUsage = custUsageInfo.getCustomerAvgUsageMap(customer);

              listOfAvgUsage.add(avgUsage);
              listOfMinUsage.add(minUsage);
              listOfMaxUsage.add(maxUsage);

              Integer totalPopulation = gameInformation.getPopulation(customer);

              int subscribedPopulation = record.subscribedPopulation;

              if(listOfTargetedProducers.contains(customer))
              {
                Double[] allUsage= custUsageInfo.getCustomerUsageMap(customer);

                for (int i = 23; i >= 0; i--)
                {
                  if(allUsage[currentTimeslot - i] != -1.0)
                  {
                      listOfUsagePerPopulation.add(allUsage[currentTimeslot - i]);
                  }
                  else
                  {
                    int index = (currentTimeslot - i) % brokerContext.getUsageRecordLength();
                    double pred = Math.abs(record.getUsage(index)); // subscribedPopulation;            // Sign is different in both the CUP Methods, so minus sign instead of plus
                    listOfUsagePerPopulation.add(pred);
                  }
                }

                for (int i = 191; i >= 168; i--)
                {
                  if(allUsage[currentTimeslot - i] != -1.0)
                  {
                      listOfUsagePerPopulation1.add(allUsage[currentTimeslot - i]);
                  }
                  else
                  {
                    int index = (currentTimeslot - i) % brokerContext.getUsageRecordLength();
                    double pred = Math.abs(record.getUsage(index)); // subscribedPopulation;            // Sign is different in both the CUP Methods, so minus sign instead of plus
                    listOfUsagePerPopulation1.add(pred);
                  }
                }

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

                for(int proximity = 1; proximity <= 24; proximity++)
                {
                  int futureTimeslot = currentTimeslot + proximity;
                  DateTime futureDate = timeslotRepo.getDateTimeForIndex(futureTimeslot);

                  Integer hourOfDay = futureDate.getHourOfDay();

                  listOfTariff.add(arr[hourOfDay]);
                }

                ArrayList<Double> predictions = HelperForJSON.communicateCUP("http://localhost:5000/CUPredictionFFN", customer,
                                                                              listOfDayOfMonth, listOfDayOfWeek, listOfHourOfDay, listOfTemperature, listOfWindSpeed,
                                                                              listOfWindDirection, listOfCloudCover, listOfAvgUsage, listOfMinUsage, listOfMaxUsage,
                                                                              listOfTariff, listOfUsagePerPopulation, listOfUsagePerPopulation1);

                //LinkedList<Double> subscriptions = customerSubscriptionPredictionMap.get(customer);  // Update this map every timeslot

                if(customer.equals("WindmillCoOp-1") || customer.equals("WindmillCoOp-2"))
                {
                  for(int proximity = 1; proximity <= 24; proximity++)
                  {
                    //double projectedUsage = predictions.get(proximity-1) * subscriptions.get(proximity-1) * totalPopulation;
                    double projectedUsage = predictions.get(proximity-1) * totalPopulation;
                    customerUsageValidation.updateCustomerUsageMap(customer, (currentTimeslot+proximity), Math.abs(projectedUsage) ,false);
                    result[proximity - 1] -= projectedUsage;
                  }
                }
                else
                {
                  for(int proximity = 1; proximity <= 24; proximity++)
                  {
                    int futureTimeslot = currentTimeslot + proximity;
                    DateTime fd = timeslotRepo.getDateTimeForIndex(futureTimeslot);

                    Integer hourOfDay = fd.getHourOfDay();

                    double projectedUsage = 0.0D;

                    if(hourOfDay >= 6 && hourOfDay < 18)
                    {
                      //projectedUsage = predictions.get(proximity-1) * subscriptions.get(proximity-1) * totalPopulation;
                      projectedUsage = predictions.get(proximity-1) * totalPopulation;
                    }

                    customerUsageValidation.updateCustomerUsageMap(customer, (currentTimeslot+proximity), Math.abs(projectedUsage) ,false);
                    result[proximity - 1] -= projectedUsage;
                  }
                }
              }
              else if(listOfTargetedConsumers.contains(customer)) // && subscribedPopulation != 0)
              {
                listOfUsagePerPopulation = new ArrayList<>();
                listOfTariff = new ArrayList<>();

                Double[] allUsage= custUsageInfo.getCustomerUsageMap(customer);

                for (int i = 191; i >= 0; i--)
                {
                  if(allUsage[currentTimeslot - i] != -1.0)
                  {
                      listOfUsagePerPopulation.add(allUsage[currentTimeslot - i]);
                  }
                  else
                  {
                    int index = (currentTimeslot - i) % brokerContext.getUsageRecordLength();
                    double pred = -record.getUsage(index); // subscribedPopulation;            // Sign is different in both the CUP Methods, so minus sign instead of plus
                    listOfUsagePerPopulation.add(pred);
                  }
                }

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

                for(int proximity = 24; proximity >= 1; proximity--)
                {
                  int prevTimeslot = currentTimeslot - proximity + 1;
                  DateTime preDate = timeslotRepo.getDateTimeForIndex(prevTimeslot);

                  Integer hourOfDay = preDate.getHourOfDay();

                  listOfTariff.add(arr[hourOfDay]);
                }

                ArrayList<Double> predictions = HelperForJSON.communicateCUP("http://localhost:5000/CUPredictionLSTM", customer,
                                                                              listOfDayOfMonth, listOfDayOfWeek, listOfHourOfDay, listOfTemperature, listOfWindSpeed,
                                                                              listOfWindDirection, listOfCloudCover, listOfAvgUsage, listOfMinUsage, listOfMaxUsage,
                                                                              listOfTariff, listOfUsagePerPopulation, null);

                LinkedList<Double> subscriptions = customerSubscriptionPredictionMap.get(customer);  // Update this map every timeslot

                for(int proximity = 1; proximity <= 24; proximity++)
                {
                  double projectedUsage = predictions.get(proximity-1) * subscriptions.get(proximity-1) * totalPopulation;

                  //if(customer.equals("CentervilleHomes") && (proximity == 1))
                  //{
                  //  System.out.println("Count : " + (subscriptions.get(proximity-1) * totalPopulation) + " :: Predicted Usage : " + projectedUsage);
                  //}

                  customerUsageValidation.updateCustomerUsageMap(customer, (currentTimeslot+proximity), Math.abs(projectedUsage) ,false);
                  result[proximity - 1] += projectedUsage;
                }
              }
              else
              {
                //Sample Broker's CUP
                for(int proximity = 1; proximity <= 24; proximity++)
                {
                  int executionTimeslot = currentTimeslot + proximity;
                  int index = (executionTimeslot) % brokerContext.getUsageRecordLength();

                  double pred = -record.getUsage(index);
                  result[proximity - 1] += pred;         // Sign is different in both the CUP Methods, so minus sign instead of plus
                }
              }
            }
            catch(Exception e)
            {
              //Sample Broker's CUP
              for(int proximity = 1; proximity <= 24; proximity++)
              {
                int executionTimeslot = currentTimeslot + proximity;
                int index = (executionTimeslot) % brokerContext.getUsageRecordLength();

                double pred = -record.getUsage(index);
                result[proximity - 1] += pred;         // Sign is different in both the CUP Methods, so minus sign instead of plus
              }
            }
          }
        }
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
    }

    for(int proximity = 1; proximity <= 24; proximity++)
    {
      //if(proximity == 1)
      //  System.out.println("Predicted Net Usage : " + result[proximity-1]);

      customerUsageValidation.updateNetUsageMap((currentTimeslot+proximity) , result[proximity-1], false);
    }

    return result;
  }

  // Customer Migration Predictor FFN
  public void CMPredictionFFN (int currentTimeslot)        // Not for Producers
  {
    Map<String, Integer> mapOfCustomers = gameInformation.getCustomerInfo();
    List<String> listOfTargetedConsumers = Helper.getListOfTargetedConsumers();

    List<Double> listOfCompareToBestTariffs = new ArrayList<>();

    double ownBestTariff = -Double.MAX_VALUE;
    double competitorBestTariff = -Double.MAX_VALUE;

    for(TariffSpecification spec1 : ownTariffs.get(PowerType.CONSUMPTION))
    {
      ownBestTariff = Math.max(Helper.evaluateCost(spec1), ownBestTariff);
    }

    for(TariffSpecification spec1 : competingTariffs.get(PowerType.CONSUMPTION))
    {
      competitorBestTariff = Math.max(Helper.evaluateCost(spec1), competitorBestTariff);
    }

    listOfCompareToBestTariffs.add((ownBestTariff - competitorBestTariff));

    for(Map.Entry<String, Integer> map : mapOfCustomers.entrySet())
    {
      String customer = map.getKey();
      Integer population = map.getValue();

      if(listOfTargetedConsumers.contains(customer))
      {
        ArrayList<Double> predictions = HelperForJSON.communicateCM("http://localhost:5000/CMPredictionFFN", customer, listOfCompareToBestTariffs);

        LinkedList<Double> predictedSubscriptions = new LinkedList<>();

        for(Double item : predictions)
        {
          for(int i = 0; i < 6; i++)
          {
            predictedSubscriptions.addLast(item);
          }
        }
        customerSubscriptionPredictionMap.put(customer, predictedSubscriptions);
      }
    }
  }

  public void updateCustomerSubscriptionPredictionMap()
  {
    for(Map.Entry<String, LinkedList<Double>> subscriptions : customerSubscriptionPredictionMap.entrySet())
    {
      Double last = subscriptions.getValue().getLast();

      subscriptions.getValue().pollFirst();
      subscriptions.getValue().addLast(last);
    }
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

   public synchronized void handleMessage(BalanceReport br)
   {
       int timeslot = br.getTimeslotIndex();
       netImbalanceValidation.updateImbalanceMap(timeslot, br.getNetImbalance(),true);
   }

   public synchronized void handleMessage(ClearedTrade ct)
   {
       Integer messageTimeslot = timeslotRepo.getTimeslotIndex(ct.getDateExecuted());
       Integer executionTimeslot = ct.getTimeslotIndex();

       mcpValidation.updateMCPMap((messageTimeslot-1), executionTimeslot, Math.abs(ct.getExecutionPrice()) ,true);
   }

  /**
   * Handles a TariffSpecification. These are sent by the server when new tariffs are
   * published. If it's not ours, then it's a competitor's tariff. We keep track of
   * competing tariffs locally, and we also store them in the tariffRepo.
   */
  public synchronized void handleMessage (TariffSpecification spec)
  {
    //System.out.println("Broker : " + spec.getBroker().getUsername() + " :: Spec : " + spec.getRates());

    if((spec.getBroker().getUsername().equals("default broker")) && (spec.getPowerType()==PowerType.CONSUMPTION))
    {
        dfrate=spec.getRates().get(0).getValue();
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

    //if(spec.getPowerType() == PowerType.CONSUMPTION)
    //{
    //  System.out.println("Calling Migrations");
    //  CMPredictionFFN(currentTimeslot);
    //}
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
     int currentTimeslot = timeslotRepo.currentTimeslot().getSerialNumber();

     List<String> listOfTargetedConsumers = Helper.getListOfTargetedConsumers();
     List<String> listOfTargetedProducers = Helper.getListOfTargetedProducers();

     totalUsage += ttx.getKWh();

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

       if(listOfTargetedConsumers.contains(customerName) || listOfTargetedProducers.contains(customerName))
       {
         customerUsageValidation.updateCustomerUsageMap(customerName, currentTimeslot, Math.abs(ttx.getKWh()) ,true);
       }

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

     //if(ttx.getCustomerInfo().getName().equals("CentervilleHomes") && (TariffTransaction.Type.CONSUME == ttx.getTxType()))
     //{
     //  System.out.println("Customers : " + ttx.getCustomerCount() + " :: Usage : " + ttx.getKWh() + " :: Population : " + ttx.getCustomerCount());
     //}

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

    TariffSpecification old = tariffRepo.findSpecificationById(tr.getTariffId());

    List<TariffSpecification> candidates1 = ownTariffs.get(old.getPowerType());
    if (null == candidates1) {
      log.warn("Candidate list is null");
      return;
    }
    candidates1.remove(old);

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

  /*public synchronized void handleMessage(SimEnd se)
  {
    int timeslot = timeslotRepo.currentTimeslot().getSerialNumber();

    try
    {
      FileWriter fw = new FileWriter("SimEndCounter.txt", true);
      fw.write(bootFile + " :: " + timeslot + "\n");
      fw.close();
    }
    catch(Exception e)
    {}

    try
    {
      customerUsageValidation.printToFile(bootFile, gameInformation.getBrokers());
    }
    catch(Exception e){}

    try
    {
      mcpValidation.printToFile(bootFile, gameInformation.getBrokers());
    }
    catch(Exception e){}

    try
    {
      netImbalanceValidation.printToFile(bootFile, gameInformation.getBrokers());
    }
    catch(Exception e){}

    try
    {
      balancingMarketInformation.printToFile(bootFile, gameInformation.getBrokers());
    }
    catch(Exception e){}

    for(Map.Entry<String, SortedMap<Integer, MigrationInfo>> customers : customerMigration.getCustomerMigrationMap().entrySet())
    {
      try
      {
        String col = customers.getKey() + "_Migration_Info";
        DBCollection collection = mongoDatabase.getCollection(col);

        SortedMap<Integer, MigrationInfo> CMM = customers.getValue();

        for(SortedMap.Entry<Integer, MigrationInfo> item : CMM.entrySet())
        {
          DBObject document = new BasicDBObject();

          document.put("Game_Name", bootFile);
          document.put("Timeslot", item.getKey());
          document.put("Compare_To_Best_Tariff", item.getValue().compareToBest);
          document.put("Migrations", item.getValue().getMigrations());
          document.put("Population", item.getValue().getPopulation());
          document.put("Label", item.getValue().label);

          collection.insert(document);
        }
      }
      catch(Exception e)
      {}
    }
  }*/


  // --------------- activation -----------------
  /**
   * Called after TimeslotComplete msg received. Note that activation order
   * among modules is non-deterministic.
   */
  @Override // from Activatable
  public synchronized void activate (int timeslotIndex)
  {
    if(timeslotIndex >= 362)
    {
      //customerUsageValidation.updateNetUsageMap(timeslotIndex , totalUsage, true);   // define totalUsage
      //System.out.println("Actual Net Usage : " + totalUsage);
      //double predictedMCP[] = MCPPredictorFFN(timeslotIndex);
      //double predictedNetImbalance[] = NetImbalancePredictorFFN(timeslotIndex);
    }

    totalUsage = 0.0;

    if (customerSubscriptions.size() == 0) {
      // we (most likely) have no tariffs
      createInitialTariffs();
    }
    else {
      // we have some, are they good enough?
      improveTariffs();
    }

    //if(timeslotIndex >= 361)
    //{
    //  try
    //  {
    //    updateCustomerSubscriptionPredictionMap();      // Updating Customer Subscription Predictions
    //  }
    //  catch(Exception e)
    //  {}
    //}

    for (CustomerRecord record: notifyOnActivation)
      record.activate();
  }

  // Creates initial tariffs for the main power types. These are simple
  // fixed-rate two-part tariffs that give the broker a fixed margin.
  private void createInitialTariffs ()
  {
    // remember that market prices are per mwh, but tariffs are by kwh
    double marketPrice = marketManager.getMeanMarketPrice() / 1000.0;
    // for each power type representing a customer population,
    // create a tariff that's better than what's available
    for (PowerType pt : customerProfiles.keySet()) {
      // we'll just do fixed-rate tariffs for now
      double rateValue = ((marketPrice + fixedPerKwh) * (1.0 + defaultMargin));
      double periodicValue = defaultPeriodicPayment;
      if (pt.isProduction()) {
        rateValue = -2.0 * marketPrice;
        periodicValue /= 2.0;
      }
      if (pt.isStorage()) {
        periodicValue = 0.0;
      }
      if (pt.isInterruptible()) {
        rateValue *= 0.7; // Magic number!! price break for interruptible
      }
      //log.info("rateValue = {} for pt {}", rateValue, pt);
      log.info("Tariff {}: rate={}, periodic={}", pt, rateValue, periodicValue);
      TariffSpecification spec = new TariffSpecification(brokerContext.getBroker(), pt).withPeriodicPayment(periodicValue);
      Rate rate = new Rate().withValue(rateValue);
      if (pt.isInterruptible() && !pt.isStorage()) {
        // set max curtailment
        rate.withMaxCurtailment(0.4);
      }
      if (pt.isStorage()) {
        // add a RegulationRate
        RegulationRate rr = new RegulationRate();
        rr.withUpRegulationPayment(-rateValue * 1.2)
            .withDownRegulationPayment(rateValue * 0.4); // magic numbers
        spec.addRate(rr);
      }

      spec.addRate(rate);

      addOwnTariff(spec);

      customerSubscriptions.put(spec, new HashMap<>());
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
    }
  }

  // Checks to see whether our tariffs need fine-tuning
  private void improveTariffs()
  {
    // quick magic-number hack to inject a balancing order
    int timeslotIndex = timeslotRepo.currentTimeslot().getSerialNumber();
    if (371 == timeslotIndex) {
      for (TariffSpecification spec : tariffRepo.findTariffSpecificationsByBroker(brokerContext.getBroker())) {
        if (PowerType.INTERRUPTIBLE_CONSUMPTION == spec.getPowerType()) {
          BalancingOrder order = new BalancingOrder(brokerContext.getBroker(), spec, 0.5, spec.getRates().get(0).getMinValue() * 0.9);
          brokerContext.sendMessage(order);
        }
      }
      // add a battery storage tariff with overpriced regulation
      // should get no subscriptions...
      TariffSpecification spec = new TariffSpecification(brokerContext.getBroker(), PowerType.BATTERY_STORAGE);
      Rate rate = new Rate().withValue(-0.2);
      spec.addRate(rate);
      RegulationRate rr = new RegulationRate();
      rr.withUpRegulationPayment(10.0).withDownRegulationPayment(-10.0); // magic numbers
      spec.addRate(rr);
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
    }
    // magic-number hack to supersede a tariff
    if (380 == timeslotIndex) {
      // find the existing CONSUMPTION tariff
      TariffSpecification oldc = null;
      List<TariffSpecification> candidates = tariffRepo.findTariffSpecificationsByBroker(brokerContext.getBroker());
      if (null == candidates || 0 == candidates.size())
        log.error("No tariffs found for broker");
      else {
        // oldc = candidates.get(0);
        for (TariffSpecification candidate: candidates) {
          if (candidate.getPowerType() == PowerType.CONSUMPTION) {
            oldc = candidate;
            break;
          }
        }
        if (null == oldc) {
          log.warn("No CONSUMPTION tariffs found");
        }
        else {
          double rateValue = oldc.getRates().get(0).getValue();
          // create a new CONSUMPTION tariff
          TariffSpecification spec = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION).withPeriodicPayment(defaultPeriodicPayment * 1.1);
          Rate rate = new Rate().withValue(rateValue);
          spec.addRate(rate);
          if (null != oldc)
            spec.addSupersedes(oldc.getId());
          //mungId(spec, 6);
          tariffRepo.addSpecification(spec);
          brokerContext.sendMessage(spec);
          // revoke the old one
          TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), oldc);
          brokerContext.sendMessage(revoke);

          addOwnTariff(spec);
        }
      }
    }
    // Exercise economic controls every 4 timeslots
    if ((timeslotIndex % 4) == 3) {
      List<TariffSpecification> candidates = tariffRepo.findTariffSpecificationsByPowerType(PowerType.INTERRUPTIBLE_CONSUMPTION);
      for (TariffSpecification spec: candidates) {
        EconomicControlEvent ece = new EconomicControlEvent(spec, 0.2, timeslotIndex + 1);
        brokerContext.sendMessage(ece);
      }
    }
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
