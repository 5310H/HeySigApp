#!/usr/bin/env python3
"""Development-only pinned-TLS watch relay. Default mode permits status/Stop only."""
import argparse
from contextlib import contextmanager
import hashlib
import hmac
import http.client
import json
import os
import re
from pathlib import Path
import secrets
import sqlite3
import ssl
import subprocess
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

READ_ONLY = {'status','current program','current mode','list programs','current volume',
             'current balance','current sharpness','current tin','current tinnitus'}
NOISE_DEVELOPMENT = {'noise status','read noise assignments','read noise settings',
                     'noise settings','open noise settings','configure noise','observe noise','start observe noise',
                     'noise sample','sample noise','stop noise','save noise settings','discard noise changes'}

def safe_noise_command(text):
    # Only the six-level editor/Observe vocabulary; never automatic playback.
    return text in NOISE_DEVELOPMENT or bool(re.fullmatch(
        r'assign program (?:[1-6]|one|two|three|four|five|six) to noise level (?:[1-6]|one|two|three|four|five|six)'
        r'|leave noise level (?:[1-6]|one|two|three|four|five|six) unused'
        r'|set noise (?:threshold (?:[1-5]|one|two|three|four|five)|margin|confirmations|cooldown|capture|wait|freshness) to [-+a-z0-9. ]{1,64}',text))

def response(phase, text):
    return {'phase': phase, 'text': text}

class Relay:
    def __init__(self, directory, allow_controls=False, forward=None, clock=time.monotonic):
        self.directory = Path(directory)
        self.config = json.loads((self.directory / 'pairing.json').read_text())
        self.allow_controls = allow_controls
        self.clock = clock
        self.nonces = {}
        self.lock = threading.Lock()
        self.forward = forward or self.forward_android
        self.db_path = self.directory / 'requests.sqlite'
        with self.db() as db:
            db.execute('CREATE TABLE IF NOT EXISTS requests (id TEXT PRIMARY KEY, digest TEXT, result TEXT, created REAL)')
            # In-flight records are intentionally uncertain after a restart; never replay.
            db.execute('DELETE FROM requests WHERE created < ?', (time.time() - 86400,))

    @contextmanager
    def db(self):
        connection = sqlite3.connect(self.db_path, timeout=5)
        try:
            yield connection
            connection.commit()
        except BaseException:
            connection.rollback(); raise
        finally:
            connection.close()

    def session(self):
        with self.lock:
            now = self.clock()
            self.nonces = {key: expiry for key, expiry in self.nonces.items() if expiry > now}
            if len(self.nonces) >= 128:
                raise ValueError('Too many active sessions')
            nonce = secrets.token_hex(16)
            self.nonces[nonce] = now + 15
            return {'nonce': nonce, 'expiresInSeconds': 15}

    def lookup(self, request_id):
        with self.db() as db:
            row = db.execute('SELECT result FROM requests WHERE id=?', (request_id,)).fetchone()
        return json.loads(row[0]) if row else response('UNKNOWN', 'No result recorded for that request')

    def command(self, data):
        request_id = str(uuid.UUID(data['id']))
        conversation = str(uuid.UUID(data['conversation']))
        text = data['text']
        alternatives = data.get('alternatives', [text])
        if not isinstance(text, str) or not 1 <= len(text) <= 512:
            raise ValueError('Command length invalid')
        if not isinstance(alternatives, list) or len(alternatives) > 5 or any(not isinstance(x,str) or len(x)>512 for x in alternatives):
            raise ValueError('Alternatives invalid')
        with self.lock:
            expiry = self.nonces.pop(data.get('nonce'), 0)
            if expiry <= self.clock():
                return response('BLOCKED', 'Request expired; speak a fresh command')
        body = {'id': request_id, 'conversation': conversation, 'text': text, 'alternatives': alternatives}
        digest = hashlib.sha256(json.dumps(body, sort_keys=True).encode()).hexdigest()
        with self.db() as db:
            db.execute('BEGIN IMMEDIATE')
            old = db.execute('SELECT digest,result FROM requests WHERE id=?',(request_id,)).fetchone()
            if old:
                return json.loads(old[1]) if hmac.compare_digest(old[0],digest) else response('BLOCKED','Request ID conflicts with an earlier command')
            count = db.execute('SELECT COUNT(*) FROM requests').fetchone()[0]
            if count >= 256:
                return response('BLOCKED','Development request log is full; review the local history before continuing')
            db.execute('INSERT INTO requests VALUES (?,?,?,?)',(request_id,digest,json.dumps(response('UNKNOWN','Request received; completion not yet known. Do not replay automatically')),time.time()))
        normalized = text.strip().lower().rstrip('.!?')
        if not self.allow_controls and normalized not in READ_ONLY | {'stop','cancel'} and not safe_noise_command(normalized):
            result = response('BLOCKED','Relay is in status-only mode. Aid controls are not enabled')
        else:
            if not self.allow_controls and normalized in READ_ONLY:
                body['text'] = 'status'; body['alternatives'] = ['status']
            try:
                result = self.forward(body)
                if not isinstance(result,dict) or not isinstance(result.get('text'),str) or not isinstance(result.get('phase'),str):
                    raise ValueError('Invalid app reply')
            except Exception:
                result = response('UNKNOWN','No final app response. Check status; do not resend automatically')
        with self.db() as db:
            db.execute('UPDATE requests SET result=? WHERE id=?',(json.dumps(result),request_id))
        return result

    def forward_android(self, body):
        connection = http.client.HTTPConnection('127.0.0.1',18765, timeout=28)
        try:
            connection.request('POST','/command',json.dumps(body),{'Content-Type':'application/json','Authorization':'Bearer '+self.config['token']})
            reply = connection.getresponse()
            raw = reply.read(8193)
            if reply.status != 200 or len(raw)>8192:
                raise ValueError('App bridge unavailable')
            return json.loads(raw)
        finally:
            connection.close()

