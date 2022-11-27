package org.powertac.samplebroker.information;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import javafx.util.Pair;
import org.powertac.common.TariffTransaction;

public class CustomerMigration
{
  public class MigrationInfo
  {
    public Double compareToBest;
    public int duration;
    public List<Pair<Integer, Integer>> migrations;
    public List<Integer> population;
    public int label;

    public MigrationInfo(Double compareToBest, int duration)
    {
      this.compareToBest = compareToBest;
      this.duration = duration;
      this.migrations = new ArrayList<>();
      this.population = new ArrayList<>();

      for(int i = 0; i < 24; i++)
        migrations.add(i, new Pair<Integer, Integer>(0, 0));

      for(int i = 0; i < 24; i++)
        population.add(i, 0);

      this.label = 0;
    }

    public void updateMigrationList(int proximity, int migrationCount)
    {
      this.migrations.set(proximity, new Pair<Integer, Integer>(1, migrationCount));

      if(migrationCount > 0)
        this.label = 1;
      else if(migrationCount < 0)
        this.label = -1;
    }

    public void updatePopulationList(int proximity, int population)
    {
      this.population.set(proximity, population);
    }

    public String getMigrations()
    {
      String S = "";

      for(Pair<Integer, Integer> item : this.migrations)
        S += "(" + item.getKey() + "::" + item.getValue() + "),";

      return S;
    }

    public String getPopulation()
    {
      String S = "";

      for(Integer item : this.population)
        S += item + ",";

      return S;
    }

    public String toString()
    {
      String S = "";

      S += "\nCompare to Best : " + this.compareToBest + " \nMigrations : ";

      for(Pair<Integer, Integer> item : this.migrations)
        S += "(" + item.getKey() + "," + item.getValue() + ") : ";

      S += "\nPopulation : ";

      for(Integer item : this.population)
        S += item + " : ";

      S += "\nLable : " + this.label;

      return S;
    }
  }

  private Map<String, SortedMap<Integer, MigrationInfo>> customerMigrationsMap;

  public CustomerMigration()
  {
    customerMigrationsMap = new HashMap<>();
  }

  public void setCustomerMigrationMap(String customer, Integer timeslot, double compareToBest, int duration)
  {
    SortedMap<Integer, MigrationInfo> MIM = customerMigrationsMap.get(customer);

    if(MIM == null)
    {
      MIM = new TreeMap<>();
    }

    if((MIM.size() == 0) || (Math.abs(MIM.get(MIM.lastKey()).compareToBest - compareToBest) > 0.0001))
    {
      MIM.put(timeslot, new MigrationInfo(compareToBest, duration));
    }

    customerMigrationsMap.put(customer, MIM);
  }

  public void updateCustomerMigrationMap(String customer, Integer timeslot, int population, TariffTransaction.Type txType)
  {
    SortedMap<Integer, MigrationInfo> MIM = customerMigrationsMap.get(customer);

    int index = MIM.lastKey();

    MigrationInfo MI = MIM.get(index);

    if(((timeslot-index) <= 23) && !(((timeslot-index) == 0) && (txType == TariffTransaction.Type.WITHDRAW)))
      MI.updateMigrationList((timeslot-index), population);

    customerMigrationsMap.put(customer, MIM);
  }

  public void updateCustomerPopulationMap(String customer, Integer timeslot, int population)
  {
    SortedMap<Integer, MigrationInfo> MIM = customerMigrationsMap.get(customer);

    int index = MIM.lastKey();

    MigrationInfo MI = MIM.get(index);

    if((timeslot-index) <= 23)
      MI.updatePopulationList((timeslot-index), population);

    customerMigrationsMap.put(customer, MIM);
  }

  public Map<String, SortedMap<Integer, MigrationInfo>> getCustomerMigrationMap()
  {
    return customerMigrationsMap;
  }

  public SortedMap<Integer, MigrationInfo> getCustomerMigrationMap(String customer)
  {
    return customerMigrationsMap.get(customer);
  }

  public MigrationInfo getCustomerMigrationMap(String customer, Integer timeslot)
  {
    return customerMigrationsMap.get(customer).get(timeslot);
  }

  public String toString()
  {
    String S = "";

    for(Map.Entry<String, SortedMap<Integer, MigrationInfo>> item : customerMigrationsMap.entrySet())
    {
      S += "\nCustomer : " + item.getKey();

      for(SortedMap.Entry<Integer, MigrationInfo> item1 : item.getValue().entrySet())
      {
          S += "\nTimeslot : " + item1.getKey() + "\nMigration Info : " + item1.getValue().toString();
      }
    }

    return S;
  }
}
