import numpy as np
import pandas as pd

from sklearn.preprocessing import MinMaxScaler, StandardScaler

class DataProcessor(object):

    def __init__(self):
        print("")

    def shift_n_lags(self, dataframe, lag, max_lag, features):
        dataframe = dataframe.shift(lag)[max_lag:]
        return dataframe[features]
    
    def shift_n_lags_all(self, dataframe, lag, max_lag):
        dataframe = dataframe.shift(lag)[max_lag:]
        return dataframe

    def onehot_encoder(self, dataframe, columns):                                # Perform OneHotEncoding on Categorical Features
    	df_encoded = pd.get_dummies(dataframe, columns = columns)
    	return df_encoded

    def shuffle_samples(self, dataframe):
    	dataframe = dataframe.sample(frac = 1, random_state = 1)
    	return dataframe

    def train_test_split(self, dataframe, train = 0.8):
    	train, test = np.split(dataframe, [int(train*len(dataframe))])
    	return train, test

    def train_validate_test_split(self, dataframe, train = 0.6, validate = 0.2, test = 0.2):
    	train, validate, test = np.split(dataframe, [int(train*len(dataframe)), int((train+validate)*len(dataframe))])
    	return train, validate, test

    def get_scaler(self):
        scaler = MinMaxScaler(feature_range = (0, 1))
        return scaler

    def normalize_minmax(self, scaler, dataframe, fit = True):
        if(fit):
    	       dataframe = pd.DataFrame(scaler.fit_transform(dataframe), index = dataframe.index, columns = dataframe.columns)
        else:
               dataframe = pd.DataFrame(scaler.transform(dataframe), index = dataframe.index, columns = dataframe.columns)

        return dataframe

    ''' ########### Method to Transform Output in Original State ########### '''

    def inverse_minmax(self, scaler, prediction):
    	dataframe = pd.DataFrame(scaler.inverse_transform(dataframe), index = dataframe.index, columns = dataframe.columns)
    	return dataframe

    ''' ################ Methods to Work with LSTM Model ################### '''

    def reshape_lstm_input(self, dataset, num_features, look_back = 24):
        
        dataset = dataset.reshape(1,look_back, num_features)
        return dataset

    def create_lstm_dataset(self, dataset, num_features, look_back = 24,look_ahead = 24):

        normalized_df_encoded_X = []
        normalized_df_encoded_Y = []

        for i in range(0, len(dataset)-look_back-look_ahead+1):
            t1 = []
            t2 = []

            for j in range(0,look_back):
                t1.append(dataset[[(i+j)], :])

            for j in range(0,look_ahead):
                t2.append(dataset[[(i+look_back+j)], num_features-1])

            normalized_df_encoded_X.append(t1)
            normalized_df_encoded_Y.append(t2)

        normalized_df_encoded_X = np.array(normalized_df_encoded_X)
        normalized_df_encoded_Y = np.array(normalized_df_encoded_Y)

        normalized_df_encoded_X = normalized_df_encoded_X.reshape(normalized_df_encoded_X.shape[0],look_back, num_features)
        normalized_df_encoded_Y = normalized_df_encoded_Y.reshape(normalized_df_encoded_Y.shape[0],look_ahead)

        return normalized_df_encoded_X, normalized_df_encoded_Y
