import sys,os
from pymongo import MongoClient
from sshtunnel import SSHTunnelForwarder
import numpy as np
import pandas as pd
#import bsonnumpy

class HelperToReadMongo(object):

    def __init__(self):
        print("")

    def connect_to_remote_mongo(self, server_ip, ssh_username, ssh_password):
        MONGO_HOST = server_ip
        MONGO_USER = ssh_username
        MONGO_PASS = ssh_password

        server = SSHTunnelForwarder(
            MONGO_HOST,
            ssh_username=MONGO_USER,
            ssh_password=MONGO_PASS,
            remote_bind_address=('127.0.0.1', 27017)
        )

        return server

    def connect_to_mongo(self, host, port, db, username = None, password = None):
    	if username and password:
    		mongo_uri = 'mongodb://%s:%s@%s:%s/%s' % (username, password, host, port, db)
    		client = MongoClient(mongo_uri)
    	else:
    		client = MongoClient(host, port)

    	db = client[db]
    	return db

    def get_all_collections(self, db_name, url = 'localhost', port = 27017, username = None, password = None):
    	db = self.connect_to_mongo(host = url, port = port, username = username, password = password, db = db_name)
    	collections = db.list_collection_names()
    	return collections

    def get_collection(self, db_name, collection_name, url = 'localhost', port = 27017, username = None, password = None):
    	db = self.connect_to_mongo(host = url, port = port, username = username, password = password, db = db_name)
    	collection = db[collection_name]
    	return collection

    def query_to_mongo(self, db_name, collection_name, features = None, query = {}, url = 'localhost', port = 27017, username = None, password = None, no_id = True):              # Connect to Mongodb Server
    	# Connect to MongoDB
        db = self.connect_to_mongo(host = url, port = port, username = username, password = password, db = db_name)
        # Make a query to the specific DB and Collection
        cursor = db[collection_name].find(query)
        # Expand the cursor and construct the DataFrame
        df =  pd.DataFrame(list(cursor))
        # Delete the _id
        if no_id:
            del df['_id']
           
        if features == None:
            return df
        else:
            return df[features]

    def get_last_N_records(self, db_name, collection_name, features, url = 'localhost', port = 27017, username = None, password = None, N = 168, no_id = True):
        db = self.connect_to_mongo(host = url, port = port, username = username, password = password, db = db_name)
        cursor = db[collection_name].find().skip(db[collection_name].count() - N)
        df =  pd.DataFrame(list(cursor))

        if no_id:
            del df['_id']

        return df[features]

    def read_pandas_dataframe(self, filename):
    	dataframe = pd.read_csv(filename, engine = 'python')
    	return dataframe

    def write_pandas_dataframe(self, dataframe, filename):
    	dataframe.to_csv(filename, sep='\t')

    def convert_collection_to_dataframe(self, collection, dataTypes):                        # Convert output of Mongodb to pandas dataframe
    	ndarray = bsonnumpy.sequence_to_ndarray(collection.find_raw_batches(), dataTypes, collection.count())
    	dataframe = pd.DataFrame(ndarray)
    	return dataframe
