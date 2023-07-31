package org.powertac.samplebroker.helpers;
import org.json.JSONArray;
import org.json.JSONObject;
import org.bson.Document;
import com.mongodb.client.MongoCollection;
import java.util.ArrayList;
import java.util.List;
import com.mongodb.MongoClient;

public class MarketManagerInformation {
    MongoClient mongoclient = new MongoClient( "localhost" , 27017 );
    JSONObject wlinfo;
    MongoCollection<Document> wlcollection;

    Double totalCost = 0.0;
    Double totalQuantity = 0.0;
    Double defaultPrice = -50.0;

    // ********************* Constructors *********************
    public MarketManagerInformation(String brokerName) {
        this.wlinfo = new JSONObject();
        this.wlcollection = mongoclient.getDatabase("PowerTAC21_"+brokerName).getCollection("Market_Manager_Information");
    }

    // ********************* Methods *********************
    private JSONArray get_jsonarray_with_zeros(Integer size) {
        JSONArray newarray = new JSONArray();
        for (int i = 0; i < size; i++) {
            newarray.put(i, 0.0);
        }
        return newarray;
    }

    private JSONObject make_timeslot_container(){
        JSONObject tsinfo = new JSONObject();
        tsinfo.put("bidding_broker_prices", get_jsonarray_with_zeros(24)); // bidding broker price
        tsinfo.put("bidding_broker_quantities", get_jsonarray_with_zeros(24)); // bidding broker quantity
        tsinfo.put("wholesale_broker_prices", get_jsonarray_with_zeros(24)); // wholesale broker price
        tsinfo.put("wholesale_broker_quantities", get_jsonarray_with_zeros(24)); // wholesale broker quantity
        tsinfo.put("wholesale_market_prices", get_jsonarray_with_zeros(24)); // wholesale market price
        tsinfo.put("wholesale_market_quantities", get_jsonarray_with_zeros(24)); // wholesale market quantity
        tsinfo.put("uncleared_bid_prices", get_jsonarray_with_zeros(24)); // new JSONObject() uncleared bid prices
        tsinfo.put("uncleared_bid_quantities", get_jsonarray_with_zeros(24)); // new JSONObject() uncleared bid quantities
        tsinfo.put("uncleared_ask_prices", get_jsonarray_with_zeros(24)); // new JSONObject() uncleared ask prices
        tsinfo.put("uncleared_ask_quantities", get_jsonarray_with_zeros(24)); // new JSONObject() uncleared ask quantities
        tsinfo.put("balancing_broker_price", 0.0); // balancing broker price
        tsinfo.put("balancing_broker_quantity", 0.0); // balancing broker quantity
        tsinfo.put("balancing_market_quantity", 0.0); // balancing market quantity
        tsinfo.put("powertac_consumption", 0.0); // powertac consumption
        tsinfo.put("powertac_production", 0.0); // powertac production
        return tsinfo;
    }

    public void set_bidding_broker_information(Integer timeslot, Integer proximity, Double bbp, Double bbq) {
        if (Math.abs(bbq) == 0.0)
            return;

        String timeslot_string = String.valueOf(timeslot);
        JSONObject tsinfo;
        if (wlinfo.has(timeslot_string)){
            tsinfo = wlinfo.getJSONObject(timeslot_string);
        }
        else{
            tsinfo = make_timeslot_container();
        }

        JSONArray bbqs = tsinfo.getJSONArray("bidding_broker_quantities");
        Double old_bbq = bbqs.getDouble(proximity-1);
        JSONArray bbps = tsinfo.getJSONArray("bidding_broker_prices");
        Double old_bbp = bbps.getDouble(proximity-1);
        if (bbp==null)
        {
            if (bbq>0.0)
                bbps.put(proximity-1, -999999.0);
            else
                bbps.put(proximity-1, 0.000001);
        }
        else if (bbp!=null)
        {
            if (old_bbp!=0.0 && bbp<old_bbp)
                bbps.put(proximity-1, bbp);
            else if (old_bbp==0.0)
               bbps.put(proximity-1, bbp);
        }
        bbqs.put(proximity-1, old_bbq + bbq);

        tsinfo.put("bidding_broker_prices", bbps);
        tsinfo.put("bidding_broker_quantities", bbqs);
        wlinfo.put(timeslot_string, tsinfo);
    }

    public void set_wholesale_broker_information(Integer timeslot, Integer proximity, Double wbp, Double wbq) {
        if (Math.abs(wbq) == 0.0)
            return;

        String timeslot_string = String.valueOf(timeslot);
        JSONObject tsinfo;
        if (wlinfo.has(timeslot_string)){
            tsinfo = wlinfo.getJSONObject(timeslot_string);
        }
        else{
            tsinfo = make_timeslot_container();
        }

        JSONArray wbps = tsinfo.getJSONArray("wholesale_broker_prices");
        JSONArray wbqs = tsinfo.getJSONArray("wholesale_broker_quantities");
        wbps.put(proximity-1, wbp);
        wbqs.put(proximity-1, wbqs.getDouble(proximity-1) + wbq);

        tsinfo.put("wholesale_broker_prices", wbps);
        tsinfo.put("wholesale_broker_quantities", wbqs);
        wlinfo.put(timeslot_string, tsinfo);
    }

