from helper.read_mongo_collection import HelperToReadMongo
from helper.data_processing import DataProcessor
from helper.network_activities import Network

import numpy as np
import pandas as pd
from tensorflow.keras.models import load_model
import joblib

data_processor = DataProcessor()
network = Network()

storage_path = "models"

look_back = 168
look_ahead = 24
num_features = 3

def deploy_consumption_model(model, df):

    try:

        cols = get_feature_order('CONSUMPTION')
        dataframe = df[cols]

        scaler_storage_path = storage_path + '/scalers/net_consumption.save'
        scaler = joblib.load(scaler_storage_path)
        dataframe = data_processor.normalize_minmax(scaler, dataframe, fit = False)

        usage_per_population_index = list(dataframe.columns).index('Total_Consumption')

        normalized_df = dataframe[cols].values

        normalized_df_encoded = data_processor.reshape_lstm_input(normalized_df, num_features = num_features, look_back = look_back)
        predicted_value= model.predict(normalized_df_encoded)

        reverse_scaler = data_processor.get_scaler()
        reverse_scaler.min_, reverse_scaler.scale_ = scaler.min_[usage_per_population_index], scaler.scale_[usage_per_population_index]

        predicted_test_Y = np.squeeze(reverse_scaler.inverse_transform(predicted_value))

        return predicted_test_Y
    
    except Exception as e:
        print(e) 


def deploy_production_model(model, df):

    try:

        cols = get_feature_order('PRODUCTION')
        dataframe = df[cols]

        scaler_storage_path = storage_path + '/scalers/net_production.save'     
        scaler = joblib.load(scaler_storage_path)
        dataframe = data_processor.normalize_minmax(scaler, dataframe, fit = False)

        usage_per_population_index = list(dataframe.columns).index('Total_Production')

        normalized_df = dataframe[cols].values

        normalized_df_encoded = data_processor.reshape_lstm_input(normalized_df, num_features = num_features, look_back = look_back)
        predicted_value= model.predict(normalized_df_encoded)

        reverse_scaler = data_processor.get_scaler()
        reverse_scaler.min_, reverse_scaler.scale_ = scaler.min_[usage_per_population_index], scaler.scale_[usage_per_population_index]

        predicted_test_Y = np.squeeze(reverse_scaler.inverse_transform(predicted_value))

        return predicted_test_Y
    
    except Exception as e:
        print(e) 


def get_feature_order(mode):

    if mode == 'PRODUCTION':
        cols = ['Temperature', 'Wind_Speed', 'Total_Production']
    else:
        cols = ['Temperature', 'Wind_Speed', 'Total_Consumption']

    return cols


def load_models():

    loaded_models = dict()

    consumption_model = load_model(storage_path + '/Net_Consumption_Demand/best_model.hdf5')
    loaded_models.update({'CONSUMPTION' : consumption_model})

    production_model = load_model(storage_path + '/Net_Production_Demand/best_model.hdf5')
    loaded_models.update({'PRODUCTION' : production_model})

    return loaded_models


def test(loaded_models):

    listOfTemperature = [-20, -20, -21, -22, -23, -23, -23, -24, -23, -21, -19, -18, -18, -17, -17, -17, -18, -19, -20, -21, -23, -23, -24, -24]*7
    listOfWindSpeed = [20, 13, 17, 19, 19, 17, 17, 13, 13, 17, 26, 19, 22, 22, 24, 19, 20, 6, 13, 7, 6, 7, 9, 0]*7

    listOfTotalConsumption = [33178.79124321858, 34817.20771686343, 41122.79091167459, 39510.965343633216, 38259.977774573345, 39967.83539209642, 
                              41463.69498776223, 45362.71583332393, 56489.58114139792, 49950.90027557945, 58993.479397020776, 58249.123970422406, 
                              64589.484143745605, 72948.64326317722, 71226.61692777448, 75034.147282428, 74614.19020307076, 56290.49636414795, 
                              53326.21199160669, 48576.24855750293, 44745.795536738086, 44872.49090716095, 34410.26470249327, 38659.52265851355]*7

    listOfTotalProduction = [11465.72, 11739.22, 11724.49, 0, 8023.2, 4737.05, 12475.78, 41463.69498776223, 23016.08, 36615.31, 40907.42, 47746.42, 
                             41325.57, 52492.28, 43045.08, 38950.71, 40902.85, 24333.36, 12246.259, 12999.19, 12920.039, 11818.1454, 11703.33, 11464.349]*7

    dataframe = pd.DataFrame({'Temperature': listOfTemperature, 'Wind_Speed': listOfWindSpeed, 'Total_Consumption': listOfTotalConsumption, 'Total_Production':listOfTotalProduction})

    consumption_prediction = deploy_consumption_model(loaded_models['CONSUMPTION'], dataframe)
    production_prediction = deploy_production_model(loaded_models['PRODUCTION'], dataframe)

    print(consumption_prediction)
    print(production_prediction)

# loaded_models = load_models()
# test(loaded_models)