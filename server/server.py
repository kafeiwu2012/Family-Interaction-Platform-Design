#!/usr/bin/env python3
"""Single-device authenticated capture receiver. Python 3.7+, no dependencies."""
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(os.environ.get('CAPTURE_DATA', './data')).resolve()
DEVICE_TOKEN = os.environ.get('DEVICE_TOKEN', '')
ADMIN_TOKEN = os.environ.get('ADMIN_TOKEN', '')
MAX_UPLOAD = 16 * 1024 * 1024

def db():
    con = sqlite3.connect(str(ROOT / 'state.db'), timeout=15)
    return con

def initialize():
    ROOT.mkdir(parents=True, exist_ok=True)
    (ROOT / 'photos').mkdir(exist_ok=True)
    with db() as con:
        con.execute('CREATE TABLE IF NOT EXISTS state (key TEXT PRIMARY KEY, value TEXT NOT NULL)')
        con.execute('CREATE TABLE IF NOT EXISTS uploads (name TEXT PRIMARY KEY, sha TEXT NOT NULL, size INTEGER NOT NULL)')
        con.execute('INSERT OR IGNORE INTO state VALUES (?,?)', ('config', json.dumps({'camera':'back','enabled':True,'interval':60})))

def load(key):
    with db() as con:
        row = con.execute('SELECT value FROM state WHERE key=?', (key,)).fetchone()
    return json.loads(row[0]) if row else {}

def save(key, value):
    with db() as con:
        con.execute('INSERT OR REPLACE INTO state VALUES (?,?)', (key, json.dumps(value)))

class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.0'
    def setup(self):
        super().setup()
        self.connection.settimeout(30)
    def log_message(self, fmt, *args):
        # Do not log tokens, headers or request bodies.
        pass
    def reply(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(data)))
        self.end_headers()
        self.wfile.write(data)
    def authorized(self, admin=False):
        expected = ADMIN_TOKEN if admin else DEVICE_TOKEN
        value = self.headers.get('Authorization','')
        if not expected or not hmac.compare_digest(value, 'Bearer ' + expected):
            self.reply(401, {'error':'unauthorized'})
            return False
        return True
    def body(self, limit):
        if self.headers.get('Transfer-Encoding'):
            raise ValueError('chunked body unsupported')
        n = int(self.headers.get('Content-Length','-1'))
        if n < 0 or n > limit:
            raise ValueError('invalid body size')
        body = self.rfile.read(n)
        if len(body) != n:
            raise ValueError('incomplete body')
        return body
    def do_GET(self):
        if self.path not in ('/api/config','/admin/status'):
            return self.reply(404, {'error':'not found'})
        if not self.authorized(self.path.startswith('/admin/')): return
        if self.path == '/api/config': return self.reply(200, load('config'))
        status=load('status')
        status['online'] = time.time() - status.get('received_at',0) < 90
        self.reply(200, {'desired':load('config'),'reported':status})
    def do_POST(self):
        if self.path not in ('/admin/config','/api/status'):
            return self.reply(404, {'error':'not found'})
        if not self.authorized(self.path.startswith('/admin/')): return
        try:
            value=json.loads(self.body(16384))
            if not isinstance(value, dict): raise ValueError('object required')
            if self.path == '/admin/config':
                if set(value) != {'camera','enabled','interval'}: raise ValueError('exact config fields required')
                if value['camera'] not in ('front','back') or type(value['enabled']) is not bool or type(value['interval']) is not int or not 10 <= value['interval'] <= 86400:
                    raise ValueError('invalid config')
                save('config',value)
            else:
                value['received_at']=time.time(); save('status',value)
            self.reply(200, {'ok':True})
        except (ValueError, UnicodeError): self.reply(400, {'error':'invalid request'})
    def do_PUT(self):
        if not self.path.startswith('/api/upload/'): return self.reply(404, {'error':'not found'})
        if not self.authorized(): return
        name=self.path[len('/api/upload/'):]
        if not re.fullmatch(r'[0-9]{10,16}_(front|back)_[a-f0-9-]{36}\.jpg',name):
            return self.reply(400, {'error':'invalid name'})
        try:
            if shutil.disk_usage(ROOT).free < MAX_UPLOAD * 2: return self.reply(507, {'error':'disk full'})
            body=self.body(MAX_UPLOAD)
            if not body.startswith(b'\xff\xd8') or not body.endswith(b'\xff\xd9'): raise ValueError('invalid JPEG')
            sha=hashlib.sha256(body).hexdigest()
            # A DB write transaction serializes idempotency checks for concurrent retries.
            with db() as con:
                con.execute('BEGIN IMMEDIATE')
                prior=con.execute('SELECT sha FROM uploads WHERE name=?',(name,)).fetchone()
                if prior:
                    if prior[0] != sha: return self.reply(409, {'error':'name conflict'})
                else:
                    dest=ROOT/'photos'/name
                    temp=ROOT/'photos'/(name+'.part')
                    with temp.open('wb') as out:
                        out.write(body); out.flush(); os.fsync(out.fileno())
                    os.replace(str(temp),str(dest))
                    directory=os.open(str(dest.parent),os.O_RDONLY)
                    try: os.fsync(directory)
                    finally: os.close(directory)
                    con.execute('INSERT INTO uploads VALUES (?,?,?)',(name,sha,len(body)))
            self.reply(201, {'ok':True,'sha256':sha})
        except (ValueError, UnicodeError): self.reply(400, {'error':'invalid upload'})
        except (OSError, sqlite3.Error): self.reply(503, {'error':'storage unavailable'})

if __name__ == '__main__':
    if len(DEVICE_TOKEN)<32 or len(ADMIN_TOKEN)<32 or DEVICE_TOKEN==ADMIN_TOKEN:
        raise SystemExit('Set different DEVICE_TOKEN and ADMIN_TOKEN, each >=32 characters')
    os.umask(0o077)
    initialize()
    ThreadingHTTPServer(('127.0.0.1',int(os.environ.get('PORT','8080'))),Handler).serve_forever()
