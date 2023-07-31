package org.powertac.samplebroker.messages;

import java.util.LinkedHashMap;
import java.util.Map;
import org.powertac.common.WeatherReport;
import org.powertac.common.WeatherForecast;

public class WeatherInformation {

    private Map<Integer, WeatherForecast> forecastedWeather;
    private Map<Integer, WeatherReport> actualWeather;

    public WeatherInformation()
    {
        forecastedWeather = new LinkedHashMap<>();
        actualWeather = new LinkedHashMap<>();
    }

    public void setWeatherForecast(Integer timeslot,  WeatherForecast weatherforecast) {
        forecastedWeather.put(timeslot, weatherforecast);
    }

    public WeatherForecast getWeatherForecast(Integer timeslot){
        return forecastedWeather.get(timeslot);
    }

    public void setWeatherReport(Integer timeslot, WeatherReport weatherreport) {
        actualWeather.put(timeslot, weatherreport);
        //System.out.println(" size of weather map " + actualWeather.size() + " Keys " + actualWeather.keySet());

    }

    public WeatherReport getWeatherReport(Integer timeslot) {
        return actualWeather.get(timeslot);
    }

    public Map<Integer, WeatherReport> getWeatherReport(){
        return actualWeather;
    }

    /**
     * @to String method
     */

    @Override
    public String toString()
    {
      String str = "\nWeather Information\n";

      for (Map.Entry<Integer, WeatherForecast> entry : forecastedWeather.entrySet())
      {
        str += "Message Timeslot : " + entry.getKey() + ", Forecast " + entry.getValue() + "\n";
      }

      for (Map.Entry<Integer, WeatherReport> entry : actualWeather.entrySet())
      {
        str += "Message Timeslot : " + entry.getKey() + ", Actual " + entry.getValue() + "\n";
      }

      return str;
    }
}
