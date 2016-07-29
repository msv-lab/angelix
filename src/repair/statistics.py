import time
import json
from os.path import join

data = dict()

def init(working_dir):
    data['file'] = join(working_dir, 'statistics.json')
    data['time'] = dict()
    data['time']['testing'] = 0
    data['time']['compilation'] = 0
    data['time']['klee'] = 0
    data['time']['synthesis'] = 0
    data['time']['inference'] = 0
    data['time']['total'] = -1
    data['iterations'] = dict()
    data['iterations']['klee'] = []
    data['iterations']['synthesis'] = []
   
def save():
    with open(data['file'], 'w') as output_file:
        asserts = json.dump(data, output_file, indent=2)

