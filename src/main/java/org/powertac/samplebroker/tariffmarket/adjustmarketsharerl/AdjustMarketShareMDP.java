package org.powertac.samplebroker.tariffmarket.adjustmarketsharerl;

import java.util.Map;
import java.util.Random;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import java.io.BufferedReader;

public class AdjustMarketShareMDP
{
  public AdjustMarketShareState state;
  public AdjustMarketShareAction action;

  public Integer stateSpaceSize;
  public Integer actionSpaceSize;
  public double[][] qTable;
  public double[][] qTableCount;
  public double gamma;
  public String gameName;
  public Random random;

  public Integer transitionNum;
  public final Map<Integer, Double> OPTIMAL_MARKET_SHARE = Map.of(
      1, 1.0,
      2, 0.60,
      3,0.48,
      4, 0.48,
      5, 0.38,
      6, 0.38,
      7, 0.30,
      8, 0.30,
      9, 0.25,
      10, 0.20
  );

  public String path;
  public File mdpfolder;

  public AdjustMarketShareMDP(String gameName, int numberOfBrokers)
  {
    this.state = new AdjustMarketShareState();
    this.action = new AdjustMarketShareAction();

    this.stateSpaceSize = this.state.getSize();
    this.actionSpaceSize = this.action.getSize();
    this.gamma = 0.01;              // Discount factor
    random = new Random();

    this.gameName = String.valueOf(gameName);   
    set_path(numberOfBrokers);
  }

  public void set_path(Integer numberOfBrokers) 
  {
    if(numberOfBrokers <= 2)
      path = "tariff_mab_exp3_v1.0/two_player/";
    else if(numberOfBrokers <= 4)
      path = "tariff_mab_exp3_v1.0/three_player/";
    else
      path = "tariff_mab_exp3_v1.0/five_player/";

    mdpfolder = new File(path);
    if (!mdpfolder.exists()){
      mdpfolder.mkdir();
    }

    System.out.println("Reading Q-Table from: " + path);

    this.qTable = loadQTable();
    this.qTableCount = loadQTableCount();
    this.transitionNum = loadTransitionNumber();

    // for(int i = 0; i < stateSpaceSize; i ++)
    // {
    //   for(int j = 0; j < actionSpaceSize; j++)
    //   {
    //     System.out.print(qTable[i][j] + ", ");
    //   }
    //   System.out.println();
    // }
  }

  public Double getLowerOptimalMarketshareUsingNumBrokers(Integer playerCount) {
    return 0.3 * OPTIMAL_MARKET_SHARE.get(playerCount);
  }

  public Double getMiddleOptimalMarketshareUsingNumBrokers(Integer playerCount) {
    return 0.7 * OPTIMAL_MARKET_SHARE.get(playerCount);
  }

  public Double getHigherOptimalMarketshareUsingNumBrokers(Integer playerCount) {
    return OPTIMAL_MARKET_SHARE.get(playerCount);
  }

  private void updateFactors() {
    transitionNum += 1;
  }

  private Integer loadTransitionNumber() 
  {
    Integer readTransitionNum = 0;

    if (new File(path + "mdpTransitionNumber.txt").canRead()) 
    {
      try 
      {
        File file = new File(path + "mdpTransitionNumber.txt");   
        BufferedReader br = new BufferedReader(new FileReader(file)); 
        readTransitionNum = Integer.parseInt(br.readLine());
        br.close();
      } 
      catch (Exception e) 
      {
        System.out.println("Error in loading transition number");
      }
    } 

    return readTransitionNum;
  }