    public void set_wholesale_market_information(Integer timeslot, Integer proximity, Double wmp, Double wmq) {
        String timeslot_string = String.valueOf(timeslot);
        JSONObject tsinfo;
        if (wlinfo.has(timeslot_string)){
            tsinfo = wlinfo.getJSONObject(timeslot_string);
        }
        else{
            tsinfo = make_timeslot_container();
        }

        JSONArray wmps = tsinfo.getJSONArray("wholesale_market_prices");
        JSONArray wmqs = tsinfo.getJSONArray("wholesale_market_quantities");
        wmps.put(proximity-1, wmp);
        wmqs.put(proximity-1, wmqs.getDouble(proximity-1) + wmq);

        tsinfo.put("wholesale_market_prices", wmps);
        tsinfo.put("wholesale_market_quantities", wmqs);
        wlinfo.put(timeslot_string, tsinfo);
    }

    public void set_orderbook_information(Integer timeslot, Integer proximity, Double price, Double quantity, String marketside){
        String timeslot_string = String.valueOf(timeslot);
        JSONObject tsinfo;
        if (wlinfo.has(timeslot_string)){
            tsinfo = wlinfo.getJSONObject(timeslot_string);
        }
        else{
            tsinfo = make_timeslot_container();
        }

        // FIRST VALID UNCLEARED ASK/BID
        JSONArray pbook = tsinfo.getJSONArray("uncleared_"+marketside+"_prices");            
        JSONArray qbook = tsinfo.getJSONArray("uncleared_"+marketside+"_quantities");
        if (price==null){
            if (quantity>0.0)
                pbook.put(proximity-1, -999999.0);
            else
                pbook.put(proximity-1, 0.000001);
        }
        else{
            pbook.put(proximity-1, price);
        }
        qbook.put(proximity-1, quantity);
        tsinfo.put("uncleared_"+marketside+"_prices", pbook);
        tsinfo.put("uncleared_"+marketside+"_quantities", qbook);
        wlinfo.put(timeslot_string, tsinfo);
    }

    public void set_balancing_broker_information(Integer timeslot, Double bbp, Double bbq) {
        String timeslot_string = String.valueOf(timeslot);
        JSONObject tsinfo;
        if (wlinfo.has(timeslot_string)){
            tsinfo = wlinfo.getJSONObject(timeslot_string);
        }
        else{
            tsinfo = make_timeslot_container();
        }

        tsinfo.put("balancing_broker_price", bbp);
        tsinfo.put("balancing_broker_quantity", tsinfo.getDouble("balancing_broker_quantity")+bbq);
        wlinfo.put(timeslot_string, tsinfo);
    }

    public void set_balancing_market_information(Integer timeslot, Double bmq) {
        String timeslot_string = String.valueOf(timeslot);
        JSONObject tsinfo;
        if (wlinfo.has(timeslot_string)){
            tsinfo = wlinfo.getJSONObject(timeslot_string);
        }
        else{
            tsinfo = make_timeslot_container();
        }

        tsinfo.put("balancing_market_quantity", tsinfo.getDouble("balancing_market_quantity")+bmq);
        wlinfo.put(timeslot_string, tsinfo);
    }

    public void set_powertac_energy_information(Integer timeslot, Double consumption, Double production) {
        String timeslot_string = String.valueOf(timeslot);
        JSONObject tsinfo;
        if (wlinfo.has(timeslot_string)){
            tsinfo = wlinfo.getJSONObject(timeslot_string);
        }
        else{
            tsinfo = make_timeslot_container();
        }
        tsinfo.put("powertac_consumption", tsinfo.getDouble("powertac_consumption") + consumption);
        tsinfo.put("powertac_production", tsinfo.getDouble("powertac_production") + production);
        wlinfo.put(timeslot_string, tsinfo);
    }

    public JSONObject get_structure(){
        return wlinfo;
    }

    public JSONArray get_bidding_broker_prices(Integer timeslot) {
        String timeslot_string = String.valueOf(timeslot);
        JSONArray bidding_broker_prices = wlinfo.getJSONObject(timeslot_string).getJSONArray("bidding_broker_prices");
        return bidding_broker_prices;
    }

    public JSONArray get_bidding_broker_quantities(Integer timeslot) {
        String timeslot_string = String.valueOf(timeslot);
        JSONArray bidding_broker_quantities = wlinfo.getJSONObject(timeslot_string).getJSONArray("bidding_broker_quantities");
        return bidding_broker_quantities;
    }

    public JSONArray get_wholesale_broker_prices(Integer timeslot) {
        String timeslot_string = String.valueOf(timeslot);
        JSONArray wholesale_broker_prices = wlinfo.getJSONObject(timeslot_string).getJSONArray("wholesale_broker_prices");
        return wholesale_broker_prices;
    }

