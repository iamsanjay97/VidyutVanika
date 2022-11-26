package org.powertac.samplebroker.information;

import org.powertac.common.enumerations.PowerType;

public class CustomerSubscriptionInformation {

    String customerName;
    PowerType powerType;
    int subscribedPopulation;
    int totalPopulation;

    /*
    Default Constructor
     */

    public CustomerSubscriptionInformation(){

    }

    public CustomerSubscriptionInformation(String name, PowerType pt, int subs, int pops){
        customerName = name;
        powerType = pt;
        subscribedPopulation = subs;
        totalPopulation = pops;
    }

    public void setCustomerName(String name){
        customerName = name;
    }

    public void setPowerType(PowerType pt){
        powerType = pt;
    }

    public void setSubscribedPopulation(int subs){
        subscribedPopulation = subs;
    }

    public void setTotalPopulation(int pops){
        totalPopulation = pops;
    }

    public String getCustomerName(){
        return customerName;
    }

    public PowerType getPowerType(){
        return powerType;
    }

    public int getSubscribedPopulation(){
        return subscribedPopulation;
    }

    public int getTotalPopulation(){
        return totalPopulation;
    }

}