  private double[][] loadQTable() 
  {
    double[][] loadQTable = new double[stateSpaceSize][actionSpaceSize];

    try 
    {  
      CSVReader reader = new CSVReader(new FileReader(path + "mdpTable.csv"));
      String[] data;

      while ((data = reader.readNext()) != null) 
      {
        Integer state = Integer.valueOf(data[0]);
        Integer action = Integer.valueOf(data[1]);
        double qvalue = Double.valueOf(data[2]);
        loadQTable[state][action] = qvalue;
      }
    } 
    catch (Exception e) 
    {
      System.out.println("Error in loading qtable");
      for (int i = 0; i < stateSpaceSize; i++) {
        for (int j = 0; j < actionSpaceSize; j++) {
          loadQTable[i][j] = 1.0;
        }
      }
    }

    return loadQTable;
  }

  private double[][] loadQTableCount() 
  {
    double[][] loadQTableCount = new double[stateSpaceSize][actionSpaceSize];

    try 
    {  
      CSVReader reader = new CSVReader(new FileReader(path + "mdpTableCount.csv"));
      String[] data;

      while ((data = reader.readNext()) != null) 
      {
        Integer state = Integer.valueOf(data[0]);
        Integer action = Integer.valueOf(data[1]);
        double qvalue = Double.valueOf(data[2]);
        loadQTableCount[state][action] = qvalue;
      }
    } 
    catch (Exception e) 
    {
      System.out.println("Error in loading qtable");
      for (int i = 0; i < stateSpaceSize; i++) {
        for (int j = 0; j < actionSpaceSize; j++) {
          loadQTableCount[i][j] = 0.0;
        }
      }
    }

    return loadQTableCount;
  }

  public void setState(double optimalMarketShare, double currentMarketShare)
  {
    this.state.setState(optimalMarketShare, currentMarketShare);
    System.out.println("Current State is " + this.state.getStateIndex());
  }

  public Double[] takeAction(double currentRateValue, double cheapestOpponentTariff) 
  {
    Integer actionIndex = 0;
    double totalWeight = 0.0;

    for(int i = 0; i < actionSpaceSize; i++)
      totalWeight += qTable[this.state.getStateIndex()][i];

    double[] prob = new double[this.actionSpaceSize];

    for(int i = 0; i < actionSpaceSize; i++)
      prob[i] = (1 - this.gamma) * qTable[this.state.getStateIndex()][i]/ totalWeight + this.gamma / this.actionSpaceSize;

    double [] cdf = prob.clone();
    for (int i = 1; i < cdf.length; i++)
        cdf[i] += cdf[i - 1];

    double currentRand = random.nextDouble();

    for(int i = 0; i < cdf.length; i++)
    {
      if(currentRand <= cdf[i])
      {
        actionIndex = i;
        break;
      }
    }

    Double[] newRateValue = this.action.setAction(actionIndex, currentRateValue, cheapestOpponentTariff);
    System.out.println("Action Index: " + actionIndex + " :: New Rate Value: " + newRateValue[0] + ", " + newRateValue[1]);
    return newRateValue;
  }

  public double getReward(double optimalMarketShare, double currentMarketShare)
  {
    double relativeMarketShare = Math.abs(optimalMarketShare - currentMarketShare);
    double newReward = 0.0;

    if(relativeMarketShare <= (optimalMarketShare*0.1))
      newReward = 1.0;
    else if(relativeMarketShare <= (optimalMarketShare*0.4))
      newReward = 0.50;
    else if(relativeMarketShare <= (optimalMarketShare*0.7))
      newReward = 0.25;
    else
      newReward = 0.0;

    return newReward;
  }

  public void updateQTable(double optimalMarketShare, double currentMarketShare)
  {
    double reward = getReward(optimalMarketShare, currentMarketShare);

    double totalWeight = 0.0;

    for(int i = 0; i < actionSpaceSize; i++)
      totalWeight += qTable[this.state.getStateIndex()][i];

    double prob = (1 - this.gamma) * qTable[this.state.getStateIndex()][this.action.getActionIndex()]/ totalWeight + this.gamma / this.actionSpaceSize;

    for(int i = 0; i < actionSpaceSize; i++)
    {
      double rewardHat;
      if(i == this.action.getActionIndex())
        rewardHat = reward / prob;
      else
        rewardHat = 0.0;

      qTable[this.state.getStateIndex()][i] *= Math.exp(this.gamma*rewardHat/this.actionSpaceSize);
    }

    this.qTableCount[this.state.getStateIndex()][this.action.getActionIndex()]++;

    for(int i = 0; i < this.stateSpaceSize; i++)
    {
      for(int j = 0; j < this.actionSpaceSize; j++)
      {
        System.out.print(this.qTable[i][j] + " ");
      }
      System.out.println();
    }

    updateFactors();
  }

