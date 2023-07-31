package org.powertac.samplebroker.messages;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joda.time.Instant;
import org.powertac.common.CustomerInfo;
import org.powertac.common.enumerations.PowerType;


public class GameInformation {
    /** The competition's ID */
    private long compID;

    /** The competition's name */
    private String name;

    /** POM ID from server.properties */
    private String pomId;

    /** Simulation base time */
    private Instant simulationBaseTime;

    /** length of a timeslot in simulation minutes    */
    private int timeslotLength;

    /** Number of timeslots in initialization data dump */
    private int bootstrapTimeslotCount;  // 14 days = 336 (usually)

    /** Number of extra timeslots at start of bootstrap before data collection starts */
    private int bootstrapDiscardedTimeslots;  // ususally 1 day = 24 time slots

    /** Minimum number of timeslots, aka competition / game length    */
    private int minimumTimeslotCount;

    /** concurrently open timeslots, i.e. time window in which broker actions like trading are allowed   */
    private int timeslotsOpen;   //usually 24

    /** # timeslots a timeslot gets deactivated ahead of the now timeslot (default: 1 timeslot, which (given default length of 60 min) means that e.g. trading is disabled 60 minutes ahead of time    */
    private int deactivateTimeslotsAhead;  //usually 1

    /** Minimum order quantity */
    private double minimumOrderQuantity; // usually 0.01 MWh

    // Tariff evaluation parameters
    /** Above this ratio, regulation is discounted. */
    private double maxUpRegulationPaymentRatio; // usually  = -4.0;

    private double upRegulationDiscount;  //usually = 0.5

    /** Above this ratio, customer will discount down-regulation,
     either during evaluation nor at runtime. */
    private double maxDownRegulationPaymentRatio;  //usually = 1.5

    private double downRegulationDiscount; //usually = 0.4

    /** Brokers typically pay less for production than they charge for
     consumption. This ratio is an estimate of that margin that is used
     to modify the constraints on up- and down- regulation behavior. */
    private double estimatedConsumptionPremium;   //usually 2.0

    /** timezone offset for scenario locale */
    private int timezoneOffset;

    /** approximate latitude in degrees north for scenario locale */
    private int latitude = 45;

    /** List of brokers in the game **/
    private ArrayList<String> brokers;

    /** List of customer models in the game **/
    private ArrayList<CustomerInfo> customers;

    /** Total number of customers in the game **/
    private Integer customerCount;

    /** Hashmap for Powertype-Customer report **/
    private Map<String, ArrayList<String>> customerPowerTypeMap;

    /** Hashmap for Multicontracting-Customer report **/
    private Map<Boolean, ArrayList<String>> customerMultiContractingMap;

    /** Hashmap for CustomerClass-Customer report **/
    private Map<String, ArrayList<String>> customerClassMap;

    /** Hashmap for CustomerClass-population report **/
    private Map<Integer, ArrayList<String>> customerPopulationMap;

    /**HasMap for CustomerName - Populattion **/
    private Map<String, Integer> populationMap;

    /**HasMap for CustomerName - Powertype **/
    private Map<String, PowerType> powerTypeMap;

    /** Default constructor
     *
     */

    public GameInformation() {
        super();
    }

    /** Game properties from boot.xml file
     *
     * @param compID
     * @param name
     * @param pomId
     * @param basetime
     * @param timeslotLength
     * @param bootstrapTimeslotCount
     * @param bootstrapDiscardedTimeslots
     * @param minimumTimeslotCount
     * @param timeslotsOpen
     * @param deactivateTimeslotsAhead
     * @param minimumOrderQuantity
     * @param maxUpRegulationPaymentRatio
     * @param upRegulationDiscount
     * @param maxDownRegulationPaymentRatio
     * @param downRegulationDiscount
     * @param estimatedConsumptionPremium
     * @param timezoneOffset
     * @param latitude
     * @param brokers
     * @param customers
     */

