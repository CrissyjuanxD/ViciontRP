import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';

const KEY = 'vrp1.prueba.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
const sha1 = (b) => crypto.createHash('sha1').update(b).digest('hex');
const players = { Launcher_Al_Dia: { launcher: true, current: true }, Launcher_Viejo: { launcher: true, current: false }, Mod_Cliente: { launcher: true, current: true } };
const log = [];
const state = { gen: 1 };
const served = new Map();

function current() {
  const packs = [
    { path: 'resourcepacks/Croissants.zip', body: Buffer.from(`pack croissants ${state.gen}`) },
    { path: 'resourcepacks/Extra', body: Buffer.from('pack extra') },
  ];
  for (const p of packs) served.set(sha1(p.body), p.body);
  return { packs: packs.map((p) => ({ path: p.path, name: p.path.split('/').pop(), sha1: sha1(p.body), size: p.body.length, url: `http://127.0.0.1:8787/v1/rp/prueba/${sha1(p.body)}.zip` })), assets: sha1(`assets ${state.gen}`) };
}

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  const json = (status, data) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); };
  if (url.pathname === '/__publish' && req.method === 'POST') {
    state.gen++;
    json(200, current());
    return;
  }
  if (url.pathname === '/__state') { json(200, current()); return; }
  log.push({ path: url.pathname, nick: url.searchParams.get('nick'), auth: req.headers.authorization === `Bearer ${KEY}`, ticket: req.headers['x-viciont-ticket'] || null });
  fs.writeFileSync(process.argv[2], JSON.stringify(log));
  const m = /^\/v1\/rp\/prueba\/([a-f0-9]{40})\.zip$/.exec(url.pathname);
  if (m && served.has(m[1])) {
    res.writeHead(200, { 'Content-Type': 'application/zip' });
    res.end(served.get(m[1]));
    return;
  }
  if (url.pathname !== '/v1/rp/prueba') { json(404, {}); return; }
  if (req.headers.authorization !== `Bearer ${KEY}`) { json(401, { ok: false, error: 'bad_key', message: 'La clave del plugin no es válida' }); return; }
  const nick = url.searchParams.get('nick');
  const now = current();
  const out = { ok: true, instance: { id: 'prueba', name: 'Croissants de Prueba', version: state.gen, mc: '1.21.4', loader: 'fabric', assets: now.assets }, pack: now.packs[0], packs: now.packs };
  if (nick) {
    out.player = { ...(players[nick] || { launcher: false, current: false }) };
    const t = req.headers['x-viciont-ticket'];
    if (t) out.player.ticket = t === `pase-${nick}`;
  }
  json(200, out);
}).listen(8787, '127.0.0.1', () => console.log(`API de prueba lista · assets ${current().assets}`));
