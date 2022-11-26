package org.powertac.samplebroker;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Random;
import javafx.util.Pair;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.powertac.common.BalancingTransaction;
import org.powertac.common.CapacityTransaction;
import org.powertac.common.ClearedTrade;
import org.powertac.common.Competition;
import org.powertac.common.DistributionTransaction;
import org.powertac.common.MarketPosition;
import org.powertac.common.MarketTransaction;
import org.powertac.common.Order;
import org.powertac.common.Orderbook;
import org.powertac.common.Timeslot;
import org.powertac.common.WeatherForecast;
import org.powertac.common.WeatherReport;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.samplebroker.messages.ClearedTradeInformation;
import org.powertac.common.msg.BalanceReport;
import org.powertac.common.msg.MarketBootstrapData;
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

import org.powertac.samplebroker.wholesalemarket.*;
import org.powertac.samplebroker.information.SubmittedBidInformation;
import org.powertac.samplebroker.information.WholesaleMarketInformation;

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
 * Handles market interactions on behalf of the broker.
 * @author John Collins
 */
@Service
public class MarketManagerService
implements MarketManager, Initializable, Activatable
{
  static private Logger log = LogManager.getLogger(MarketManagerService.class);

  private BrokerContext broker; // broker

  @Autowired
  private MessageManager messageManager;

  // Spring fills in Autowired dependencies through a naming convention
  @Autowired
  private BrokerPropertiesService propertiesService;

  @Autowired
  private TimeslotRepo timeslotRepo;

  @Autowired
  private PortfolioManager portfolioManager;

  private SubmittedBidInformation submittedBidInformation;
  private WholesaleMarketInformation wholesaleMarketInformation;
  private ClearedTradeInformation clearedTradeInformation;
  private AFA afa;
  private AFP afp;

  // ------------ Configurable parameters --------------
  // max and min offer prices. Max means "sure to trade"

  @ConfigurableValue(valueType = "Double",
          description = "Upper end (least negative) of bid price range")
  private double buyLimitPriceMax = -1.0;  // broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Lower end (most negative) of bid price range")
  private double buyLimitPriceMin = -100.0;  // broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Upper end (most positive) of ask price range")
  private double sellLimitPriceMax = 100.0;    // other broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Lower end (least positive) of ask price range")
  private double sellLimitPriceMin = 0.5;    // other broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Minimum bid/ask quantity in MWh")
  private double minMWh = 0.001; // don't worry about 1 KWh or less

  @ConfigurableValue(valueType = "Integer",
          description = "If set, seed the random generator")
  private Integer seedNumber = null;

  // ---------------- local state ------------------
  private Random randomGen; // to randomize bid/ask prices

  //MongoDB client and Database
  MongoClient mongoClient;

  //MongoDB database
  DB mongoDatabase;

  String dbname;

  //Game Name
  String bootFile;

  // Bid recording
  private HashMap<Integer, Order> lastOrder;
  private double[] marketMWh;
  private double[] marketPrice;
  private double meanMarketPrice = 0.0;

  public MarketManagerService ()
  {
    super();
  }

  /* (non-Javadoc)
   * @see org.powertac.samplebroker.MarketManager#init(org.powertac.samplebroker.SampleBroker)
   */
  @Override
  public void initialize (BrokerContext broker)
  {
    this.broker = broker;
    lastOrder = new HashMap<>();
    propertiesService.configureMe(this);
    System.out.println("  name=" + broker.getBrokerUsername());
    if (seedNumber != null) {
      System.out.println("  seeding=" + seedNumber);
      log.info("Seeding with : " + seedNumber);
      randomGen = new Random(seedNumber);
    }
    else {
      randomGen = new Random();
    }

    //baselineMDP = messageManager.getVVGenericMDP();
    submittedBidInformation = new SubmittedBidInformation();
    afa = AFA.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
    afp = AFP.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);

    dbname = "PowerTAC2020_ZIP";

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
    System.out.println("Connected to Database " + dbname + " from Initialize in MarketManager");
  }

  // ----------------- data access -------------------
  /**
   * Returns the mean price observed in the market
   */
  @Override
  public double getMeanMarketPrice ()
  {
    return meanMarketPrice;
  }

  @Override
  public SubmittedBidInformation getSubmittedBidInformation()
  {
    return submittedBidInformation;
  }

  // --------------- message handling -----------------
  /**
   * Handles the Competition instance that arrives at beginning of game.
   * Here we capture minimum order size to avoid running into the limit
   * and generating unhelpful error messages.
   */
  public synchronized void handleMessage (Competition comp)
  {
    minMWh = Math.max(minMWh, comp.getMinimumOrderQuantity());
  }


  /**
   * Receives a MarketBootstrapData message, reporting usage and prices
   * for the bootstrap period. We record the overall weighted mean price,
   * as well as the mean price and usage for a week.
   */
  public synchronized void handleMessage (MarketBootstrapData data)
  {
    marketMWh = new double[broker.getUsageRecordLength()];
    marketPrice = new double[broker.getUsageRecordLength()];
    double totalUsage = 0.0;
    double totalValue = 0.0;

    for (int i = 0; i < data.getMwh().length; i++)
    {
      totalUsage += data.getMwh()[i];
      totalValue += data.getMarketPrice()[i] * data.getMwh()[i];
      if (i < broker.getUsageRecordLength())
      {
        // first pass, just copy the data
        marketMWh[i] = data.getMwh()[i];
        marketPrice[i] = data.getMarketPrice()[i];
      }
      else
      {
        // subsequent passes, accumulate mean values
        int pass = i / broker.getUsageRecordLength();
        int index = i % broker.getUsageRecordLength();
        marketMWh[index] = (marketMWh[index] * pass + data.getMwh()[i]) / (pass + 1);
        marketPrice[index] = (marketPrice[index] * pass + data.getMarketPrice()[i]) / (pass + 1);
      }
    }
    meanMarketPrice = totalValue / totalUsage;
  }

  /**
   * Receives a new MarketTransaction. We look to see whether an order we
   * have placed has cleared.
   */
  public synchronized void handleMessage (MarketTransaction tx)
  {
    // reset price escalation when a trade fully clears.
    Order lastTry = lastOrder.get(tx.getTimeslotIndex());
    if (lastTry == null) // should not happen
      log.error("order corresponding to market tx " + tx + " is null");
    else if (tx.getMWh() == lastTry.getMWh()) // fully cleared
      lastOrder.put(tx.getTimeslotIndex(), null);
  }

  public synchronized void handleMessage(TimeslotComplete ts)
  {
    int currentTimeslot = ts.getTimeslotIndex();

    try
    {
      String col6 = "Submitted_Bid_Information";
      DBCollection collection6 = mongoDatabase.getCollection(col6);

      Map<Integer, Pair<Double, Double>> MTI = submittedBidInformation.getSubmittedBidInformationbyMessageTimeslot(currentTimeslot-1);

      for(Map.Entry<Integer, Pair<Double, Double>> message : MTI.entrySet())
      {
        DBObject document6 = new BasicDBObject();

        Pair<Double, Double> m = message.getValue();

        document6.put("Game_Name", bootFile);
        document6.put("Bidding_Timeslot", currentTimeslot-1);
        document6.put("Execution_Timeslot", message.getKey());
        document6.put("LimitPrice", m.getKey());
        document6.put("Broker's_Bidded_Quantity", m.getValue());

        collection6.insert(document6);
      }
    }
    catch(Exception e){}
    ////////////////////////////////////////////////////////////////////////////////////////////////////
  }

  // ----------- per-timeslot activation ---------------

  /**
   * Compute needed quantities for each open timeslot, then submit orders
   * for those quantities.
   *
   * @see org.powertac.samplebroker.interfaces.Activatable#activate(int)
   */
  @Override
  public synchronized void activate (int timeslotIndex)
  {
    wholesaleMarketInformation = messageManager.getWholesaleMarketInformation();
    clearedTradeInformation = messageManager.getClearTradeInformation();

    double neededMWh;

    if(timeslotIndex > 361)
    {
      // double[] predictedMCP = portfolioManager.MCPPredictorFFN(timeslotIndex);
      // Map<Integer, Pair<Double, Double>> cti = clearedTradeInformation.getClearedTradebyMessageTimeslot(timeslotIndex);

      // for(int i = 0; i < 24; i++)
      // {
      //   afp.setAvgLimitPriceMap(i+1, predictedMCP[i]);
      // }

      // for(Map.Entry<Integer, Pair<Double, Double>> item : cti.entrySet())
      // {
      //   int proximity = item.getKey() - timeslotIndex + 1;

      //   afa.setAvgLimitPriceMap(proximity, item.getValue().getKey());
      // }

      log.debug("Current timeslot is " + timeslotIndex);
      System.out.println("Current Timeslot : " + timeslotIndex);

      for (Timeslot timeslot : timeslotRepo.enabledTimeslots())
      {
        int index = (timeslot.getSerialNumber()) % broker.getUsageRecordLength();
        //neededMWh[(timeslot.getSerialNumber()-currentTimeslot)] = portfolioManager.collectUsage(index) / 1000.0;
        neededMWh = portfolioManager.collectUsage(index) / 1000.0;
        submitOrder(neededMWh , timeslot.getSerialNumber());                         
      }
    }
  }

  /**
   * Composes and submits the appropriate order for the given timeslot.
   */
  private void submitOrder (double neededMWh, Integer timeslot)
  {
    int currentTimeslot = timeslotRepo.currentTimeslot().getSerialNumber();

    double totalAmountNeeded = neededMWh;

    MarketPosition posn = broker.getBroker().findMarketPositionByTimeslot(timeslot);

    if (posn != null)
      neededMWh -= posn.getOverallBalance();
    if (Math.abs(neededMWh) <= minMWh)
    {
      log.info("no power required in timeslot " + timeslot);
      return;
    }
    Order order = prepareOrder(timeslot, neededMWh, totalAmountNeeded);
    System.out.println("Submitted Bid: Broker : " + order.getBroker() + ", Timeslot : " + order.getTimeslotIndex() + ", Price : " + order.getLimitPrice() + ", Quantity : " + order.getMWh());
    lastOrder.put(timeslot, order);
    broker.sendMessage(order);
  }

  private Order prepareOrder(int timeslot, double amountNeeded, double totalAmountNeeded)
  {
    Double limitPrice = null;
    Order order = null;

    int currentTimeslot = timeslotRepo.currentTimeslot().getSerialNumber();

    Strategies strategy;

    switch(broker.getBrokerUsername())
    {
      case "ZI":
                strategy = ZI.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, amountNeeded, limitPrice);
                break;

      case "ZIP":
                strategy = ZIP.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, amountNeeded, limitPrice);
                break;

      case "AFA":
                strategy = AFA.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                amountNeeded = strategy.computeQuantity(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, amountNeeded, limitPrice);
                break;

    case "AFP":
              strategy = AFP.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
              limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
              amountNeeded = strategy.computeQuantity(timeslot, currentTimeslot, amountNeeded);
              order = strategy.submitBid(timeslot, amountNeeded, limitPrice);
              break;

      case "TT":
                strategy = TT.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, amountNeeded, limitPrice);
                break;

      case "Linear":
                strategy = Linear.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, amountNeeded, limitPrice);
                break;

      case "Eagerness":
                strategy = Eagerness.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager, submittedBidInformation);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded, totalAmountNeeded);
                order = strategy.submitBid(timeslot, amountNeeded, limitPrice);
                break;

      case "LE":
                strategy = LE.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager, submittedBidInformation);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded, totalAmountNeeded);
                order = strategy.submitBid(timeslot, amountNeeded, limitPrice);
                break;

      default:
                limitPrice = computeLimitPrice(timeslot, amountNeeded);
                order = new Order(this.broker.getBroker(), timeslot, amountNeeded, limitPrice);
    }

    if(limitPrice == null)
    {
      wholesaleMarketInformation.setWholesaleMarketOrderMap(timeslot, amountNeeded);
    }

    submittedBidInformation.setSubmittedBidInformationbyExecutionTimeslot(timeslot, currentTimeslot, limitPrice, amountNeeded);
    submittedBidInformation.setSubmittedBidInformationbyMessageTimeslot(currentTimeslot, timeslot, limitPrice, amountNeeded);

    return order;
  }

  private Map<Integer, Double> findLeastMCPs(Integer currentTimeslot, double[] predictions)
  {
    Map<Integer, Double> map = new HashMap<>();
    Map<Integer, Double> sortedMap = new HashMap<>();

    for(int i = 1; i <= 24; i++)
    {
      map.put(currentTimeslot+i, predictions[i-1]);
    }

    map.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .forEachOrdered(x -> sortedMap.put(x.getKey(), x.getValue()));

    Map<Integer, Double> least = new HashMap<>();

    int numberOfBids = 5;

    for(Map.Entry<Integer, Double> item : sortedMap.entrySet())
    {
      Integer timeslot = item.getKey();
      Double limitPrice = item.getValue();

      if(numberOfBids > 0)
      {
        least.put(timeslot, limitPrice);
        numberOfBids--;
      }
      else
        break;
    }

    return least;
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
      log.debug("lastTry: " + lastTry.getMWh() + " at " + lastTry.getLimitPrice());
    if (lastTry != null && Math.signum(amountNeeded) == Math.signum(lastTry.getMWh())) {
      oldLimitPrice = lastTry.getLimitPrice();
      log.debug("old limit price: " + oldLimitPrice);
    }

    // set price between oldLimitPrice and maxPrice, according to number of
    // remaining chances we have to get what we need.
    double newLimitPrice = minPrice; // default value
    int current = timeslotRepo.currentSerialNumber();
    int remainingTries = (timeslot - current - Competition.currentCompetition().getDeactivateTimeslotsAhead());
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