  public Integer getState()
  {
    return this.state.getStateIndex();
  }

  public Integer getAction()
  {
    return this.action.getActionIndex();
  }

  public void resetMDP()
  {
    this.state = new AdjustMarketShareState();
    this.action = new AdjustMarketShareAction();
  }

  public double getQTableEntry(Integer state, Integer action)
  {
    return this.qTable[state][action];
  }

  public double[][] getQTable()
  {
    return this.qTable;
  }

  public String toString()
  {   
    String S = "State -> " + this.state + "\nAction -> " + this.action;
    return S;
  }

  public void saveMDP() 
  {
    System.out.println("SaveMDP called");
    saveTransitionNumber();
    saveQTable(this.transitionNum);
  }

  private void saveTransitionNumber() 
  {
    try 
    {
      FileWriter fWriter = new FileWriter(path + "mdpTransitionNumber.txt");
      fWriter.write(transitionNum.toString());
      fWriter.close();
    }
    catch (Exception e) 
    {
      System.out.print(e.getMessage());
    } 
  }

  private void saveQTable(Integer transitionNumber) 
  {
    try 
    { 
      CSVWriter writer = new CSVWriter(new FileWriter(path + "mdpTable.csv"));

      for (int i = 0; i < stateSpaceSize; i++) 
      {
        for (int j = 0; j < actionSpaceSize; j++) 
        {
          String[] data = {String.valueOf(i), String.valueOf(j), String.valueOf(qTable[i][j])};
          writer.writeNext(data);
        }
      }
      writer.flush();
      writer.close();

      if(transitionNumber % 500 == 0)
      {
        String fileName = path + "mdpTable_" + transitionNumber + ".csv";
        CSVWriter writer1 = new CSVWriter(new FileWriter(fileName));

        for (int i = 0; i < stateSpaceSize; i++) 
        {
          for (int j = 0; j < actionSpaceSize; j++) 
          {
            String[] data = {String.valueOf(i), String.valueOf(j), String.valueOf(qTable[i][j])};
            writer1.writeNext(data);
          }
        }
        writer1.flush();
        writer1.close();
      }
    } 
    catch (Exception e) 
    {
      System.out.println("Error in saving qtable");
    }

    try 
    {  
      CSVWriter writer = new CSVWriter(new FileWriter(path + "mdpTableCount.csv"));

      for (int i = 0; i < stateSpaceSize; i++) 
      {
        for (int j = 0; j < actionSpaceSize; j++) 
        {
          String[] data = {String.valueOf(i), String.valueOf(j), String.valueOf(qTableCount[i][j])};
          writer.writeNext(data);
        }
      }
      writer.flush();
      writer.close();

      if(transitionNumber % 500 == 0)
      {
        String fileName = path + "mdpTableCount_" + transitionNumber + ".csv";
        CSVWriter writer1 = new CSVWriter(new FileWriter(fileName));

        for (int i = 0; i < stateSpaceSize; i++) 
        {
          for (int j = 0; j < actionSpaceSize; j++) 
          {
            String[] data = {String.valueOf(i), String.valueOf(j), String.valueOf(qTableCount[i][j])};
            writer1.writeNext(data);
          }
        }
        writer1.flush();
        writer1.close();
      }
    } 
    catch (Exception e) 
    {
      System.out.println("Error in saving qtable");
    }
  }
}