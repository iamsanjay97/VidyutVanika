package org.powertac.samplebroker.wholesalemarket;

public class MDP_DDPG_State
{
  public Double proximity;
  public Double balancingPrice;
  public Double quantity;

  public MDP_DDPG_State(Double proximity, Double balancingPrice, Double quantity)
  {
    this.proximity = proximity;
    this.balancingPrice = balancingPrice;
    this.quantity = quantity;
  }

  public String toString()
  {
    String S = "<" + this.proximity + "," + this.balancingPrice + "," + this.quantity + ">";
    return S;
  }
}
