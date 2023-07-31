import numpy as np
import tensorflow.compat.v1 as tf
tf.disable_v2_behavior()

HIDDEN1_UNITS = 40
HIDDEN2_UNITS = 30
action_dim = 2

class ActorNetwork(object):
    def __init__(self, sess, state_size, action_size, BATCH_SIZE, TAU, LEARNING_RATE):
        self.sess = sess
        self.BATCH_SIZE = BATCH_SIZE
        self.TAU = TAU
        self.LEARNING_RATE = LEARNING_RATE

        tf.keras.backend.set_session(sess)

        #Now create the model
        self.model , self.weights, self.state = self.create_actor_network(state_size, action_size)
        self.target_model, self.target_weights, self.target_state = self.create_actor_network(state_size, action_size)
        self.action_gradient = tf.placeholder(tf.float32,[None, action_size])
        self.params_grad = tf.gradients(self.model.output, self.weights, -self.action_gradient)
        grads = zip(self.params_grad, self.weights)
        self.optimize = tf.train.AdamOptimizer(LEARNING_RATE).apply_gradients(grads)
        self.sess.run(tf.initialize_all_variables())

    def train(self, states, action_grads):
        self.sess.run(self.optimize, feed_dict={
            self.state: states,
            self.action_gradient: action_grads
        })

    def target_train(self):
        actor_weights = self.model.get_weights()
        actor_target_weights = self.target_model.get_weights()
        for i in range(len(actor_weights)):
            actor_target_weights[i] = self.TAU * actor_weights[i] + (1 - self.TAU)* actor_target_weights[i]
        self.target_model.set_weights(actor_target_weights)

    def create_actor_network(self, state_size,action_dim):

        S = tf.keras.layers.Input(shape=[state_size])
        h0 = tf.keras.layers.Dense(HIDDEN1_UNITS, activation='relu')(S)
        h1 = tf.keras.layers.Dense(HIDDEN2_UNITS, activation='relu')(h0)
        limitprice = tf.keras.layers.Dense(action_dim, activation='sigmoid')(h1)
        V = tf.keras.layers.concatenate([limitprice])
        model = tf.keras.Model(inputs=S,outputs=V)
        model.summary()
        return model, model.trainable_weights, S