    public JSONArray get_wholesale_broker_quantities(Integer timeslot) {
        String timeslot_string = String.valueOf(timeslot);
        JSONArray wholesale_broker_quantities = wlinfo.getJSONObject(timeslot_string).getJSONArray("wholesale_broker_quantities");
        return wholesale_broker_quantities;
    }

    public JSONArray get_wholesale_market_prices(Integer timeslot) {
        String timeslot_string = String.valueOf(timeslot);
        JSONArray wholesale_market_prices = wlinfo.getJSONObject(timeslot_string).getJSONArray("wholesale_market_prices");
        return wholesale_market_prices;
    }

    public JSONArray get_wholesale_market_quantities(Integer timeslot) {
        String timeslot_string = String.valueOf(timeslot);
        JSONArray wholesale_market_quantities = wlinfo.getJSONObject(timeslot_string).getJSONArray("wholesale_market_quantities");
        return wholesale_market_quantities;
    }

    public JSONArray get_uncleared_bid_prices(Integer timeslot){
        String timeslot_string = String.valueOf(timeslot);
        JSONArray uncleared_bid_prices = wlinfo.getJSONObject(timeslot_string).getJSONArray("uncleared_bid_prices");
        return uncleared_bid_prices;
    }

    public JSONArray get_uncleared_bid_quantities(Integer timeslot){
        String timeslot_string = String.valueOf(timeslot);
        JSONArray uncleared_bid_quantities = wlinfo.getJSONObject(timeslot_string).getJSONArray("uncleared_bid_quantities");
        return uncleared_bid_quantities;
    }

    public JSONArray get_uncleared_ask_prices(Integer timeslot){
        String timeslot_string = String.valueOf(timeslot);
        JSONArray uncleared_ask_prices = wlinfo.getJSONObject(timeslot_string).getJSONArray("uncleared_ask_prices");
        return uncleared_ask_prices;
    }

    public JSONArray get_uncleared_ask_quantities(Integer timeslot){
        String timeslot_string = String.valueOf(timeslot);
        JSONArray uncleared_ask_quantities = wlinfo.getJSONObject(timeslot_string).getJSONArray("uncleared_ask_quantities");
        return uncleared_ask_quantities;
    }

    public Double get_balancing_broker_price(Integer timeslot){
        String timeslot_string = String.valueOf(timeslot);
        Double bbp = wlinfo.getJSONObject(timeslot_string).getDouble("balancing_broker_price");
        return bbp;
    }

    public Double get_balancing_broker_quantity(Integer timeslot){
        String timeslot_string = String.valueOf(timeslot);
        Double bbq = wlinfo.getJSONObject(timeslot_string).getDouble("balancing_broker_quantity");
        return bbq;
    }

    public Double get_balancing_market_quantity(Integer timeslot){
        String timeslot_string = String.valueOf(timeslot);
        Double bmq = wlinfo.getJSONObject(timeslot_string).getDouble("balancing_market_quantity");
        return bmq;
    }

    public Double get_powertac_consumption(Integer timeslot){
        String timeslot_string = String.valueOf(timeslot);
        Double energy = wlinfo.getJSONObject(timeslot_string).getDouble("powertac_consumption");
        return energy;
    }

    public List<Double> get_powertac_consumption(){
        List<Double> consumption_book = new ArrayList<>();
        for (String timeslot_string : wlinfo.keySet()) {
            Double energy = wlinfo.getJSONObject(timeslot_string).getDouble("powertac_consumption");
            consumption_book.add(energy);            
        }
        return consumption_book;
    }

    public Double get_powertac_production(Integer timeslot){
        String timeslot_string = String.valueOf(timeslot);
        Double energy = wlinfo.getJSONObject(timeslot_string).getDouble("powertac_production");
        return energy;
    }

    public List<Double> get_powertac_production(){
        List<Double> production_book = new ArrayList<>();
        for (String timeslot_string : wlinfo.keySet()) {
            Double energy = wlinfo.getJSONObject(timeslot_string).getDouble("powertac_production");
            production_book.add(energy);            
        }
        return production_book;
    }

    public Double get_mean_wholesale_broker_price(Integer timeslot) {
        String timeslot_string = String.valueOf(timeslot);
        JSONArray wbps = wlinfo.getJSONObject(timeslot_string).getJSONArray("wholesale_broker_prices");
        JSONArray wbqs = wlinfo.getJSONObject(timeslot_string).getJSONArray("wholesale_broker_quantities");
        Double totalcost = 0.0;
        Double totalquantity = 0.0;
        for (int i = 0; i < 24; i++) {
            totalcost += wbps.getDouble(i) * Math.abs(wbqs.getDouble(i));
            totalquantity += Math.abs(wbqs.getDouble(i));
        }

        Double mean_wholesale_price = 0.0;
        if (totalquantity!=0.0)
            mean_wholesale_price = totalcost/totalquantity;
        return mean_wholesale_price;
    }

    public String available_timeslots() {
        return wlinfo.keySet().toString();
    }
}