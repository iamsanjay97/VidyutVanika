package org.powertac.samplebroker.util;

import java.util.Map;
import java.util.List;
import javafx.util.Pair;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import org.apache.commons.io.IOUtils;
import com.google.gson.Gson;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class JSON_API
{
	public static String communicateWithPython(String link, JSONObject[] data)
	{
		String responseString = "";
		HttpPost httpPost = new HttpPost(link);
		Gson gson = new Gson();

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
		catch(Exception e){}
		// System.out.println(" Response from python --  " + responseString);

		return responseString;
	}

	public static String communicateWithPython(String link, String customer, JSONObject[] dataframe, List<Double> listOfUsagePerPopulation)
	{
		String responseString = "";
		HttpPost httpPost = new HttpPost(link);
		Gson gson = new Gson();

		Map<String, JSONObject[]> data = new HashMap<>();

		JSONObject[] object = new JSONObject[1];

		JSONObject obj = new JSONObject();
		obj.put("customer", customer);
		obj.put("usages", listOfUsagePerPopulation);
		object[0] = obj;

		data.put("data", object);
		data.put("dataframe", dataframe);

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
		catch(Exception e){}
		//System.out.println(" Response from python --  " + responseString);

		return responseString;
	}

	// MCP, Net Imbalance and Customer Migration Predictions
	public static Pair<Double, Double> decodeJSON(String responseString)
 	{
	 	JSONParser parser = new JSONParser();
		JSONArray json = null;

		Pair<Double, Double> predictions = null;

		try
		{
			json = (JSONArray) parser.parse(responseString);
			predictions = new Pair<Double, Double> (Double.valueOf(json.get(0).toString()), Double.valueOf(json.get(1).toString()));

			// for(Object a: json)
			// {
			// 	String lps = String.valueOf(a);
			// 	JSONArray temp = (JSONArray) parser.parse(lps);

			// 	predictions.add(new Pair<Double, Double> (Double.valueOf(temp.get(0).toString()), Double.valueOf(temp.get(1).toString()))); 
			// }
		}
		catch(Exception e){}

		return predictions;
	}
}
