import { spawn, execFileSync } from 'node:child_process';
import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const [dir, target] = process.argv.slice(2);
const ROOT = process.cwd();
const MOD = path.join(ROOT, 'mod');
const RUN = path.join(MOD, 'run');
const SHOTS = path.join(ROOT, `screens-${target}`);
const NICK = 'Mod_Cliente';
const API = 'http://127.0.0.1:8787';
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const api = (p, method = 'GET') => fetch(`${API}${p}`, { method }).then((r) => r.json());
const serverLog = () => fs.readFileSync(path.join(dir, 'server.log'), 'utf8');
const clientLog = () => { try { return fs.readFileSync(path.join(RUN, 'logs', 'latest.log'), 'utf8'); } catch { return ''; } };
const failures = [];
const check = (ok, what) => {
  console.log(`${ok ? 'OK ' : 'MAL'} ${what}`);
  if (!ok) failures.push(what);
};

async function until(fn, ms, every = 500) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (fn()) return true;
    await wait(every);
  }
  return false;
}

function zip(files) {
  const parts = [];
  const central = [];
  let offset = 0;
  for (const [n, body] of Object.entries(files)) {
    const name = Buffer.from(n);
    const data = Buffer.from(body);
    const crc = zlib.crc32(data);
    const h = Buffer.alloc(30);
    h.writeUInt32LE(0x04034b50, 0);
    h.writeUInt16LE(20, 4);
    h.writeUInt32LE(crc, 14);
    h.writeUInt32LE(data.length, 18);
    h.writeUInt32LE(data.length, 22);
    h.writeUInt16LE(name.length, 26);
    parts.push(h, name, data);
    central.push({ name, crc, size: data.length, offset });
    offset += 30 + name.length + data.length;
  }
  const cd = [];
  for (const c of central) {
    const h = Buffer.alloc(46);
    h.writeUInt32LE(0x02014b50, 0);
    h.writeUInt16LE(20, 4);
    h.writeUInt16LE(20, 6);
    h.writeUInt32LE(c.crc, 16);
    h.writeUInt32LE(c.size, 20);
    h.writeUInt32LE(c.size, 24);
    h.writeUInt16LE(c.name.length, 28);
    h.writeUInt32LE(c.offset, 42);
    cd.push(h, c.name);
  }
  const dirBuf = Buffer.concat(cd);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(central.length, 8);
  end.writeUInt16LE(central.length, 10);
  end.writeUInt32LE(dirBuf.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...parts, dirBuf, end]);
}

const pack = (description) => zip({
  'pack.mcmeta': JSON.stringify({ pack: { description, pack_format: 34, supported_formats: [34, 9999], min_format: 34, max_format: 9999 } }),
  'assets/minecraft/lang/es_es.json': JSON.stringify({ 'menu.singleplayer': description }),
});

async function shot(name) {
  const dir = path.join(RUN, 'screenshots');
  const before = new Set(fs.existsSync(dir) ? fs.readdirSync(dir) : []);
  const appeared = () => fs.existsSync(dir) && fs.readdirSync(dir).some((f) => !before.has(f));
  let win = '';
  try {
    win = execFileSync('xdotool', ['search', '--onlyvisible', '--class', 'Minecraft|java'], { encoding: 'utf8' }).trim().split(/\s+/).pop();
  } catch {}
  for (const args of [win ? ['windowactivate', '--sync', win, 'key', 'F2'] : null, ['mousemove', '640', '360', 'key', 'F2'], win ? ['key', '--window', win, 'F2'] : null]) {
    if (!args) continue;
    try { execFileSync('xdotool', args, { timeout: 10000 }); } catch (e) { console.log(`xdotool ${args[0]}: ${e.message.split('\n')[0]}`); }
    if (await until(appeared, 4000, 200)) break;
  }
  const ok = await until(appeared, 4000, 200);
  if (!ok) { console.log(`La captura ${name} no apareció`); return; }
  await wait(1000);
  const file = fs.readdirSync(dir).find((f) => !before.has(f));
  fs.mkdirSync(SHOTS, { recursive: true });
  fs.copyFileSync(path.join(dir, file), path.join(SHOTS, `${name}.png`));
}

