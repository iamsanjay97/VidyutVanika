from flask import Flask, render_template, request, url_for, jsonify
import os,sys,csv,itertools,datetime, subprocess
from flask.wrappers import Response
import pandas as pd
import json
import time
import numpy as np
from multiprocessing.pool import ThreadPool
from joblib import Parallel, delayed

import net_demand_prediction_deploy

from helper.read_mongo_collection import HelperToReadMongo
from helper.customer_info import CustomerInfo

helper_to_read_mongo = HelperToReadMongo()

app = Flask(__name__)
pool = ThreadPool(processes=8)

@app.route("/NDPredictionLSTM", methods=['POST'])
def NDPredictionLSTM():
    global predictions

    if request.method == 'POST':
        try:
            data = request.get_json()
            dataframe = pd.DataFrame()

            for item in data:
                if item is not None:
                    dataframe = dataframe.append(item, ignore_index=True)

            consumption_predictions = net_demand_prediction_deploy.deploy_consumption_model(loaded_models['CONSUMPTION'], dataframe)
            production_predictions = net_demand_prediction_deploy.deploy_production_model(loaded_models['PRODUCTION'], dataframe)

            consumption_resp = json.dumps([prediction.item() for prediction in consumption_predictions])
            production_resp = json.dumps([prediction.item() for prediction in production_predictions])

            dict = {"consumption": consumption_resp, "production": production_resp}
            response = json.dumps(dict)
            print(response)

        except Exception as e:
            print(e)

    return response


def test():

    # for customer in list_of_customers:
    #     try:
    #         predictions = customer_usage_prediction_deploy.test(customer, loaded_customer_models[customer])
    #     except Exception as e:
    #         print(e)

    try:
        net_demand_prediction_deploy.test(loaded_models)
    except Exception as e:
        print(e)


if __name__== '__main__':

    # list_of_customers = customer_info.get_all_customers()

    # print('Loading Customer Models ...')
    # loaded_customer_models = customer_usage_prediction_deploy.load_customer_models()

    print('Loading NDP Models ...')
    loaded_models = net_demand_prediction_deploy.load_models()

    # First dummy prediction
    test()

    app.run(host='localhost', debug=True, use_reloader=False)
