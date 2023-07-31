package org.powertac.samplebroker.information;

public class UsageRecord {

    int timeOfday;
    int dayOfWeek;
    int subscribedPopulation;
    double unitTariff;
    double consumptionPerPopulation;

    UsageRecord(){}

    public UsageRecord(int timeOfday, int dayOfWeek, int subscribedPopulation, double unitTariff, double consumptionPerPopulation){
        this.timeOfday = timeOfday;
        this.dayOfWeek = dayOfWeek;
        this.unitTariff = unitTariff;
        this.subscribedPopulation = subscribedPopulation;
        this.consumptionPerPopulation = consumptionPerPopulation;
    }

    public int getTimeOfDay(){
        return timeOfday;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public double getUnitTariff(){
        return unitTariff;
    }

    public int getSubscribedPopulation(){
        return subscribedPopulation;
    }

    public double getConsumptionPerPopulation(){
        return consumptionPerPopulation;
    }

    public String toString(){

        return Integer.toString(timeOfday) + ", " + Integer.toString(dayOfWeek) + ", " + Integer.toString(subscribedPopulation) + ", " + Double.toString(unitTariff) + ", " + Double.toString(consumptionPerPopulation);
    }

}
