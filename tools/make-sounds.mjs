// Makes the bleep's voice: short chip-tune blips, one sound per mood, plus a hurt buzz and the clunk of being put
// together (assets/bleep/sounds/entity/bleep/<name>.ogg). The sound is the name of the mod.
// Needs the npm package wasm-media-encoders@0.7.0: install it in an empty scratch folder and pass that package's
// folder:   node tools/make-sounds.mjs <scratch>/node_modules/wasm-media-encoders
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const pkg = process.argv[2];
if (!pkg) throw new Error('usage: node tools/make-sounds.mjs <path to node_modules/wasm-media-encoders>');
const { createOggEncoder } = await import(pathToFileURL(join(pkg, 'dist/es/index.mjs')).href);
const OUT = 'src/main/resources/assets/bleep/sounds/entity/bleep';
mkdirSync(OUT, { recursive: true });
const RATE = 44100;

// a seeded random, so the glitch and the noise come out the same every time
let seed = 7;
const random = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);

/**
 * A row of notes: [start Hz, end Hz, seconds, pause after, options]. Options: wobble (vibrato depth), noise (0..1),
 * level. The tone is a soft square (odd harmonics), a little bit-crushed so it sounds like a small robot.
 */
function sound(notes, level = 0.55) {
  const total = notes.reduce((t, [, , d, gap = 0]) => t + d + gap, 0) + 0.05;
  const pcm = new Float32Array(Math.round(total * RATE));
  let at = 0;
  for (const [f0, f1, dur, gap = 0, o = {}] of notes) {
    const n = Math.round(dur * RATE), start = Math.round(at * RATE), fade = Math.round(0.006 * RATE);
    let phase = 0;
    for (let i = 0; i < n; i++) {
      const t = i / n, f = (f0 + (f1 - f0) * t) * (1 + (o.wobble || 0) * Math.sin(2 * Math.PI * 7 * i / RATE));
      phase += f / RATE;
      let s = 0;
      for (const [h, a] of [[1, 1], [3, 0.3], [5, 0.12]]) s += a * Math.sin(2 * Math.PI * h * phase);
      if (o.noise) s = s * (1 - o.noise) + (random() * 2 - 1) * o.noise;
      const envelope = Math.min(1, i / fade, (n - i) / fade) * (o.decay ? Math.exp(-o.decay * t) : 1);
      pcm[start + i] += Math.round(s * 12) / 12 * envelope * (o.level ?? 1); // 4-bit-ish steps: a robot voice
    }
    at += dur + gap;
  }
  let lp = 0, peak = 0;
  for (let i = 0; i < pcm.length; i++) { lp += (pcm[i] - lp) * 0.5; pcm[i] = lp; peak = Math.max(peak, Math.abs(lp)); }
  for (let i = 0; i < pcm.length; i++) pcm[i] *= level / peak;
  return pcm;
}

const glitch = [];
for (let k = 0; k < 10; k++) { const f = 300 + random() * 1700; glitch.push([f, f * (0.6 + random()), 0.03, 0.008, { noise: 0.25 }]); }

const SOUNDS = {
  happy: sound([[880, 900, 0.07, 0.03], [1175, 1200, 0.09]]),
  excited: sound([[880, 880, 0.06, 0.02], [1109, 1109, 0.06, 0.02], [1319, 1319, 0.06, 0.02], [1760, 1800, 0.1]]),
  sad: sound([[660, 392, 0.45, 0, { wobble: 0.01 }]], 0.45),
  angry: sound([[220, 210, 0.1, 0.04, { noise: 0.15 }], [196, 180, 0.15, 0, { noise: 0.15 }]]),
  bored: sound([[523, 494, 0.32, 0, { wobble: 0.03 }]], 0.4),
  surprised: sound([[600, 1500, 0.14]]),
  tired: sound([[523, 523, 0.12, 0.06], [440, 440, 0.12, 0.06], [349, 330, 0.22]], 0.35),
  asleep: sound([[900, 120, 0.6, 0, { decay: 1.5 }]], 0.45),
  boot: sound([[523, 523, 0.08, 0.03], [784, 784, 0.08, 0.03], [1047, 1047, 0.08, 0.03], [1568, 1568, 0.16]]),
  glitch: sound(glitch, 0.45),
  stubborn: sound([[494, 494, 0.12, 0.05], [392, 380, 0.18]]),
  hurt: sound([[300, 150, 0.12, 0, { noise: 0.4 }]]),
  assemble: sound([[180, 120, 0.12, 0, { noise: 0.5, decay: 6 }]], 0.5),
};

const encoder = await createOggEncoder();
for (const [name, pcm] of Object.entries(SOUNDS)) {
  encoder.configure({ channels: 1, sampleRate: RATE, vbrQuality: 5 });
  const parts = [encoder.encode([pcm]).slice(), encoder.finalize().slice()];
  writeFileSync(join(OUT, name + '.ogg'), Buffer.concat(parts.map((p) => Buffer.from(p))));
  console.log('wrote', name, (pcm.length / RATE).toFixed(2) + ' s');
}