class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args):
        pass  # Never log tokens, speech text or URLs containing request IDs.
    def setup(self):
        super().setup(); self.connection.settimeout(10)
    def reply(self, code, data):
        raw = json.dumps(data).encode()
        self.send_response(code)
        self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(raw)))
        self.send_header('Connection','close'); self.end_headers()
        self.wfile.write(raw); self.close_connection = True
    def authorized(self):
        expected = 'Bearer ' + self.server.relay.config['token']
        return hmac.compare_digest(self.headers.get('Authorization',''),expected)
    def do_GET(self):
        if not self.authorized(): return self.reply(401,response('BLOCKED','Authentication required'))
        try:
            if self.path == '/session': return self.reply(200,self.server.relay.session())
            if self.path.startswith('/result/'):
                return self.reply(200,self.server.relay.lookup(str(uuid.UUID(self.path.removeprefix('/result/')))))
            self.reply(404,response('FAILED','Unknown endpoint'))
        except Exception:
            self.reply(400,response('FAILED','Invalid request'))
    def do_POST(self):
        if not self.authorized(): return self.reply(401,response('BLOCKED','Authentication required'))
        try:
            if self.path != '/command': return self.reply(404,response('FAILED','Unknown endpoint'))
            if self.headers.get('Transfer-Encoding'): raise ValueError('Chunked request unsupported')
            size = int(self.headers.get('Content-Length','0'))
            if not 1 <= size <= 4096: raise ValueError('Body size invalid')
            raw = self.rfile.read(size)
            if len(raw) != size: raise ValueError('Incomplete body')
            self.reply(200,self.server.relay.command(json.loads(raw)))
        except Exception:
            self.reply(400,response('FAILED','Invalid request'))

class Server(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, relay):
        self.relay = relay
        self.slots = threading.Semaphore(8)
        super().__init__(address,Handler)
    def get_request(self):
        request, address = super().get_request()
        request.settimeout(5)
        return self.tls.wrap_socket(request,server_side=True,do_handshake_on_connect=False), address
    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False): request.close(); return
        try: super().process_request(request,address)
        except BaseException: self.slots.release(); raise
    def process_request_thread(self, request, address):
        try:
            request.do_handshake()
            super().process_request_thread(request,address)
        except (OSError, ssl.SSLError):
            self.shutdown_request(request)
        finally: self.slots.release()

def initialize(directory, host):
    directory.mkdir(parents=True,exist_ok=True); directory.chmod(0o700)
    config_path = directory / 'pairing.json'
    if not config_path.exists():
        subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-sha256','-days','30',
                        '-subj','/CN=HeySig Chromebook development','-keyout',str(directory/'key.pem'),'-out',str(directory/'cert.pem')],check=True,capture_output=True)
        pem = (directory/'cert.pem').read_text()
        fingerprint = hashlib.sha256(ssl.PEM_cert_to_DER_cert(pem)).hexdigest()
        config_path.write_text(json.dumps({'host':host,'port':8765,'fingerprint':fingerprint,'token':secrets.token_hex(32)},indent=2))
    else:
        config = json.loads(config_path.read_text()); config['host'] = host
        config_path.write_text(json.dumps(config,indent=2))
    for file in directory.iterdir():
        if file.is_file(): file.chmod(0o600)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory',type=Path,default=Path('private/watch-relay'))
    parser.add_argument('--host',required=True,help='Chromebook Wi-Fi IP, as reachable from watch')
    parser.add_argument('--bind',default='0.0.0.0')
    parser.add_argument('--allow-controls',action='store_true')
    parser.add_argument('--initialize-only',action='store_true')
    args = parser.parse_args()
    initialize(args.directory,args.host)
    if args.initialize_only:
        print('Private relay configuration created. No server started.'); return
    relay = Relay(args.directory,args.allow_controls)
    server = Server((args.bind,8765),relay)
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(args.directory/'cert.pem',args.directory/'key.pem')
    server.tls = tls
    print('HeySig TLS relay listening on port 8765. Mode: '+('aid controls enabled' if args.allow_controls else 'status only'),flush=True)
    try: server.serve_forever()
    finally: server.server_close()

if __name__ == '__main__': main()
