from flask import Flask, request
import pandas as pd
import json
import time
import numpy as np

from gym_powertac.ddpg import DDPG
from gym_powertac.powertac_wm import PowerTAC_WM

import tensorflow.compat.v1 as tf
tf.disable_v2_behavior()

config = tf.ConfigProto(
    device_count={'GPU': 1},
    intra_op_parallelism_threads=1,
    allow_soft_placement=True
)

config.gpu_options.allow_growth = True
config.gpu_options.per_process_gpu_memory_fraction = 0.6

session = tf.Session(config=config)

tf.keras.backend.set_session(session)

app = Flask(__name__)

ddpg = DDPG(session)

@app.route("/DDPGActionPicker", methods=['POST'])
def DDPGActionPicker():
    global resp

    if request.method == 'POST':
        try:
            data = request.get_json()
            states = list()

            # print(data)

            for item in data:
                if item is not None:

                    state = PowerTAC_WM(item['state']['proximity'], item['state']['balancingPrice'], item['state']['quantity'])
                    states.append(state)

            actions = ddpg.choose_Action(states)
            resp = json.dumps([json.dumps(action) for action in actions[0]])
            # print(resp)

        except Exception as e:
            print(e)

    return resp


@app.route("/DDPGUpdateReplayBuffer", methods=['POST'])
def DDPGUpdateReplayBuffer():         

    if request.method == 'POST':
        try:
            data = request.get_json()
            exeperiences = list()

            # print(data)

            for item in data:
                if item is not None:

                    state = PowerTAC_WM(item['state']['proximity'], item['state']['balancingPrice'], item['state']['quantity'])
                    next_state = PowerTAC_WM(item['next_state']['proximity'], item['next_state']['balancingPrice'], item['next_state']['quantity'])

                    exeperiences.append((state,item['action'],item['reward'],next_state,item['terminal']))

            ddpg.add_to_reply_buffer(exeperiences)
            # ddpg.train_ddpg_network()

        except Exception as e:
            print(e)

    return "Done"


@app.route("/DDPGTraining", methods=['POST'])
def DDPGTraining():         

    if request.method == 'POST':
        try:
            ddpg.train_ddpg_network()

        except Exception as e:
            print(e)

    return "Done"


@app.route("/SaveDDPGModels", methods=['POST'])
def SaveDDPGModels():

    if request.method == 'POST':
        try:
            ddpg.save_models()

        except Exception as e:
            print(e)

    return "Done"


if __name__== '__main__':   

    app.run(host='localhost', debug=True, use_reloader=False)
