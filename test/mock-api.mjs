import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';

const KEY = 'vrp1.prueba.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
const body = Buffer.from('paquete de prueba');
const sha1 = crypto.createHash('sha1').update(body).digest('hex');
const players = { Launcher_Al_Dia: { launcher: true, current: true }, Launcher_Viejo: { launcher: true, current: false } };
const log = [];

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  log.push({ path: url.pathname, nick: url.searchParams.get('nick'), auth: req.headers.authorization === `Bearer ${KEY}` });
  fs.writeFileSync(process.argv[2], JSON.stringify(log));
  if (url.pathname === `/v1/rp/prueba/${sha1}.zip`) {
    res.writeHead(200, { 'Content-Type': 'application/zip' });
    res.end(body);
    return;
  }
  if (url.pathname !== '/v1/rp/prueba') { res.writeHead(404); res.end('{}'); return; }
  if (req.headers.authorization !== `Bearer ${KEY}`) {
    res.writeHead(401, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ok: false, error: 'bad_key', message: 'La clave del plugin no es válida' }));
    return;
  }
  const nick = url.searchParams.get('nick');
  const out = { ok: true, instance: { id: 'prueba', name: 'Croissants de Prueba' }, pack: { sha1, size: body.length, url: `http://127.0.0.1:8787/v1/rp/prueba/${sha1}.zip` } };
  if (nick) out.player = players[nick] || { launcher: false, current: false };
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(out));
}).listen(8787, '127.0.0.1', () => console.log(`API de prueba lista · pack ${sha1}`));
