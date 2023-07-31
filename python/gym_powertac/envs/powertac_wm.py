import gym, datetime
import collections as col
import numpy as np

class PowerTAC_WM(gym.Env):

  metadata = {'render.modes': ['human']}

  def __init__(self,proximity=None, quantity=None, ratio=None, limitprices=None):

    proximity_ohe = np.zeros(24)
    proximity_ohe[proximity-1] = 1

    self.proximity = proximity_ohe           # One-Hot Encoded Proximity

    self.quantity = quantity
    self.ratio = ratio

    self.limitprices = limitprices           # Limitprices for all 24 Proximities


  def step(self, action):

      '''   '''

  def reset(self):

      self.proximity = [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1]           # One-Hot Encoded Proximity
      self.quantity = 40.0
      self.ratio = 1.0
      self.limitprices = np.random.uniform(low=30.0, high=100.0, size=(24,))
      self.observation = self.make_observaton()
      return self.observation


  def make_observaton(self):

      names = ['proximity', 'quantity', 'ratio', 'limitprices']
      Observation = col.namedtuple('Observaion', names)
      return Observation(proximity=self.proximity, quantity=self.quantity, ratio=self.ratio, limitprices=self.limitprices)


  def render(self, mode='human', close=False):

      print('Proximity : ', self.proximity)
      print('Required Quantity : ', self.quantity)
      print('Ratio of Purchased Quantity : ', self.ratio)
      print('Predicted Limitprices : ', self.limitprices)
