import mineflayer from 'mineflayer';
import fs from 'node:fs';
import path from 'node:path';

const [calls, dir] = process.argv.slice(2);
const CHANNEL = 'viciontrp:main';
const API = 'http://127.0.0.1:8787';
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const text = (v) => (typeof v === 'string' ? v : JSON.stringify(v));
const console_ = (cmd) => fs.appendFileSync(path.join(dir, 'console'), `${cmd}\n`);
const config = (patch) => {
  const file = path.join(dir, 'plugins', 'ViciontRP', 'config.yml');
  let s = fs.readFileSync(file, 'utf8');
  for (const [k, v] of Object.entries(patch)) s = s.replace(new RegExp(`^${k}: .*$`, 'm'), `${k}: ${v}`);
  fs.writeFileSync(file, s);
  console_('viciontrp reload');
};
const api = (p, method = 'GET') => fetch(`${API}${p}`, { method }).then((r) => r.json());

function join(username, { mod = null } = {}) {
  return new Promise((resolve, reject) => {
    const s = { username, packs: [], messages: [], mod: [], kicked: null, ended: false };
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username, auth: 'offline' });
    s.bot = bot;
    s.send = (m) => bot._client.writeChannel(CHANNEL, Buffer.from(JSON.stringify(m)));
    bot._client.on('packet', (data, meta) => {
      if (meta.name === 'add_resource_pack' || meta.name === 'resource_pack_send') s.packs.push(data);
    });
    if (mod) {
      bot._client.once('login', () => {
        bot._client.registerChannel(CHANNEL, ['restBuffer', []], true);
        bot._client.on(CHANNEL, (buf) => {
          let m;
          try { m = JSON.parse(Buffer.from(buf).toString('utf8')); } catch { return; }
          s.mod.push(m);
          if (m.t === 'hello' && m.ticket && mod.ticket) s.send({ t: 'ticket', ticket: mod.ticket });
        });
      });
    }
    bot.on('message', (m) => s.messages.push(m.toString()));
    bot.on('kicked', (reason) => { s.kicked = text(reason); });
    bot.on('end', () => { s.ended = true; });
    bot.once('spawn', () => {
      if (mod) setTimeout(() => s.send({ t: 'hello', v: 1, mod: 'prueba', launcher: mod.launcher, assets: mod.assets }), 300);
      resolve(s);
    });
    bot.once('error', reject);
    setTimeout(() => reject(new Error(`${username} no pudo entrar`)), 60000);
  });
}

function answer(s, results, uuids = s.packs.map((p) => p.uuid)) {
  for (const uuid of uuids) for (const result of results) s.bot._client.write('resource_pack_receive', uuid ? { uuid, result } : { result });
}

async function until(fn, ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (fn()) return true;
    await wait(200);
  }
  return false;
}

const failures = [];
const check = (ok, what) => {
  console.log(`${ok ? 'OK ' : 'MAL'} ${what}`);
  if (!ok) failures.push(what);
};
const leave = async (...list) => { for (const s of list) s.bot.quit(); await wait(1500); };

const first = await api('/__state');

const fresh = await join('Launcher_Al_Dia');
await wait(6000);
check(fresh.packs.length === 0, 'quien juega desde el launcher con el pack al día no recibe nada');
await leave(fresh);

const old = await join('Launcher_Viejo');
check(await until(() => old.packs.length === 2, 10000), 'quien tiene la instancia sin actualizar recibe los dos packs');
await leave(old);

const normal = await join('Jugador_Normal');
check(await until(() => normal.packs.length === 2, 10000), 'quien entra sin el launcher recibe los dos packs');
check(normal.packs.every((p) => /\/v1\/rp\/prueba\/[a-f0-9]{40}\.zip$/.test(p.url || '')), 'los enlaces son los de los packs');
check(new Set(normal.packs.map((p) => p.uuid)).size === 2, 'cada pack tiene su identificador');
check(normal.packs.every((p) => p.forced === true), 'los packs son obligatorios');
const prompt = text(normal.packs[0]?.promptMessage ?? normal.packs[0]?.prompt ?? '');
check(prompt.includes('Croissants de Prueba'), 'el mensaje lleva el nombre de la instancia');
check(/c8a2f8/i.test(prompt), `el mensaje del pack tiene el color hexadecimal (${prompt.slice(0, 160)})`);
answer(normal, [3, 4, 0]);
check(await until(() => normal.messages.some((m) => m.includes('launcher')), 8000), 'al cargarlo recibe el mensaje del chat con el enlace');