fs.rmSync(RUN, { recursive: true, force: true });
fs.mkdirSync(path.join(RUN, 'resourcepacks'), { recursive: true });
fs.writeFileSync(path.join(RUN, 'resourcepacks', 'Base.zip'), pack('Base 1'));
fs.writeFileSync(path.join(RUN, 'options.txt'), [
  'onboardAccessibility:false', 'skipMultiplayerWarning:true', 'joinedFirstServer:true', 'tutorialStep:none', 'pauseOnLostFocus:false',
  'renderDistance:2', 'simulationDistance:5', 'soundCategory_master:0.0', 'resourcePacks:["vanilla","file/Base.zip"]', '',
].join('\n'));

const configFile = path.join(dir, 'plugins', 'ViciontRP', 'config.yml');
fs.writeFileSync(configFile, fs.readFileSync(configFile, 'utf8').replace(/^access: .*$/m, 'access: launcher-only'));
fs.appendFileSync(path.join(dir, 'console'), 'viciontrp reload\n');
await until(() => serverLog().includes('solo se puede entrar con el launcher'), 20000);

const first = await api('/__state');
const token = crypto.randomBytes(24).toString('hex');
const events = [];
const job = { state: 'idle', done: 0, total: 0, enable: [] };
let installed = first.assets;
const bridge = http.createServer((req, res) => {
  const send = (status, data) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); };
  if (req.headers.authorization !== `Bearer ${token}`) { send(401, { ok: false, error: 'unauthorized' }); return; }
  let body = '';
  req.on('data', (c) => { body += c; });
  req.on('end', () => {
    const route = `${req.method} ${req.url}`;
    const data = body ? JSON.parse(body) : {};
    events.push({ route, at: Date.now(), data });
    if (route === 'GET /hello') return send(200, { ok: true, launcher: 'prueba', instance: 'prueba', assets: installed, version: 1 });
    if (route === 'POST /ticket') return send(200, { ok: true, ticket: `pase-${NICK}` });
    if (route === 'GET /assets/status') return send(200, { ok: true, ...job, files: job.total ? 3 : 0, filesDone: job.total ? Math.floor((job.done / job.total) * 3) : 0, replace: job.state === 'ready' });
    if (route === 'POST /assets/cancel') { job.state = 'idle'; return send(200, { ok: true }); }
    if (route === 'POST /assets/prepare') {
      Object.assign(job, { state: 'checking', done: 0, total: 0, enable: [], target: data.assets });
      setTimeout(() => {
        Object.assign(job, { state: 'downloading', total: 4_000_000 });
        const t = setInterval(() => {
          job.done = Math.min(job.total, job.done + 200_000);
          if (job.done < job.total) return;
          clearInterval(t);
          fs.writeFileSync(path.join(RUN, 'resourcepacks', 'Nuevo.zip'), pack('Nuevo'));
          Object.assign(job, { state: 'ready', enable: ['Nuevo.zip'] });
        }, 300);
      }, 800);
      return send(200, { ok: true, state: job.state });
    }
    if (route === 'POST /assets/apply') {
      if (job.state !== 'ready') return send(409, { ok: false, error: 'Nada que aplicar' });
      fs.writeFileSync(path.join(RUN, 'resourcepacks', 'Base.zip'), pack('Base 2'));
      installed = job.target;
      job.state = 'idle';
      return send(200, { ok: true, onlyPacks: true, version: 2 });
    }
    return send(404, { ok: false });
  });
});
await new Promise((r) => bridge.listen(0, '127.0.0.1', r));

