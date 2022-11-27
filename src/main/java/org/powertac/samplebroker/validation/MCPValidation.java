package org.powertac.samplebroker.validation;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class MCPValidation
{
  public class CollectMCP
  {
    public Double actual;
    public Double predictions;

    public CollectMCP()
    {
      this.actual = null;
      this.predictions = null;
    }

    public void updateActualMCP(Double mcp)
    {
      this.actual = mcp;
    }

    public void updatePredictions(Double mcp)
    {
      this.predictions = mcp;
    }
  }

  private Map<Integer, Map<Integer, CollectMCP>> MCPValidationMap;

  public MCPValidation()
  {
    MCPValidationMap = new LinkedHashMap<>();
  }

  public void updateMCPMap(Integer currentTimeslot, Integer futureTimeslot, Double mcp, boolean flag)          // flag is true for actualUsage updation
  {
    Map<Integer, CollectMCP> CUVM = MCPValidationMap.get(currentTimeslot);

    if(CUVM == null)
    {
      CUVM = new LinkedHashMap<>();
    }

    CollectMCP CU = CUVM.get(futureTimeslot);

    if(CU == null)
    {
      CU = new CollectMCP();
    }

    if(flag)
      CU.updateActualMCP(mcp);
    else
      CU.updatePredictions(mcp);

    CUVM.put(futureTimeslot, CU);
    MCPValidationMap.put(currentTimeslot, CUVM);
  }

  public void printToFile(String bootFile, ArrayList<String> brokers)
  {
    try
    {
      FileWriter fr = new FileWriter(new File("MCP_Validation.csv"), true);
      BufferedWriter br = new BufferedWriter(fr);

      br.write("\n\n" + bootFile + "\n" + brokers + "\n");

      for(Map.Entry<Integer, Map<Integer, CollectMCP>> outerItem : MCPValidationMap.entrySet())
      {
        Integer currentTimeslot = outerItem.getKey();
        Map<Integer, CollectMCP> oItems = outerItem.getValue();

        for(Map.Entry<Integer, CollectMCP> innerItem : oItems.entrySet())
        {
          Integer futureTimeslot = innerItem.getKey();
          CollectMCP mcpData = innerItem.getValue();

          br.write(currentTimeslot + "," + futureTimeslot + "," + mcpData.predictions + "," + mcpData.actual + "\n");
        }
      }

      br.close();
      fr.close();
    }
    catch(Exception e)
    {
      e.printStackTrace();
    }
  }
}
