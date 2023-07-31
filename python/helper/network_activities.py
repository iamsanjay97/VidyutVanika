from keras.optimizers import SGD, Adam
from keras.models import Sequential
from keras.callbacks import EarlyStopping, ModelCheckpoint
from keras.layers import Dense, LSTM, Dropout, Activation, LeakyReLU
from keras.constraints import UnitNorm
from sklearn.metrics import mean_squared_error

#from hyperopt import Trials, STATUS_OK, tpe
#from hyperas import optim
#from hyperas.distributions import choice, uniform

import numpy as np

class Network(object):

    def __init__(self):
        print("")

    def create_feed_forward_network_old(self, num_features, num_labels, num_layers = 3, num_cells = 64, dropout_rate = 0.4):
        model = Sequential()
        model.add(Dense(num_cells , input_dim = num_features , activation = 'relu'))
        model.add(Dense(num_cells, activation = 'sigmoid'))
        model.add(Dense(num_cells , activation = 'relu'))
        model.add(Dense(num_labels , activation = 'sigmoid'))
        model.compile(optimizer = 'adam' , loss = 'mse')
        return model

    def create_feed_forward_network(self, num_features, num_labels, num_layers = 3, num_cells = 64, dropout_rate = 0.4, reduction_rate = 0.6):
        model = Sequential()
        model.add( Dense(int(num_features*reduction_rate), kernel_constraint=UnitNorm(axis=0)) )
        model.add(Dense(num_cells , input_dim = num_features))
        model.add(LeakyReLU(alpha=0.1))
        model.add(Dense(num_cells))
        model.add(LeakyReLU(alpha=0.1))
        model.add(Dense(num_cells))
        model.add(LeakyReLU(alpha=0.1))
        model.add(Dense(num_labels , activation = 'linear'))
        model.compile(optimizer = 'adam' , loss = 'mse')
        return model

    def create_lstm_network(self, look_back, num_features, num_labels, num_layers = 3, num_cells = 32, dropout_rate = 0.4):
    	# Input Layer
    	model = Sequential()
    	model.add(LSTM(units = num_cells, return_sequences = True, input_shape = (look_back, num_features)))

    	# Other LSTM-layers
    	for _ in range(num_layers):
    		model.add(Dropout(dropout_rate))
    		model.add(LSTM(units = num_cells, return_sequences = True))

    		model.add(Dropout(dropout_rate))
    		model.add(LSTM(units = num_cells, return_sequences = False))

    	model.add(Dropout(dropout_rate))
    	model.add(Dense(num_labels))

    	model.compile(loss = 'mse', optimizer = 'adam')
    	return model

    def create_lstm_network_2(self, look_back, num_features, num_labels, num_layers = 3, num_cells = 32, dropout_rate = 0.4):

        model = Sequential()
        model.add(LSTM(units=num_cells, return_sequences= True, input_shape=(look_back,num_features)))
        model.add(LSTM(units=num_cells, return_sequences=True))
        model.add(LSTM(units=num_cells))
        model.add(Dense(units=num_labels))     # num_labels = look_ahead

        model.compile(optimizer='adam', loss='mean_squared_error')
        return model

    def train_network(self, model, train_X, train_Y, checkpoint_path, validation_split = 0.33, epochs = 200, batch_size = 32, patience = 20):
    	# Patience early stopping
    	es = EarlyStopping(monitor = 'val_loss', mode = 'min', verbose = 1, patience = patience)

    	# Checkpoint
    	filepath = checkpoint_path + "/model-{epoch:02d}-{val_loss:.2f}.hdf5"
    	Checkpoint = ModelCheckpoint(filepath, monitor = 'val_loss', verbose = 1, save_best_only = True, mode = 'min')

    	# Callback List
    	callbacks_list = [es, Checkpoint]

    	# Fit the Model
    	history = model.fit(train_X, train_Y, validation_split = validation_split, epochs = epochs, batch_size = batch_size, callbacks = callbacks_list, verbose = 1)

    	return model
