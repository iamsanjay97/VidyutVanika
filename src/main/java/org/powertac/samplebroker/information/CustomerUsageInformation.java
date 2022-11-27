package org.powertac.samplebroker.information;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class CustomerUsageInformation {

    private Map<Integer, ArrayList<CustomerSubscriptionInformation>> customerSubscriptionList;
    private Map<String, Map<Integer, CustomerSubscriptionInformation>> customerSubscriptionMap;
    private Map<String, Map<Integer, UsageRecord>> customerActualUsageMap;
    private Map<String, Double[]> customerUsageProjectionMap;
    private Map<String, Double[]> customerUsageMap;
    private Map<String, Vector<Double>> customerBootUsageMap;
    private Map<String, Double> customerMaxUsageMap;
    private Map<String, Double> customerMinUsageMap;
    private Map<String, Double> customerAvgUsageMap;
    private Double alpha = 0.3;
    private Integer uLength = 168;

    static Logger log = LogManager.getLogger(CustomerUsageInformation.class);

    public CustomerUsageInformation(){
        customerSubscriptionList = new HashMap<>();
        customerSubscriptionMap = new HashMap<>();
        customerActualUsageMap = new HashMap<>();
        customerUsageProjectionMap = new HashMap<>();
        customerUsageMap = new HashMap<>();
        customerBootUsageMap = new HashMap<>();
        customerMinUsageMap = new HashMap<>();
        customerMaxUsageMap = new HashMap<>();
        customerAvgUsageMap = new HashMap<>();
    }

    public CustomerUsageInformation(Double alpha, Integer uLength){
        customerSubscriptionList = new HashMap<>();
        customerSubscriptionMap = new HashMap<>();
        customerActualUsageMap = new HashMap<>();
        customerUsageProjectionMap = new HashMap<>();
        customerUsageMap = new HashMap<>();
        customerBootUsageMap = new HashMap<>();
        customerMinUsageMap = new HashMap<>();
        customerMaxUsageMap = new HashMap<>();
        customerAvgUsageMap = new HashMap<>();
        this.alpha = alpha;
        this.uLength = uLength;
    }

    public void setCustomerSubscriptionList(Integer timeslot, CustomerSubscriptionInformation customerSubscription){
        ArrayList<CustomerSubscriptionInformation> alist = customerSubscriptionList.get(timeslot);
        if (alist == null){
            alist = new ArrayList<>();
        }
        alist.add(customerSubscription);
        customerSubscriptionList.put(timeslot,alist);
    }

    public void setCustomerSubscriptionList(Integer timeslot, ArrayList<CustomerSubscriptionInformation> custList){
        customerSubscriptionList.put(timeslot, custList);
    }

    public ArrayList<CustomerSubscriptionInformation> getCustomerSubscriptionList(Integer timeslot){
        return customerSubscriptionList.get(timeslot);
    }

    public void setCustomerSubscriptionMap(String custName, Integer timeslot, CustomerSubscriptionInformation customerSubscription){
        Map<Integer, CustomerSubscriptionInformation> aMap = customerSubscriptionMap.get(custName);
        if (aMap == null){
            aMap = new HashMap<>();
        }
        aMap.put(timeslot, customerSubscription);
        customerSubscriptionMap.put(custName,aMap);
    }

    public void setCustomerSubscriptionMap(String custName, Map<Integer, CustomerSubscriptionInformation> custMap){
        customerSubscriptionMap.put(custName, custMap);
    }

    public CustomerSubscriptionInformation getCustomerSubscriptionMap(String custName, Integer timeslot){
        Map<Integer, CustomerSubscriptionInformation> aMap = customerSubscriptionMap.get(custName);
        return aMap.get(timeslot);
    }

    public Map<Integer, CustomerSubscriptionInformation> getCustomerSubscriptionMap(String custName){
        return customerSubscriptionMap.get(custName);
    }

    public Map<String, Map<Integer, CustomerSubscriptionInformation>> getCustomerSubscriptionMap()
    {
      return customerSubscriptionMap;
    }

    public void setCustomerActualUsage(String customerName, int timeslot, UsageRecord usage){
        Map<Integer, UsageRecord> usageMap = customerActualUsageMap.get(customerName);
        if(usageMap == null)
            usageMap = new HashMap<>();

        usageMap.put(timeslot, usage);
        customerActualUsageMap.put(customerName, usageMap);
    }

    public Map<String, Map<Integer, UsageRecord>> getCustomerAcutalUsageMap(){
        return customerActualUsageMap;
    }

    public Double getCustomerActualUsage(String customerName, int timeslot) {
        Map<Integer, UsageRecord> usageMap = customerActualUsageMap.get(customerName);
        if(usageMap != null)
        {
          UsageRecord usageRecord = usageMap.get(timeslot);
          return usageRecord.getConsumptionPerPopulation();
        }
        else
        {
          return null;
        }
    }

    public Integer getCustomerSubscription(String customerName, int timeslot) {
        Map<Integer, UsageRecord> usageMap = customerActualUsageMap.get(customerName);
        if(usageMap != null)
        {
          UsageRecord usageRecord = usageMap.get(timeslot);
          return usageRecord.getSubscribedPopulation();
        }
        else
        {
          return null;
        }
    }

    public Double getCustomerTariffSubscribed(String customerName, int timeslot) {
        Map<Integer, UsageRecord> usageMap = customerActualUsageMap.get(customerName);
        if(usageMap != null)
        {
          UsageRecord usageRecord = usageMap.get(timeslot);
          return usageRecord.getUnitTariff();
        }
        else
        {
          return null;
        }
    }

    public void setCustomerUsageProjectionMap(String customerName, int index,  Double usage){
        Double[] customerUsageProjection = customerUsageProjectionMap.get(customerName);

        if(customerUsageProjection == null){
            customerUsageProjection = new Double[uLength];
            for(int i=0; i<uLength; i++)
                customerUsageProjection[i] = 0.0;
            customerUsageProjection[index] = usage;
        }

        else{
            Double oldUsage =customerUsageProjection[index];
            if(oldUsage == 0.0)
                customerUsageProjection[index] = usage;
            else {
                Double newUsage = oldUsage * (1 - alpha) + alpha * usage;
                customerUsageProjection[index] = newUsage;
            }
        }
        customerUsageProjectionMap.put(customerName, customerUsageProjection);
    }

    public Double getCustomerUsageProjection(String customerName, int index){
        Double[] customerUsageProjection = customerUsageProjectionMap.get(customerName);

        if(customerUsageProjection == null) {
            log.warn(" No customer record is found ");
            return 0.0;
        }
        else{
            return customerUsageProjection[index];
        }
    }

   public void setCustomerBootUsageMap(String customerName, Vector<Double> usageMap){
        customerBootUsageMap.put(customerName, usageMap);
   }

    public Vector<Double> getCustomerBootUsageMap(String customerName){
        return customerBootUsageMap.get(customerName);
    }

    public Map<String, Vector<Double>> getCustomerBootUsageMap()
    {
      return customerBootUsageMap;
    }

    public void setCustomerUsageMap(String customerName, Integer timeslot, Double usage)
    {
         Double[] list = customerUsageMap.get(customerName);

         if(list == null)
         {
           list = new Double[2000];
           Arrays.fill(list, -1.0);
         }

         list[timeslot] = usage;

         customerUsageMap.put(customerName, list);
    }

    public Double[] getCustomerUsageMap(String customerName){
         return customerUsageMap.get(customerName);
    }

    public Map<String, Double[]> getCustomerUsageMap(){
         return customerUsageMap;
    }

    public void setCustomerMaxUsageMap(String customerName, Double maxValue){
        customerMaxUsageMap.put(customerName, maxValue);
    }

    public Double getCustomerMaxUsage(String customerName){
        return customerMaxUsageMap.get(customerName);
    }

    public void setCustomerMinUsageMap(String customerName, Double minValue){
        customerMinUsageMap.put(customerName, minValue);
    }

    public Double getCustomerMinUsage(String customerName){
        return customerMinUsageMap.get(customerName);
    }

    public void setCustomerAvgUsageMap(String customerName, Double avgValue){
        customerAvgUsageMap.put(customerName,avgValue);
    }

    public Double getCustomerAvgUsageMap(String customerName){
        return customerAvgUsageMap.get(customerName);
    }

    public void printCustomerSubscriptionList()
    {
        for(Map.Entry<Integer, ArrayList<CustomerSubscriptionInformation>> csl : customerSubscriptionList.entrySet())
        {
            System.out.println("Customer Name : " + csl.getKey());

            for(CustomerSubscriptionInformation csi : csl.getValue())
            {
              System.out.print(csi.getCustomerName() + " :: ");
            }
            System.out.println("\n");
        }
    }

    public void printCustomerMinUsage()
    {
        for(Map.Entry<String, Double> cmum : customerMinUsageMap.entrySet())
        {
            System.out.println("Customer : " + cmum.getKey() + " :: Min Usage : " + cmum.getValue());
        }
    }
}
