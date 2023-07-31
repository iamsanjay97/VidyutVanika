package org.powertac.samplebroker.wholesalemarket;

import java.util.Map;
import java.util.LinkedHashMap;
import javafx.util.Pair;

public class MDP_DDPG
{
  public class Exeperience
  {
    public MDP_DDPG_State state;
    public Pair<Double, Double> action;
    public Double reward;
    public MDP_DDPG_State nextState;
    public Integer terminal;

    public Exeperience()
    {
      this.state = null;
      this.action = new Pair<Double, Double> (null, null);
      this.reward = null;
      this.nextState = null;
      this.terminal = null;
    }

    public String toString()
    {   
      String S = "State -> " + this.state + "\nAction -> " + this.action.getKey() + this.action.getValue() + "\nReward -> " + this.reward + "\nNext State -> " + 
      this.nextState + "\nIs Terminal -> " + this.terminal;
      return S;
    }
  }

  private Map<Integer, Exeperience> exeperienceMap;

  public MDP_DDPG()
  {
    this.exeperienceMap = new LinkedHashMap<>();

    for(int i = 1; i <= 24; i++)
    {
      this.exeperienceMap.put(i, new Exeperience());
    }
  }

  public void setStateAction(Integer proximity, Double normProximity, Double balancingPrice, Double quantity, Pair<Double, Double> action)
  {
    Exeperience exe = this.exeperienceMap.get(proximity);
    exe.state = new MDP_DDPG_State(normProximity, balancingPrice, quantity);
    exe.action = action;
    this.exeperienceMap.put(proximity, exe);
  }

  public MDP_DDPG_State getState(Integer proximity)
  {
    Exeperience exe = this.exeperienceMap.get(proximity);

    return exe.state;
  }

  public void setRewardNextState(Double reward, Integer proximity, Double normProximity, Double balancingPrice, Double quantity, Integer terminal)
  {
    Exeperience exe = this.exeperienceMap.get(proximity+1);
    exe.reward = reward;
    exe.nextState = new MDP_DDPG_State(normProximity, balancingPrice, quantity);
    exe.terminal = terminal;
    this.exeperienceMap.put(proximity+1, exe);

    // System.out.println(exe.state + " :: " + exe.action + " :: " + exe.reward + " :: " + exe.nextState);
  }

  public void resetExperienceMap()
  {
    for(int i = 1; i <= 24; i++)
    {
      this.exeperienceMap.put(i, new Exeperience());
    }
  }

  public Map<Integer, Exeperience> getExperiences()
  {
    return this.exeperienceMap;
  }

  public void printExperience()
  {
    for(int i = 1; i <= 24; i++)
    {
      System.out.println(this.exeperienceMap.get(i) + "\n");
    }
  }
}