    public void setGameInformation(long compID, String name, String pomId, Instant basetime, int timeslotLength, int bootstrapTimeslotCount,
                                    int bootstrapDiscardedTimeslots, int minimumTimeslotCount, int timeslotsOpen, int deactivateTimeslotsAhead,
                                    double minimumOrderQuantity, double maxUpRegulationPaymentRatio, double upRegulationDiscount,
                                    double maxDownRegulationPaymentRatio, double downRegulationDiscount, double estimatedConsumptionPremium,
                                    int timezoneOffset, int latitude, List<String> brokers, List<CustomerInfo> customers){
        this.compID = compID;
        this.name = name;
        this.pomId = pomId;
        this.simulationBaseTime = basetime;
        this.timeslotLength = timeslotLength;
        this.bootstrapTimeslotCount = bootstrapTimeslotCount;
        this.bootstrapDiscardedTimeslots = bootstrapDiscardedTimeslots;
        this.minimumTimeslotCount = minimumTimeslotCount;
        this.timeslotsOpen = timeslotsOpen;
        this.deactivateTimeslotsAhead = deactivateTimeslotsAhead;
        this.minimumOrderQuantity = minimumOrderQuantity;
        this.maxUpRegulationPaymentRatio = maxUpRegulationPaymentRatio;
        this.upRegulationDiscount = upRegulationDiscount;
        this.maxDownRegulationPaymentRatio = maxDownRegulationPaymentRatio;
        this.downRegulationDiscount = downRegulationDiscount;
        this.estimatedConsumptionPremium = estimatedConsumptionPremium;
        this.timezoneOffset = timezoneOffset;
        this.latitude = latitude;
        this.brokers = new ArrayList<>(brokers);
        this.customers = new ArrayList<> (customers);

        this.customerCount = customers.size();
        this.customerPowerTypeMap = new HashMap<>();
        this.customerMultiContractingMap = new HashMap<>();
        this.customerClassMap = new HashMap<>();
        this.customerPopulationMap = new HashMap<>();
        this.populationMap = new HashMap<>();
        this.powerTypeMap = new HashMap<>();

        ArrayList<String> ptList = null;
        ArrayList<String> populationList = null;
        ArrayList<String> classList =  null;
        ArrayList<String> multiList = null;

        for (int i=0; i < customerCount; i++){
            String custName = this.customers.get(i).getName();

            PowerType pType = customers.get(i).getPowerType();
            String pt = customers.get(i).getPowerType().toString();
            Integer population = customers.get(i).getPopulation();
            String custClass = customers.get(i).getCustomerClass().toString();
            Boolean multContract = customers.get(i).isMultiContracting();

            this.populationMap.put(custName,population);
            this.powerTypeMap.put(custName,pType);

            ptList = this.customerPowerTypeMap.get(pt);
            if (ptList == null)
                ptList = new ArrayList<>();
             else
                 ptList.add(custName);
            this.customerPowerTypeMap.put(pt,ptList);


            populationList = this.customerPopulationMap.get(population);
            if (populationList == null)
                populationList = new ArrayList<>();
            else
                populationList.add(custName);
            this.customerPopulationMap.put(population, populationList);

            classList = this.customerClassMap.get(custClass);
            if (classList == null)
                classList = new ArrayList<>();
            else
                classList.add(custName);
            this.customerClassMap.put(custClass, classList);

            multiList = this.customerMultiContractingMap.get(multContract);
            if (multiList == null)
                multiList = new ArrayList<>();
            else
                multiList.add(custName);
            this.customerMultiContractingMap.put(multContract, multiList);

        }
    }

    /**
     *
     * @return competition ID
     */

    public long getCompID(){
        return compID;
    }

    /**
     *
     * @return latitute
     */
    public int getLatitude() {
        return latitude;
    }

    /**
     *
     * @return customer game name, if any
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @return
     */
    public String getPomId() {
        return pomId;
    }

    /**
     *
     * @return
     */
    public Instant getSimulationBaseTime() {
        return this.simulationBaseTime;
    }

    /**
     *
     * @return
     */

    public int getTimeslotLength() {
        return timeslotLength;
    }


    /**
     *
     * @return
     */

    public int getBootstrapTimeslotCount() {
        return bootstrapTimeslotCount;
    }

    /**
     *
     * @return
     */

    public int getBootstrapDiscardedTimeslots() {
        return bootstrapDiscardedTimeslots;
    }

    /**
     *
     * @return
     */

    public int getMinimumTimeslotCount() {
        return minimumTimeslotCount;
    }

    /**
     *
     * @return
     */

    public int getTimeslotsOpen() {
        return timeslotsOpen;
    }

    /**
     *
     * @return
     */
    public int getDeactivateTimeslotsAhead() {
        return deactivateTimeslotsAhead;
    }

    /**
     *
     * @return
     */
    public double getMinimumOrderQuantity() {
        return minimumOrderQuantity;
    }

    /**
     *
     * @return
     */
    public double getMaxUpRegulationPaymentRatio() {
        return maxUpRegulationPaymentRatio;
    }

    /**
     *
     * @return
     */

    public double getUpRegulationDiscount() {
        return upRegulationDiscount;
    }

