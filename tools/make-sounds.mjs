// Makes the bleep's voice: cute little robot chirps, a few takes for every mood (Minecraft picks one at random), plus
// a hurt squeak and the clunk of being put together (assets/bleep/sounds/entity/bleep/<name>[_n].ogg). Every sound is
// made here from scratch (no samples, so no licences): a bright tone whose pitch slides and warbles, like a small
// droid talking. tools/gen-assets.mjs lists whatever takes are there in sounds.json, so run it after this.
// Needs the npm package wasm-media-encoders@0.7.0: install it in an empty scratch folder and pass that package's
// folder:   node tools/make-sounds.mjs <scratch>/node_modules/wasm-media-encoders
import { mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const pkg = process.argv[2];
if (!pkg) throw new Error('usage: node tools/make-sounds.mjs <path to node_modules/wasm-media-encoders>');
const { createOggEncoder } = await import(pathToFileURL(join(pkg, 'dist/es/index.mjs')).href);
const OUT = 'src/main/resources/assets/bleep/sounds/entity/bleep';
mkdirSync(OUT, { recursive: true });
for (const file of readdirSync(OUT)) if (file.endsWith('.ogg')) rmSync(join(OUT, file));
const RATE = 44100;

// a seeded random, so every run makes the same sounds
let seed = 11;
const random = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
const around = (value, spread) => value * (1 + (random() * 2 - 1) * spread);

/**
 * A chirp: its pitch goes from `from` to `to` (Hz) over `d` seconds (exponentially, so a slide sounds even; shape
 * 'hump' goes up and back, 'dip' down and back), then `gap` seconds of quiet. Options: warble (wobbles a second) and
 * depth, fm (how bright: 0 soft, 2 buzzy), noise, decay, level.
 */
const chirp = (from, to, d, gap = 0.02, o = {}) => ({ from, to, d, gap, ...o });

function render(chirps, level = 0.6) {
  const total = chirps.reduce((t, c) => t + c.d + c.gap, 0) + 0.04;
  const pcm = new Float32Array(Math.round(total * RATE));
  let at = 0;
  for (const c of chirps) {
    const n = Math.round(c.d * RATE), start = Math.round(at * RATE);
    const attack = Math.round(0.004 * RATE), release = Math.round(Math.min(0.03, c.d / 3) * RATE);
    let phase = 0;
    for (let i = 0; i < n; i++) {
      const t = i / n;
      const curve = c.shape === 'hump' ? Math.sin(Math.PI * t) : c.shape === 'dip' ? 1 - Math.sin(Math.PI * t) : t;
      let f = c.from * Math.pow(c.to / c.from, curve);
      if (c.warble) f *= 1 + (c.depth ?? 0.06) * Math.sin(2 * Math.PI * c.warble * i / RATE);
      phase += f / RATE;
      const p = 2 * Math.PI * phase;
      let s = Math.sin(p + (c.fm ?? 0.7) * Math.sin(2 * p)) * 0.85 + 0.15 * Math.sin(2 * p);
      if (c.noise) s = s * (1 - c.noise) + (random() * 2 - 1) * c.noise;
      const envelope = Math.min(1, i / attack, (n - i) / release) * (c.decay ? Math.exp(-c.decay * t) : 1);
      pcm[start + i] += s * envelope * (c.level ?? 1);
    }
    at += c.d + c.gap;
  }
  // a touch of 8-bit grit and a soft filter, then every sound about as loud as the others
  let lp = 0, peak = 0;
  for (let i = 0; i < pcm.length; i++) { lp += (Math.round(pcm[i] * 24) / 24 - lp) * 0.55; pcm[i] = lp; peak = Math.max(peak, Math.abs(lp)); }
  for (let i = 0; i < pcm.length; i++) pcm[i] *= level / peak;
  return pcm;
}

/** A few takes on a mood: the same idea, a little different each time. */
const takes = (make, n = 3) => Array.from({ length: n }, (_, k) => make(k));

const VOICE = {
  // bright little rising chirps: "bi-dee!"
  happy: takes((k) => render([
    chirp(around(1300, 0.08), around(1700, 0.08), 0.06, 0.03),
    ...(k === 1 ? [chirp(around(1500, 0.06), around(1500, 0.06), 0.05, 0.03)] : []),
    chirp(around(1600, 0.08), around(2200, 0.08), 0.09, 0, { shape: k === 2 ? 'hump' : undefined }),
  ])),
  // a fast trill that climbs and ends in a whoop
  excited: takes((k) => {
    const notes = [];
    for (let i = 0; i < 4 + k; i++) notes.push(chirp(around(1100 + i * 220, 0.04), around(1300 + i * 240, 0.04), 0.045, 0.015));
    notes.push(chirp(around(1900, 0.05), around(2900, 0.05), 0.12, 0, { warble: 28, depth: 0.05 }));
    return render(notes);
  }),
  // a slow, falling "ooh" with a wobble, and a small low sigh after
  sad: takes(() => render([
    chirp(around(950, 0.06), around(480, 0.06), 0.42, 0.05, { warble: 6, depth: 0.03, fm: 0.25 }),
    chirp(around(420, 0.05), around(360, 0.05), 0.18, 0, { fm: 0.2, level: 0.6, decay: 2 }),
  ], 0.45)),
  // a low buzzy raspberry: "brrzt-zt"
  angry: takes((k) => render([
    chirp(around(330, 0.06), around(280, 0.06), 0.12, 0.04, { fm: 2.4, warble: 45, depth: 0.08 }),
    chirp(around(300, 0.06), around(240, 0.06), 0.16 + 0.04 * k, 0, { fm: 2.6, warble: 50, depth: 0.1 }),
  ], 0.5)),
  // two lazy notes: "boo-dup"
  bored: takes((k) => render([
    chirp(around(700, 0.05), around(680, 0.05), 0.16, 0.08, { fm: 0.3, warble: 4, depth: 0.02 }),
    chirp(around(600, 0.05), k === 1 ? around(640, 0.05) : around(560, 0.05), 0.2, 0, { fm: 0.3 }),
  ], 0.4)),
  // a quick swoop up and a little blip at the top: "whoop?!"
  surprised: takes((k) => render([
    chirp(around(700, 0.08), around(2500, 0.06), 0.13, 0.02),
    chirp(around(2600, 0.05), around(2800, 0.05), 0.04 + 0.02 * k, 0),
  ])),
  // a sagging yawn: up a little, then down and slow
  tired: takes(() => render([
    chirp(around(600, 0.05), around(760, 0.05), 0.12, 0, { fm: 0.25 }),
    chirp(around(760, 0.05), around(330, 0.05), 0.42, 0, { fm: 0.2, warble: 5, depth: 0.02, decay: 1 }),
  ], 0.38)),
  // powering down, soft and slow
  asleep: takes((k) => render([
    chirp(around(1100, 0.05), around(160, 0.05), 0.55, 0.06, { fm: 0.2, decay: 1.4 }),
    ...(k !== 0 ? [chirp(around(220, 0.05), around(200, 0.05), 0.16, 0, { fm: 0.1, level: 0.5, decay: 3 })] : []),
  ], 0.4), 2),
  // a happy start-up chime: up the notes, then a chirp
  boot: [render([
    chirp(1047, 1047, 0.08, 0.02, { fm: 0.3 }), chirp(1319, 1319, 0.08, 0.02, { fm: 0.3 }), chirp(1568, 1568, 0.08, 0.02, { fm: 0.3 }),
    chirp(2093, 2093, 0.14, 0.05, { fm: 0.3, decay: 1 }), chirp(1600, 2400, 0.08, 0),
  ])],
  // "nuh-uh": down, then a firm low one
  stubborn: takes((k) => render([
    chirp(around(1000, 0.06), around(780, 0.06), 0.1, 0.05, { fm: 0.9 }),
    chirp(around(650, 0.06), around(520, 0.06), 0.16, 0, { fm: 1.1, warble: k === 2 ? 18 : 0, depth: 0.04 }),
  ], 0.5)),
  // chirps all over the place, a bit broken
  glitch: takes(() => {
    const notes = [];
    for (let i = 0; i < 9; i++) { const f = 400 + random() * 2600; notes.push(chirp(f, f * (0.5 + random()), 0.025 + random() * 0.03, 0.006, { noise: 0.2, fm: random() * 2 })); }
    return render(notes, 0.45);
  }, 2),
  // "eek!"
  hurt: takes(() => render([chirp(around(2600, 0.06), around(1100, 0.06), 0.11, 0, { fm: 1.2, warble: 35, depth: 0.04 })]), 2),
  // the clunk of a part going on
  assemble: [render([chirp(180, 120, 0.12, 0, { noise: 0.5, decay: 6, fm: 0.5 })], 0.5)],
};

const encoder = await createOggEncoder();
for (const [name, list] of Object.entries(VOICE)) {
  list.forEach((pcm, i) => {
    encoder.configure({ channels: 1, sampleRate: RATE, vbrQuality: 5 });
    const parts = [encoder.encode([pcm]).slice(), encoder.finalize().slice()];
    const file = list.length === 1 ? name : name + '_' + (i + 1);
    writeFileSync(join(OUT, file + '.ogg'), Buffer.concat(parts.map((p) => Buffer.from(p))));
    console.log('wrote', file, (pcm.length / RATE).toFixed(2) + ' s');
  });
}
