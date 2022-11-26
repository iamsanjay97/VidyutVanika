package org.powertac.samplebroker.util;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.JSONObject;

import org.apache.commons.io.IOUtils;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

import org.json.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class HelperForJSON
{
	// MCP Predictions
	public static ArrayList<Double> communicateMCP(String link,
			List<Integer> listofBiddingTimeslot, List<Integer> listOfBiddingMonthOfYear, List<Integer> listOfBiddingDayOfWeek, List<Integer> listOfBiddingDayOfMonth, List<Integer> listOfBiddingHourOfDay,
			List<Integer> listofExecutionTimeslot, List<Integer> listOfExecutionMonthOfYear, List<Integer> listOfExecutionDayOfWeek, List<Integer> listOfExecutionDayOfMonth, List<Integer> listOfExecutionHourOfDay,
			List<Double> listOfTemperature, List<Double> listOfCloudCover, List<Double> listOfWindSpeed, List<Double> listOfUnclearedAsk, List<Double> listOfUnclearedBid, List<Double> listofMCP,
			List<Double> listOfUnclearedAsk1, List<Double> listOfUnclearedBid1, List<Double> listofMCP1)
  {
	  String responseString = "";
		HttpPost httpPost = new HttpPost(link);
		Map<String, List<Object>> data = new HashMap<>();
		Gson gson = new Gson();

		if(link == null)
    {
			data.put("data", null);
		}
		else
    {
			List<Object> d = new ArrayList<>();

			d.add(listofBiddingTimeslot);
			d.add(listOfBiddingMonthOfYear);
			d.add(listOfBiddingDayOfWeek);
			d.add(listOfBiddingDayOfMonth);
			d.add(listOfBiddingHourOfDay);
			data.put("Biddind Date Info", d);

			d = new ArrayList<>();
			d.add(listofExecutionTimeslot);
			d.add(listOfExecutionMonthOfYear);
			d.add(listOfExecutionDayOfWeek);
			d.add(listOfExecutionDayOfMonth);
			d.add(listOfExecutionHourOfDay);
			data.put("Execution Date Info", d);

			d = new ArrayList<>();
			d.add(listOfTemperature);
			d.add(listOfCloudCover);
			d.add(listOfWindSpeed);
			data.put("Weather Info", d);

			d = new ArrayList<>();
			d.add(listOfUnclearedAsk);
			d.add(listOfUnclearedBid);
			d.add(listofMCP);
			d.add(listOfUnclearedAsk1);
			d.add(listOfUnclearedBid1);
			d.add(listofMCP1);
			data.put("Market Info", d);
		}

		try(DefaultHttpClient httpClient = new DefaultHttpClient())
		{
			StringEntity postingString = new StringEntity(gson.toJson(data));
			httpPost.setEntity(postingString);
			httpPost.setHeader("Content-type", "application/json");
			HttpResponse response = httpClient.execute(httpPost);
			//System.out.println(" Bundled the http post and sent to python ");
			if (response != null)
			{
				InputStream in = response.getEntity().getContent(); // Get the data in the entity
				responseString = IOUtils.toString(in, "UTF-8");
				in.close();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		//System.out.println(" Response from python --  " + responseString);

		JSONParser parser = new JSONParser();
		JSONArray json = null;

		ArrayList<Double> predictions = new ArrayList<>();

		try
		{
			 json = (JSONArray) parser.parse(responseString);

			 for(Object a : json)
				predictions.add(new Double(a.toString()));
		}
		catch(Exception e)
		{
		}

		return predictions;
	}

	// Net Imbalance Predictions
	public static ArrayList<Double> communicateNIP(String link,
			List<Integer> listofBiddingTimeslot, List<Integer> listOfBiddingMonthOfYear, List<Integer> listOfBiddingDayOfWeek, List<Integer> listOfBiddingDayOfMonth, List<Integer> listOfBiddingHourOfDay,
			List<Integer> listofExecutionTimeslot, List<Integer> listOfExecutionMonthOfYear, List<Integer> listOfExecutionDayOfWeek, List<Integer> listOfExecutionDayOfMonth, List<Integer> listOfExecutionHourOfDay,
			List<Integer> listOfNumberOfPlayers, List<Double> listOfTemperature, List<Double> listOfCloudCover, List<Double> listOfWindSpeed)
  {
	  String responseString = "";
		HttpPost httpPost = new HttpPost(link);
		Map<String, List<Object>> data = new HashMap<>();
		Gson gson = new Gson();

		if(link == null)
    {
			data.put("data", null);
		}
		else
    {
			List<Object> d = new ArrayList<>();

			d.add(listofBiddingTimeslot);
			d.add(listOfBiddingMonthOfYear);
			d.add(listOfBiddingDayOfWeek);
			d.add(listOfBiddingDayOfMonth);
			d.add(listOfBiddingHourOfDay);
			data.put("Biddind Date Info", d);

			d = new ArrayList<>();
			d.add(listofExecutionTimeslot);
			d.add(listOfExecutionMonthOfYear);
			d.add(listOfExecutionDayOfWeek);
			d.add(listOfExecutionDayOfMonth);
			d.add(listOfExecutionHourOfDay);
			data.put("Execution Date Info", d);

			d = new ArrayList<>();
			d.add(listOfNumberOfPlayers);
			d.add(listOfTemperature);
			d.add(listOfCloudCover);
			d.add(listOfWindSpeed);
			data.put("Market Info", d);
		}

		try(DefaultHttpClient httpClient = new DefaultHttpClient())
		{
			StringEntity postingString = new StringEntity(gson.toJson(data));
			httpPost.setEntity(postingString);
			httpPost.setHeader("Content-type", "application/json");
			HttpResponse response = httpClient.execute(httpPost);
			//System.out.println(" Bundled the http post and sent to python ");
			if (response != null)
			{
				InputStream in = response.getEntity().getContent(); // Get the data in the entity
				responseString = IOUtils.toString(in, "UTF-8");
				in.close();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		//System.out.println(" Response from python --  " + responseString);

		JSONParser parser = new JSONParser();
		JSONArray json = null;

		ArrayList<Double> predictions = new ArrayList<>();

		try
		{
			 json = (JSONArray) parser.parse(responseString);

			 for(Object a : json)
				predictions.add(new Double(a.toString()));
		}
		catch(Exception e)
		{
		}

		return predictions;
	}

	// Customer Usage Predictions
	public static ArrayList<Double> communicateCUP(String link, String _data1_,
			List<Integer> listOfDayOfMonth, List<Integer> listOfDayOfWeek, List<Integer> listOfHourOfDay, List<Double> listOfTemperature, List<Double> listOfWindSpeed,
			List<Double> listOfWindDirection, List<Double> listOfCloudCover, List<Double> listOfAvgUsage, List<Double> listOfMinUsage, List<Double> listOfMaxUsage,
			List<Double> listOfTariff, List<Double> listOfUsagePerPopulation, List<Double> listOfUsagePerPopulation1)
	{
		String responseString = "";
		HttpPost httpPost = new HttpPost(link);
		Map<String, List<Object>> data = new HashMap<>();
		Gson gson = new Gson();

		if(_data1_ == null)
		{
			data.put("data", null);
		}
		else
		{
			List<Object> d = new ArrayList<>();
			d.add(_data1_);
			data.put("data", d);

			d = new ArrayList<>();
			d.add(listOfDayOfMonth);
			d.add(listOfDayOfWeek);
			d.add(listOfHourOfDay);
			data.put("Calendar Info", d);

			d = new ArrayList<>();
			d.add(listOfTemperature);
			d.add(listOfWindSpeed);
			d.add(listOfWindDirection);
			d.add(listOfCloudCover);
			data.put("Weather Info", d);

			d = new ArrayList<>();
			d.add(listOfAvgUsage);
			d.add(listOfMinUsage);
			d.add(listOfMaxUsage);
			d.add(listOfTariff);
			d.add(listOfUsagePerPopulation);
			d.add(listOfUsagePerPopulation1);
			data.put("Tariff Info", d);
		}

		try(DefaultHttpClient httpClient = new DefaultHttpClient())
		{
			StringEntity postingString = new StringEntity(gson.toJson(data));
			httpPost.setEntity(postingString);
			httpPost.setHeader("Content-type", "application/json");
			HttpResponse response = httpClient.execute(httpPost);
			//System.out.println(" Bundled the http post and sent to python ");
			if (response != null)
			{
				InputStream in = response.getEntity().getContent(); // Get the data in the entity
				responseString = IOUtils.toString(in, "UTF-8");
				in.close();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		//System.out.println(" Response from python --  " + responseString);

		JSONParser parser = new JSONParser();
		JSONArray json = null;

		ArrayList<Double> predictions = new ArrayList<>();

		try
		{
			 json = (JSONArray) parser.parse(responseString);

			 for(Object a : json)
				predictions.add(new Double(a.toString()));
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		return predictions;
	}

	// Customer Migration Predictions
	public static ArrayList<Double> communicateCM(String link, String _data1_, List<Double> listOfCompareToBestTariffs)
	{
		String responseString = "";
		HttpPost httpPost = new HttpPost(link);
		Map<String, List<Object>> data = new HashMap<>();
		Gson gson = new Gson();

		if(_data1_ == null)
		{
			data.put("data", null);
		}
		else
		{
			List<Object> d = new ArrayList<>();
			d.add(_data1_);
			data.put("data", d);

			d = new ArrayList<>();
			d.add(listOfCompareToBestTariffs);
			data.put("Tariff Comparison Info", d);
		}

		try(DefaultHttpClient httpClient = new DefaultHttpClient())
		{
			StringEntity postingString = new StringEntity(gson.toJson(data));
			httpPost.setEntity(postingString);
			httpPost.setHeader("Content-type", "application/json");
			HttpResponse response = httpClient.execute(httpPost);
			//System.out.println(" Bundled the http post and sent to python ");
			if (response != null)
			{
				InputStream in = response.getEntity().getContent(); // Get the data in the entity
				responseString = IOUtils.toString(in, "UTF-8");
				in.close();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		//System.out.println(" Response from python --  " + responseString);

		JSONParser parser = new JSONParser();
		JSONArray json = null;

		ArrayList<Double> predictions = new ArrayList<>();

		try
		{
			 json = (JSONArray) parser.parse(responseString);

			 for(Object a : json)
				predictions.add(new Double(a.toString()));
		}
		catch(Exception e)
		{
		}

		return predictions;
	}

	public static List<String> decodeJSON(JSONObject json, String key, JSONParser parser)
  {
	  List <String> jsonOutput = new ArrayList<String>();
		try
        {
			String jsonString = json.get(key).toString();
			JSONObject jsonObject = (JSONObject) parser.parse(jsonString);
			String data__ = jsonObject.get("names").toString();
			jsonOutput = Arrays.asList(data__.split(","));
		}
        catch(Exception e)
        {
			System.out.println(e.toString());
		}
		return jsonOutput;
	}

	public static JSONObject extractJSON(JSONObject json, String key, JSONParser parser)
    {
		String report = json.get(key).toString();
		JSONObject jsonObject = null;
		try
        {
			jsonObject = (JSONObject) parser.parse(report);
			String data = jsonObject.get("data").toString();
			jsonObject = (JSONObject) parser.parse(data);
		}
        catch(Exception e)
        {
			System.out.println(e.toString());
		}
		return jsonObject;
	}
}
