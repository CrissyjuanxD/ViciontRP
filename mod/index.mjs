import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const [version, tag, jars, out] = process.argv.slice(2);
const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), 'versions');
const files = [];
for (const name of fs.readdirSync(dir).filter((n) => n.endsWith('.properties')).sort()) {
  const props = Object.fromEntries(fs.readFileSync(path.join(dir, name), 'utf8').split(/\r?\n/).filter((l) => l.includes('=')).map((l) => l.split(/=(.*)/s).slice(0, 2)));
  if (props.release === 'false') continue;
  const target = name.replace(/\.properties$/, '');
  const file = `ViciontRP-mod-${target}-${version}.jar`;
  const body = fs.readFileSync(path.join(jars, file));
  files.push({
    file, min: props.min, max: props.max, size: body.length,
    sha1: crypto.createHash('sha1').update(body).digest('hex'),
    url: `https://github.com/CrissyjuanxD/ViciontRP/releases/download/${tag}/${file}`,
  });
}
fs.writeFileSync(out, `${JSON.stringify({ version, files }, null, 2)}\n`);
console.log(files.map((f) => path.join(jars, f.file)).join('\n'));