    /**
     *
     * @return
     */
    public double getMaxDownRegulationPaymentRatio() {
        return maxDownRegulationPaymentRatio;
    }

    /**
     *
     * @return
     */
    public double getDownRegulationDiscount() {
        return downRegulationDiscount;
    }


    /**
     *
     * @return
     */
    public double getEstimatedConsumptionPremium() {
        return estimatedConsumptionPremium;
    }

    /**
     *
     * @return
     */
    public int getTimezoneOffset() {
        return timezoneOffset;
    }

    /**
     *
     * @return
     */
    public ArrayList<String> getBrokers() {
        return brokers;
    }

    /**
     *
     * @return
     */
    public Integer getNumberOfBroker(){
        return brokers.size();
    }

    /**
     *
     * @return
     */
    public ArrayList<CustomerInfo> getCustomers() {
        return customers;
    }


    /**
     *
     * @return
     */
    public Integer getNumPopulation(){
        return this.customerCount;
    }

    /**
     *
     * @return
     */
    public Map<String, Integer> getCustomerInfo(){
        return this.populationMap;
    }

    /**
     *
     * @return
     */
    public Integer getPopulation(String custName){
        return this.populationMap.get(custName);
    }

    /**
     *
     * @return
     */
    public PowerType getPowerType(String custName){
        return this.powerTypeMap.get(custName);
    }

    /**
     *
     * @return
     */
    public Map<String, ArrayList<String>> getCustomerPowerTypeMap(){
        return this.customerPowerTypeMap;
    }

    /**
     *
     * @return
     */
    public Map<Boolean, ArrayList<String>> getCustomerMultiContractingMap(){
        return this.customerMultiContractingMap;
    }

    /**
     *
     * @return
     */
    public Map<String, ArrayList<String>> getCustomerClassMap(){
        return this.customerClassMap;
    }

    /**
     *
     * @return
     */
    public Map<Integer, ArrayList<String>> getCustomerPopulationReport(){
        return this.customerPopulationMap;
    }


    /**
     * @to String method
     */

    @Override
    public String toString() {

        String str = "";
        str += " Game Information \n " ;
        str +=  " Game name " + this.name + " Game POM ID " + this.pomId + " Game competition id " + this.compID + " time slot length " + this.timeslotLength + " boot strap time slot count " + this.bootstrapTimeslotCount;
        str += " discarded time slot counts " + this.bootstrapDiscardedTimeslots + " minimum time slot count " + this.minimumTimeslotCount + " time slot open " + this.timeslotsOpen + " deactivated time slots ahead " + this.deactivateTimeslotsAhead;
        str += " Minimum order quantity " + this.minimumOrderQuantity + " up regulation ratio " + this.maxUpRegulationPaymentRatio + " up regulation discount " + this.upRegulationDiscount;
        str += " down regulation ratio " + this.maxDownRegulationPaymentRatio + " down regulation discount " + this.downRegulationDiscount + " estimated consumption premium " + this.estimatedConsumptionPremium;
        str += " latitute " + this.latitude + " time zone offset " + this.timezoneOffset;
        str += " Competing brokers " + this.brokers.toString();
        str += " customers in the game " + this.customers.toString();
        str += " total number of customers in the game " + this.customerCount;

        str += " Powertype map \n ";
        try{
            for (Map.Entry<String, ArrayList<String>> om:this.customerPowerTypeMap.entrySet()){
                str += " Key : " + om.getKey() + " Values " + om.getValue().toString() + " \n ";
            }
        }
        catch(Exception e){
            System.out.println(e.toString());
        }

        str += " Customer class map \n ";
        try{
            for (Map.Entry<String, ArrayList<String>> om:this.customerClassMap.entrySet()){
                str += " Key : " + om.getKey() + " Values " + om.getValue().toString() + " \n ";
            }
        }
        catch(Exception e){
            System.out.println(e.toString());
        }

        str += " Population map \n ";
        try{
            for (Map.Entry<Integer, ArrayList<String>> om:this.customerPopulationMap.entrySet()){
                str += " Key : " + om.getKey() + " Values " + om.getValue().toString() + " \n ";
            }
        }
        catch(Exception e){
            System.out.println(e.toString());
        }

        str += " Multicontracting map \n ";
        try{
            for (Map.Entry<Boolean, ArrayList<String>> om:this.customerMultiContractingMap.entrySet()){
                str += " Key : " + om.getKey() + " Values " + om.getValue().toString() + " \n ";
            }
        }
        catch(Exception e){
            System.out.println(e.toString());
        }


        str += " Competition message toString Method completed ";
        return str;

    }

}