const out = fs.openSync(path.join(dir, `client-${target}.log`), 'w');
const gradle = spawn('./gradlew', ['--no-daemon', '--console=plain', `-Ptarget=${target}`, 'runClient', `-PclientArgs=--quickPlayMultiplayer 127.0.0.1:25565 --username ${NICK}`], {
  cwd: MOD, detached: true, stdio: ['ignore', out, out],
  env: { ...process.env, VICIONT_BRIDGE: `http://127.0.0.1:${bridge.address().port}`, VICIONT_BRIDGE_TOKEN: token },
});
let exited = null;
gradle.on('exit', (code) => { exited = code; });
const stop = () => { try { process.kill(-gradle.pid, 'SIGKILL'); } catch {} };
process.on('exit', stop);

try {
  const joined = await until(() => exited !== null || serverLog().includes(`${NICK} juega desde el launcher con los recursos al día`), 20 * 60000, 2000);
  check(joined && exited === null, 'el cliente con el mod entra al servidor y el plugin sabe que tiene los recursos al día');
  if (!joined || exited !== null) throw new Error('El cliente no llegó a entrar');
  check(events.some((e) => e.route === 'GET /hello'), 'el mod saluda al launcher');
  check(await until(() => events.some((e) => e.route === 'POST /ticket'), 15000), 'con «solo con el launcher», el plugin le pide el pase y el mod se lo pide al launcher');
  check(/ViciontRP 1\.\d+\.\d+ listo \(con el launcher de Viciont Studios\)/.test(clientLog()), 'el mod arranca y encuentra el launcher');
  await wait(8000);
  await shot('1-dentro');
  const next = await api('/__publish', 'POST');
  check(await until(() => events.some((e) => e.route === 'POST /assets/prepare' && e.data.assets === next.assets), 60000), 'al publicar, el mod le pide al launcher los assets nuevos');
  await until(() => job.state === 'downloading' && job.done >= job.total / 2, 30000, 100);
  await shot('2-descargando');
  check(await until(() => events.some((e) => e.route === 'POST /assets/apply'), 180000), 'cuando termina la descarga, el mod recarga y el launcher cambia los archivos');
  const done = await until(() => serverLog().includes(`${NICK}: recursos actualizados desde el launcher.`), 240000, 1000);
  check(done, 'el mod avisa al plugin de que ya tiene los recursos nuevos');
  await wait(6000);
  await shot('3-actualizado');
  const log = clientLog();
  const reload = log.lastIndexOf('Reloading ResourceManager');
  const placed = log.lastIndexOf('Assets nuevos colocados');
  check(reload >= 0 && placed > reload, 'los archivos se cambian durante la recarga, cuando el juego ya soltó los packs');
  check(log.slice(reload).split('\n')[0].includes('file/Nuevo.zip'), 'el pack nuevo entra en la recarga');
  check(/resourcePacks:\[.*"file\/Nuevo\.zip".*\]/.test(fs.readFileSync(path.join(RUN, 'options.txt'), 'utf8')), 'y se queda activado en options.txt');
  check(log.includes('Assets actualizados'), 'el mod termina la actualización');
  check(!/InvalidInjectionException|Mixin apply failed|viciontrp.*Exception/i.test(log), 'sin errores del mod en el registro del juego');
  check(!serverLog().includes('no pudo actualizar los recursos'), 'el plugin no tuvo que mandar el pack del servidor');
  check(!serverLog().includes(`${NICK} intentó entrar sin el launcher`), 'y no lo echa por «solo con el launcher»');
} catch (e) {
  check(false, e.message);
} finally {
  stop();
  bridge.close();
  for (const f of ['latest.log']) {
    try { fs.copyFileSync(path.join(RUN, 'logs', f), path.join(ROOT, `client-${target}-${f}`)); } catch {}
  }
  try { fs.copyFileSync(path.join(dir, `client-${target}.log`), path.join(ROOT, `client-${target}-gradle.log`)); } catch {}
}

if (failures.length) {
  console.error(`\n${failures.length} comprobación(es) fallaron`);
  process.exit(1);
}
console.log('\nTodo bien');
process.exit(0);
