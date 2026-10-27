import http.client
import json
import tempfile
import threading
import unittest
from pathlib import Path
from http.server import ThreadingHTTPServer
import server

class Integration(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        server.ROOT=Path(self.temp.name)
        server.DEVICE_TOKEN='d'*32; server.ADMIN_TOKEN='a'*32
        server.initialize()
        self.http=ThreadingHTTPServer(('127.0.0.1',0),server.Handler)
        self.thread=threading.Thread(target=self.http.serve_forever,daemon=True); self.thread.start()
    def tearDown(self):
        self.http.shutdown(); self.http.server_close(); self.thread.join(); self.temp.cleanup()
    def request(self,method,path,body=b'',token='d'*32):
        c=http.client.HTTPConnection('127.0.0.1',self.http.server_port,timeout=3)
        c.request(method,path,body,{'Authorization':'Bearer '+token})
        r=c.getresponse(); value=(r.status,json.loads(r.read())); c.close(); return value
    def test_auth_config_status(self):
        self.assertEqual(self.request('GET','/api/config',token='bad')[0],401)
        self.assertEqual(self.request('POST','/admin/config',b'{}')[0],401)
        config={'camera':'front','enabled':False,'interval':10}
        self.assertEqual(self.request('POST','/admin/config',json.dumps(config).encode(),'a'*32)[0],200)
        self.assertEqual(self.request('GET','/api/config')[1],config)
        config['interval']=True
        self.assertEqual(self.request('POST','/admin/config',json.dumps(config).encode(),'a'*32)[0],400)
        self.assertFalse(self.request('GET','/admin/status',token='a'*32)[1]['reported']['online'])
        self.assertEqual(self.request('POST','/api/status',b'{"camera":"front"}')[0],200)
        self.assertTrue(self.request('GET','/admin/status',token='a'*32)[1]['reported']['online'])
    def test_readable_unicode_filename(self):
        from urllib.parse import quote
        name='前摄_2026-10-27_14-30-00-123_+0800_1234abcd.jpg'
        path='/api/upload/'+quote(name)
        data=b'\xff\xd8test\xff\xd9'
        self.assertEqual(self.request('PUT',path,data)[0],201)
        self.assertTrue((server.ROOT/'photos'/name).exists())
        self.assertEqual(self.request('PUT',path,data)[0],201)
        self.assertEqual(self.request('PUT','/api/upload/'+quote('../'+name),data)[0],400)

    def test_retry_and_conflict(self):
        path='/api/upload/1793000000000_back_12345678-1234-1234-1234-123456789abc.jpg'
        data=b'\xff\xd8test\xff\xd9'
        self.assertEqual(self.request('PUT',path,data)[0],201)
        self.assertEqual(self.request('PUT',path,data)[0],201)
        self.assertEqual(len(list((server.ROOT/'photos').iterdir())),1)
        self.assertEqual(self.request('PUT',path,b'\xff\xd8different\xff\xd9')[0],409)
        self.assertEqual(self.request('PUT','/api/upload/../../bad',data)[0],400)
        self.assertEqual(self.request('PUT',path,b'not jpeg')[0],400)

if __name__=='__main__': unittest.main()
