package org.powertac.samplebroker.information;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import javafx.util.Pair;
import org.powertac.common.TariffSpecification;

public class CustomerAndTariffInformation
{
  public class SubscriptionInfo
  {
    public Integer startTime;
    public Integer endTime;
    public int subscribedPopulation;

    public SubscriptionInfo(Integer startTime, Integer endTime, int subscribedPopulation)
    {
      this.startTime = startTime;
      this.endTime = endTime;
      this.subscribedPopulation = subscribedPopulation;
    }
  }

  public class TariffInfo
  {
    public Integer publishTime;

    public TariffInfo(Integer publishTime)
    {
      this.publishTime = publishTime;
    }
  }

  private Map<String, Map<TariffSpecification, SubscriptionInfo>> customerInformationMap;
  private Map<TariffSpecification, TariffInfo> tariffInformationMap;

  public CustomerAndTariffInformation()
  {
    customerInformationMap = new HashMap<>();
    tariffInformationMap = new HashMap<>();
  }

  public void setCustomerInformationMap(String customer, TariffSpecification spec, Integer startTime, Integer endTime, int subscribedPopulation)
  {
    Map<TariffSpecification, SubscriptionInfo> CIM = customerInformationMap.get(customer);

    if(CIM == null)
    {
      CIM = new HashMap<>();
    }

    CIM.put(spec, new SubscriptionInfo(startTime, endTime, subscribedPopulation));
    customerInformationMap.put(customer, CIM);
  }

  public Map<TariffSpecification, SubscriptionInfo> getCustomerInformationMap(String customer)
  {
    return customerInformationMap.get(customer);
  }

  public SubscriptionInfo getCustomerInformationMap(String customer, TariffSpecification spec)
  {
    return customerInformationMap.get(customer).get(spec);
  }

  public void setTariffInformationMap(TariffSpecification spec, Integer publishTime)
  {
    tariffInformationMap.put(spec, new TariffInfo(publishTime));
  }

  public TariffInfo getTariffInformationMap(TariffSpecification spec)
  {
    return tariffInformationMap.get(spec);
  }

  public Integer getTariffPublishTimeslot(TariffSpecification spec)
  {
    return tariffInformationMap.get(spec).publishTime;
  }
}
