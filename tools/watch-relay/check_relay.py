import json
from pathlib import Path
import tempfile
import threading
import unittest
import uuid
from relay import Relay

class RelayChecks(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.directory = Path(self.tmp.name)
        (self.directory/'pairing.json').write_text(json.dumps({'token':'a'*64}))
        self.calls = []
        self.now = 10.0
        def forward(body): self.calls.append(body); return {'phase':'FINAL','text':'test result'}
        self.relay = Relay(self.directory,False,forward,lambda:self.now)
    def tearDown(self): self.tmp.cleanup()
    def request(self,text='status',request_id=None):
        return {'id':request_id or str(uuid.uuid4()),'conversation':str(uuid.uuid4()),'text':text,'nonce':self.relay.session()['nonce']}
    def test_status_only_blocks_control(self):
        self.assertEqual(self.relay.command(self.request('program two'))['phase'],'BLOCKED')
        self.assertEqual(self.calls,[])
        self.assertEqual(self.relay.command(self.request('current program'))['phase'],'FINAL')
        self.assertEqual(self.calls[0]['text'],'status')
    def test_duplicate_and_conflicting_id(self):
        self.relay.allow_controls = True
        request = self.request('program two')
        self.relay.command(request)
        request['nonce'] = self.relay.session()['nonce']
        self.relay.command(request)
        self.assertEqual(len(self.calls),1)
        request['text'] = 'program one'; request['nonce'] = self.relay.session()['nonce']
        self.assertEqual(self.relay.command(request)['phase'],'BLOCKED')
        self.assertEqual(len(self.calls),1)
    def test_noise_observe_keeps_status_only_guard(self):
        for text in ('noise status','observe noise','assign program three to noise level five',
                     'set noise threshold one to minus sixty','save noise settings'):
            self.assertEqual(self.relay.command(self.request(text))['phase'],'FINAL')
            self.assertEqual(self.calls[-1]['text'],text)
        for text in ('automatic noise','start automatic noise','program three','volume five'):
            self.assertEqual(self.relay.command(self.request(text))['phase'],'BLOCKED')
    def test_expired_nonce_and_reuse(self):
        request = self.request(); self.now += 16
        self.assertEqual(self.relay.command(request)['phase'],'BLOCKED')
        self.assertEqual(self.calls,[])
        request = self.request(); self.relay.command(request)
        self.assertEqual(self.relay.command(request)['phase'],'BLOCKED')
    def test_timeout_is_not_replayed_after_restart(self):
        self.relay.allow_controls = True
        def failure(body): raise TimeoutError()
        self.relay.forward = failure
        request = self.request('program two')
        self.assertEqual(self.relay.command(request)['phase'],'UNKNOWN')
        self.relay = Relay(self.directory,True,lambda body: self.fail('must not replay'),lambda:self.now)
        request['nonce'] = self.relay.session()['nonce']
        self.assertEqual(self.relay.command(request)['phase'],'UNKNOWN')
    def test_invalid_and_concurrent(self):
        request = self.request(); request['text'] = 'x'*513
        with self.assertRaises(ValueError): self.relay.command(request)
        self.relay.allow_controls = True
        request = self.request('volume eight')
        copies = [dict(request,nonce=self.relay.session()['nonce']) for _ in range(4)]
        threads = [threading.Thread(target=lambda data=data:self.relay.command(data)) for data in copies]
        for thread in threads: thread.start()
        for thread in threads: thread.join()
        self.assertEqual(len(self.calls),1)

if __name__ == '__main__': unittest.main()
