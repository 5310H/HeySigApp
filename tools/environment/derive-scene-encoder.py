from pathlib import Path
import hashlib,struct
from ai_edge_litert import schema_py_generated as schema
from ai_edge_litert.interpreter import Interpreter
import numpy as np
import argparse
p=argparse.ArgumentParser(description='Expose the pinned encoder embedding without changing operators or weights.')
p.add_argument('--source',type=Path,default=Path('third_party/environment/yamnet-original.tflite'))
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
source=a.source.read_bytes()
assert hashlib.sha256(source).hexdigest()=='10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de'
model=schema.Model.GetRootAsModel(source,0)
assert model.SubgraphsLength()==1
graph=model.Subgraphs(0)
assert graph.OutputsLength()==1
matches=[i for i in range(graph.TensorsLength()) if graph.Tensors(i).Name()==b'tower0/network/layer28/reduce_mean']
assert len(matches)==1
index=matches[0];tensor=graph.Tensors(index)
assert list(tensor.ShapeAsNumpy())==[1,1,1,1024] and tensor.Type()==schema.TensorType.INT8
field=graph._tab.Pos+graph._tab.Offset(8)
patched=bytearray(source)
patched.extend(b'\0'*(-len(patched)%16));vector=len(patched)
patched.extend(struct.pack('<Iii',2,graph.Outputs(0),index));patched.extend(b'\0'*(-len(patched)%16))
struct.pack_into('<I',patched,field,vector-field)
path=a.output;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(patched)
assert hashlib.sha256(patched).hexdigest()=='b2fc8fc3ff9c960bde60b5fea71335d4cb2a35c4d1b011c3a80927fd64d8a2e4'
engines=[Interpreter(model_content=data,num_threads=1) for data in (source,bytes(patched))]
for engine in engines:engine.allocate_tensors()
for wave in (np.zeros(15600,dtype=np.float32),np.sin(np.arange(15600)*2*np.pi*440/16000).astype(np.float32),np.random.default_rng(17).uniform(-.1,.1,15600).astype(np.float32)):
 outputs=[]
 for engine in engines:
  engine.set_tensor(engine.get_input_details()[0]['index'],wave);engine.invoke();outputs.append(engine.get_tensor(engine.get_output_details()[0]['index']))
 assert np.max(np.abs(outputs[0]-outputs[1]))<=1/256
print('embedding index',index,'bytes',len(patched),'sha256',hashlib.sha256(patched).hexdigest())
print(engines[1].get_output_details())