const no = await join('Jugador_Rechaza');
await until(() => no.packs.length > 0, 10000);
answer(no, [1], [no.packs[0].uuid]);
check(await until(() => no.kicked || no.ended, 8000), 'quien lo rechaza sale del servidor');
check(String(no.kicked || '').includes('Necesitas'), `con el mensaje de config.yml (${String(no.kicked).slice(0, 120)})`);

const modded = await join('Mod_Launcher', { mod: { launcher: true, assets: first.assets, ticket: 'pase-Mod_Launcher' } });
check(await until(() => modded.mod.some((m) => m.t === 'hello'), 8000), 'el plugin contesta al mod');
check(modded.mod.find((m) => m.t === 'hello')?.autoAccept === true, 'y le dice que acepte los packs solo (required: true)');
check(modded.mod.find((m) => m.t === 'hello')?.ticket === false, 'sin «solo con el launcher» no pide el pase');
await wait(5000);
check(modded.packs.length === 0, 'el mod con el launcher al día no recibe el pack del servidor');
check(!modded.mod.some((m) => m.t === 'update'), 'ni tiene que actualizar nada');

const plain = await join('Mod_Sin_Launcher', { mod: { launcher: false } });
check(await until(() => plain.packs.length === 2, 12000), 'el mod sin el launcher recibe el pack del servidor');
await leave(plain);

const before = normal.packs.length;
const next = await api('/__publish', 'POST');
check(await until(() => modded.messages.some((m) => /actualizarán en 3 segundos/.test(m)), 25000), 'aviso en el chat antes de actualizar');
check(normal.messages.some((m) => /actualizarán en 3 segundos/.test(m)), 'el aviso también le llega a quien no tiene el launcher');
check(normal.packs.length === before, 'antes del aviso no se manda nada');
check(await until(() => modded.mod.some((m) => m.t === 'update' && m.assets === next.assets), 10000), 'al acabar el aviso el mod recibe la orden de actualizar');
check(await until(() => normal.packs.length === before + 1, 10000), 'quien no tiene el launcher recibe solo el pack que cambió');
check(normal.packs.at(-1)?.url?.includes(next.packs[0].sha1), 'y es el pack nuevo');
modded.send({ t: 'status', s: 'failed', error: 'prueba' });
check(await until(() => modded.packs.length === 2, 10000), 'si el launcher no puede actualizar, el mod recibe el pack del servidor');

config({ 'update-mode': 'manual' });
await wait(3000);
const manual = normal.packs.length;
const third = await api('/__publish', 'POST');
await wait(22000);
check(normal.packs.length === manual, 'en modo manual no se actualiza solo');
console_('viciontrp actualizar ahora');
check(await until(() => normal.packs.length === manual + 1 && normal.packs.at(-1).url.includes(third.packs[0].sha1), 15000), 'con /viciontrp actualizar ahora le llega el pack nuevo');
await leave(normal, modded);

config({ access: 'launcher-only' });
await wait(3000);
const intruder = await join('Intruso');
const good = await join('Mod_Ticket', { mod: { launcher: true, assets: third.assets, ticket: 'pase-Mod_Ticket' } });
const bad = await join('Mod_Malo', { mod: { launcher: true, assets: third.assets, ticket: 'pase-otro' } });
const heart = await join('Launcher_Al_Dia');
check(await until(() => bad.kicked || bad.ended, 12000), 'con «solo con el launcher», un pase falso se echa enseguida');
check(await until(() => intruder.kicked || intruder.ended, 25000), 'y quien entra sin el launcher también sale');
check(String(intruder.kicked || '').includes('launcher'), `con el mensaje «launcher-only» (${String(intruder.kicked).slice(0, 120)})`);
await wait(3000);
check(!good.kicked && !good.ended, 'quien entra con el mod y un pase válido se queda');
check(!heart.kicked && !heart.ended, 'quien juega desde el launcher sin el mod también se queda');
await leave(good, heart);

const log = JSON.parse(fs.readFileSync(calls, 'utf8'));
check(log.length > 0 && log.filter((c) => c.path === '/v1/rp/prueba').every((c) => c.auth), 'todas las consultas llevan la clave');
check(['Launcher_Al_Dia', 'Jugador_Normal'].every((n) => log.some((c) => c.nick === n)), 'se pregunta por cada jugador al entrar');
check(log.some((c) => c.nick === 'Mod_Ticket' && c.ticket === 'pase-Mod_Ticket'), 'con «solo con el launcher» el plugin pide el pase y lo comprueba con la API');
check(!log.some((c) => c.nick === 'Mod_Launcher' && c.ticket), 'sin «solo con el launcher» el pase no sale del PC');

if (failures.length) {
  console.error(`\n${failures.length} comprobación(es) fallaron`);
  process.exit(1);
}
console.log('\nTodo bien');
