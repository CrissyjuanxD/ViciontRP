import mineflayer from 'mineflayer';
import fs from 'node:fs';

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const text = (v) => (typeof v === 'string' ? v : JSON.stringify(v));

function join(username) {
  return new Promise((resolve, reject) => {
    const s = { username, packs: [], messages: [], kicked: null, ended: false };
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username, auth: 'offline' });
    s.bot = bot;
    bot._client.on('packet', (data, meta) => {
      if (meta.name === 'add_resource_pack' || meta.name === 'resource_pack_send') s.packs.push(data);
    });
    bot.on('message', (m) => s.messages.push(m.toString()));
    bot.on('kicked', (reason) => { s.kicked = text(reason); });
    bot.on('end', () => { s.ended = true; });
    bot.once('spawn', () => resolve(s));
    bot.once('error', reject);
    setTimeout(() => reject(new Error(`${username} no pudo entrar`)), 60000);
  });
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

const fresh = await join('Launcher_Al_Dia');
await wait(6000);
check(fresh.packs.length === 0, 'quien juega desde el launcher con el pack al día no recibe nada');
fresh.bot.quit();
await wait(1500);

const old = await join('Launcher_Viejo');
check(await until(() => old.packs.length > 0, 10000), 'quien tiene la instancia sin actualizar recibe el pack');
old.bot.quit();
await wait(1500);

const normal = await join('Jugador_Normal');
check(await until(() => normal.packs.length > 0, 10000), 'quien entra sin el launcher recibe el pack');
const p = normal.packs[0] || {};
check(/\/v1\/rp\/prueba\/[a-f0-9]{40}\.zip$/.test(p.url || ''), `el enlace es el del pack (${p.url})`);
check(p.forced === true, 'el pack es obligatorio');
check(text(p.promptMessage ?? p.prompt ?? '').includes('Croissants de Prueba'), 'el mensaje lleva el nombre de la instancia');
normal.bot.acceptResourcePack();
check(await until(() => normal.messages.some((m) => m.includes('launcher')), 8000), 'al cargarlo recibe el mensaje del chat con el enlace');
normal.bot.quit();
await wait(1500);

const no = await join('Jugador_Rechaza');
await until(() => no.packs.length > 0, 10000);
no.bot.denyResourcePack();
check(await until(() => no.kicked || no.ended, 8000), 'quien lo rechaza sale del servidor');
check(String(no.kicked || '').includes('Necesitas'), `con el mensaje de config.yml (${no.kicked})`);

const calls = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
check(calls.length > 0 && calls.every((c) => c.auth), 'todas las consultas llevan la clave');
check(['Launcher_Al_Dia', 'Jugador_Normal'].every((n) => calls.some((c) => c.nick === n)), 'se pregunta por cada jugador al entrar');

if (failures.length) {
  console.error(`\n${failures.length} comprobación(es) fallaron`);
  process.exit(1);
}
console.log('\nTodo bien');
process.exit(0);
