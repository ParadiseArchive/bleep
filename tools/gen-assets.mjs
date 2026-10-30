// Generates Bleep's textures, item models and language file. Run from the project root:  node tools/gen-assets.mjs
// The bleep's texture follows the boxes in client/BleepModel.java (64 x 64, Minecraft's usual box layout); the faces
// on its display are 128 x 128, so the display (10 x 5 on the model) gets 20 x 10 pixels.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { deflateSync } from 'node:zlib';

const ROOT = 'src/main/resources';
const ASSETS = join(ROOT, 'assets/bleep');
const DATA = join(ROOT, 'data/bleep');

function write(path, content) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content);
}
const json = (path, value) => write(path, JSON.stringify(value, null, 2) + '\n');

// ---------- PNG writer (RGBA, no dependencies) ----------
const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function png(pixels) { // pixels: rows of '#rrggbb' (or '#rrggbbaa') or null (transparent)
  const w = pixels[0].length, h = pixels.length;
  const raw = Buffer.alloc(h * (1 + w * 4));
  pixels.forEach((row, y) => row.forEach((hex, x) => {
    const o = y * (1 + w * 4) + 1 + x * 4;
    if (!hex) return;
    raw[o] = parseInt(hex.slice(1, 3), 16); raw[o + 1] = parseInt(hex.slice(3, 5), 16);
    raw[o + 2] = parseInt(hex.slice(5, 7), 16); raw[o + 3] = hex.length > 7 ? parseInt(hex.slice(7, 9), 16) : 255;
  }));
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
// a texture at textures/<path>.png, drawn with set(x, y, colour) and rect(x, y, w, h, colour)
function texture(path, w, h, draw) {
  const px = Array.from({ length: h }, () => Array(w).fill(null));
  const set = (x, y, c) => { if (x >= 0 && x < w && y >= 0 && y < h) px[y][x] = c; };
  const rect = (x, y, rw, rh, c) => { for (let j = y; j < y + rh; j++) for (let i = x; i < x + rw; i++) set(i, j, c); };
  draw({ set, rect });
  write(join(ASSETS, 'textures', path + '.png'), png(px));
}

// ---------- colours ----------
const WHITE = '#eef1f4', WHITE_SHADE = '#d6dce2', WHITE_EDGE = '#b7c0c9', SEAM = '#c5cdd5';
const DARK = '#3b3f45', DARK2 = '#2c2f34', DARK_EDGE = '#22252a', TREAD = '#5a5f66';
const GREY = '#8d949c', HUB = '#c8ced4', SCREEN = '#08090c';
const GLOW = '#8ff7ff', DIM = '#2f8f96', AMBER = '#ffb547';

// ---------- the bleep (64 x 64) ----------
// Minecraft lays a box of w x h x d out from (u, v) like this: top and bottom in the first d rows, then the four sides.
function box(g, u, v, w, h, d, paint) {
  const faces = {
    top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
    right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h],
  };
  for (const [face, [x0, y0, fw, fh]] of Object.entries(faces)) {
    for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) g.set(x0 + x, y0 + y, paint(face, x, y, fw, fh));
  }
}
// a white shell, a shade darker towards the bottom and at the edges
const shell = (face, x, y, w, h) => {
  if (face === 'bottom') return WHITE_EDGE;
  if (face === 'top') return x === 0 || x === w - 1 || y === 0 || y === h - 1 ? WHITE_SHADE : WHITE;
  if (x === 0 || x === w - 1) return WHITE_SHADE;
  return y >= h - 1 ? WHITE_EDGE : y >= h - 2 ? WHITE_SHADE : WHITE;
};
const dark = (face, x, y, w, h) => (x === 0 || x === w - 1 || y === 0 || y === h - 1 ? DARK_EDGE : DARK);

texture('entity/bleep/bleep', 64, 64, g => {
  // head 12 x 7 x 8, its front one big display with a white rim
  box(g, 0, 0, 12, 7, 8, (face, x, y, w, h) => {
    if (face === 'front') return x === 0 || x === w - 1 || y === 0 || y === h - 1 ? WHITE_EDGE : SCREEN;
    return shell(face, x, y, w, h);
  });
  // the knobs on the sides of the head: a grey disc with a light ring
  box(g, 56, 48, 1, 3, 3, (face, x, y) => (face === 'right' || face === 'left' ? (x === 1 && y === 1 ? GREY : HUB) : GREY));
  // body 8 x 6 x 7, with the seams of its panels
  box(g, 0, 16, 8, 6, 7, (face, x, y, w, h) => {
    const c = shell(face, x, y, w, h);
    if ((face === 'front' || face === 'back') && (y === 2 || (x === 4 && y > 2))) return SEAM;
    return c;
  });
  // the rounded front and back of the body
  box(g, 32, 16, 6, 5, 1, (face, x, y, w, h) => (face === 'front' && y === 2 ? SEAM : shell(face, x, y, w, h)));
  // neck and chassis
  box(g, 32, 30, 4, 2, 4, dark);
  box(g, 0, 30, 8, 2, 8, dark);
  // the tracks: treads along the top, bottom and outside, darker in between
  for (const u of [0, 28]) {
    box(g, u, 41, 3, 4, 11, (face, x, y, w, h) => {
      if (face === 'top' || face === 'bottom') return y % 2 === 0 ? TREAD : DARK2;
      if (face === 'right' || face === 'left') return y === 0 || y === h - 1 ? (x % 2 === 0 ? TREAD : DARK2) : DARK_EDGE;
      return y % 2 === 0 ? TREAD : DARK2; // the ends, round the wheels
    });
  }
  // a wheel: a light hub with a dark notch, so you see it turn
  box(g, 56, 41, 1, 3, 3, (face, x, y) => {
    if (face !== 'right' && face !== 'left') return DARK2;
    if (x === 1 && y === 0) return DARK_EDGE;
    return x === 1 && y === 1 ? GREY : HUB;
  });
  // arms and hands, dark grey like the reference
  box(g, 40, 0, 2, 6, 2, dark);
  box(g, 48, 0, 3, 2, 3, (face, x, y, w, h) => DARK2);
});

// ---------- faces on the display (20 x 10, at (18, 18) in a 128 x 128 texture) ----------
const W = 20, H = 10;
function face(name, draw) {
  texture('entity/bleep/face/' + name, 128, 128, g => {
    const set = (x, y, c = GLOW) => { if (x >= 0 && x < W && y >= 0 && y < H) g.set(18 + x, 18 + y, c); };
    const rect = (x, y, w, h, c = GLOW) => { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) set(i, j, c); };
    draw({ set, rect });
  });
}
// the eyes sit at x 4..6 and 13..15; blink draws them as a line
const EYES = [4, 13];
const roundEyes = (f, y, h) => EYES.forEach(x => { f.rect(x, y, 3, h); });
const blinkEyes = (f, y) => EYES.forEach(x => f.rect(x, y, 3, 1));
const smile = f => { f.rect(8, 8, 4, 1); f.set(7, 7); f.set(12, 7); };

const FACES = {
  happy: (f, blink) => { EYES.forEach(x => blink ? f.rect(x, 4, 3, 1) : f.rect(x + 1, 2, 2, 3)); smile(f); },
  excited: f => { // ^ ^ and a big open smile
    for (const x of [3, 12]) { f.set(x, 4); f.set(x + 1, 3); f.set(x + 2, 2); f.set(x + 3, 3); f.set(x + 4, 4); }
    f.rect(6, 6, 8, 1); f.rect(7, 7, 6, 1); f.rect(8, 8, 4, 1);
  },
  sad: (f, blink) => {
    blink ? blinkEyes(f, 4) : roundEyes(f, 3, 3);
    f.set(4, 1); f.set(5, 1); f.set(6, 0); f.set(13, 0); f.set(14, 1); f.set(15, 1); // brows up in the middle
    f.rect(8, 7, 4, 1); f.set(7, 8); f.set(12, 8); // a frown
  },
  angry: (f, blink) => {
    blink ? blinkEyes(f, 4) : roundEyes(f, 4, 2);
    f.set(3, 1); f.set(4, 2); f.set(5, 2); f.set(6, 3); f.set(13, 3); f.set(14, 2); f.set(15, 2); f.set(16, 1); // brows down in the middle
    for (let x = 7; x <= 12; x++) f.set(x, x % 2 ? 7 : 8); // a grumbly zigzag
  },
  bored: (f, blink) => {
    blink ? blinkEyes(f, 5) : roundEyes(f, 4, 2);
    EYES.forEach(x => f.rect(x - 1, 3, 5, 1, DIM)); // heavy lids
    f.rect(9, 7, 4, 1);
  },
  surprised: f => {
    EYES.forEach(x => { f.rect(x, 1, 3, 4); f.rect(x - 1, 2, 1, 2); f.rect(x + 3, 2, 1, 2); });
    f.set(9, 7); f.set(10, 7); f.set(8, 8); f.set(11, 8); f.set(9, 9); f.set(10, 9); // a little o
  },
  tired: (f, blink) => {
    blink ? blinkEyes(f, 5) : roundEyes(f, 5, 1);
    EYES.forEach(x => f.rect(x, 4, 3, 1, DIM));
    f.rect(8, 8, 4, 1);
    // an almost empty battery in the corner
    f.rect(15, 0, 4, 1, DIM); f.rect(15, 2, 4, 1, DIM); f.set(15, 1, DIM); f.set(18, 1, DIM); f.set(19, 1, DIM); f.set(16, 1, AMBER);
  },
  asleep: f => { // closed eyes and a z, dimmed
    EYES.forEach(x => { f.set(x - 1, 4, DIM); f.rect(x, 5, 3, 1, DIM); f.set(x + 3, 4, DIM); });
    f.rect(9, 8, 2, 1, DIM);
    f.rect(15, 0, 4, 1, DIM); f.set(17, 1, DIM); f.set(16, 2, DIM); f.rect(15, 3, 4, 1, DIM);
  },
};
for (const [name, draw] of Object.entries(FACES)) {
  face(name, f => draw(f, false));
  if (name !== 'asleep' && name !== 'surprised') face(name + '_blink', f => draw(f, true));
}
// the start-up screen: a line flicks on, then a loading bar fills
face('boot_0', f => { f.rect(0, 4, W, 2, DIM); f.rect(6, 4, 8, 2); });
face('boot_1', f => { f.rect(3, 3, 14, 1, DIM); f.rect(3, 7, 14, 1, DIM); f.rect(3, 3, 1, 5, DIM); f.rect(16, 3, 1, 5, DIM); f.rect(5, 5, 4, 1); });
face('boot_2', f => { f.rect(3, 3, 14, 1, DIM); f.rect(3, 7, 14, 1, DIM); f.rect(3, 3, 1, 5, DIM); f.rect(16, 3, 1, 5, DIM); f.rect(5, 5, 10, 1); });

// ---------- items ----------
const item = (name, model) => json(join(ASSETS, 'items', name + '.json'), { model: { type: 'minecraft:model', model } });
// the Bleep Core: a lump of bedrock with a glowing heart
texture('item/bleep_core', 16, 16, g => {
  const BED = ['#3a3a3a', '#4c4c4c', '#5e5e5e', '#2a2a2a'];
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const dx = x - 7.5, dy = y - 7.5;
    if (dx * dx + dy * dy > 42) continue;
    g.set(x, y, BED[(x * 7 + y * 13 + ((x * y) % 5)) % 4]);
  }
  g.rect(6, 6, 4, 4, '#2de2e6'); g.rect(7, 7, 2, 2, GLOW); g.set(7, 6, '#ffffff');
  for (const [x, y] of [[5, 7], [10, 8], [8, 5], [7, 10]]) g.set(x, y, '#1f9aa0'); // light leaking from the cracks
});
json(join(ASSETS, 'models/item/bleep_core.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'bleep:item/bleep_core' } });
item('bleep_core', 'bleep:item/bleep_core');

// ---------- the homebase ----------
// a charging pad (1..15 across, 2 high) with a post at the back (5..11 across, 12..15 deep, up to 14): the front faces north
texture('block/homebase_pad', 16, 16, g => {
  g.rect(0, 0, 16, 16, DARK);
  g.rect(1, 1, 14, 14, WHITE_EDGE); g.rect(2, 2, 12, 12, DARK);
  g.rect(3, 3, 2, 10, TREAD); g.rect(11, 3, 2, 10, TREAD);       // where the tracks stand
  g.rect(7, 6, 2, 4, '#1f9aa0'); g.set(7, 6, '#2de2e6');           // the contact in the middle
});
texture('block/homebase_side', 16, 16, g => {
  g.rect(0, 0, 16, 16, WHITE);
  for (let x = 0; x < 16; x++) { g.set(x, 0, WHITE_SHADE); g.set(x, 15, WHITE_EDGE); g.set(x, 14, DARK); }
  g.rect(0, 6, 16, 1, SEAM);
});
for (const [name, light] of [['homebase_post', '#1a3a3d'], ['homebase_post_on', GLOW]]) {
  texture('block/' + name, 16, 16, g => {
    g.rect(0, 0, 16, 16, WHITE);
    g.rect(5, 0, 6, 1, WHITE_SHADE);
    g.rect(7, 3, 2, 8, SCREEN); g.rect(7, 4, 2, 6, light);           // the charge light
    g.set(7, 1, GREY); g.set(8, 1, GREY);
  });
}
const PAD = { from: [1, 0, 1], to: [15, 2, 15], faces: {
  up: { uv: [1, 1, 15, 15], texture: '#pad' }, down: { uv: [1, 1, 15, 15], texture: '#side' },
  north: { uv: [1, 14, 15, 16], texture: '#side' }, south: { uv: [1, 14, 15, 16], texture: '#side' },
  east: { uv: [1, 14, 15, 16], texture: '#side' }, west: { uv: [1, 14, 15, 16], texture: '#side' } } };
const POST = { from: [5, 2, 12], to: [11, 14, 15], faces: {
  north: { uv: [5, 2, 11, 14], texture: '#post' }, south: { uv: [5, 2, 11, 14], texture: '#side' },
  east: { uv: [12, 2, 15, 14], texture: '#side' }, west: { uv: [1, 2, 4, 14], texture: '#side' },
  up: { uv: [5, 12, 11, 15], texture: '#side' } } };
const homebaseTextures = post => ({ particle: 'bleep:block/homebase_side', pad: 'bleep:block/homebase_pad', side: 'bleep:block/homebase_side', post: 'bleep:block/' + post });
json(join(ASSETS, 'models/block/homebase.json'), { parent: 'minecraft:block/block', textures: homebaseTextures('homebase_post'), elements: [PAD, POST] });
json(join(ASSETS, 'models/block/homebase_charging.json'), { parent: 'bleep:block/homebase', textures: homebaseTextures('homebase_post_on') });
const YAW = { north: 0, east: 90, south: 180, west: 270 };
json(join(ASSETS, 'blockstates/homebase.json'), { variants: Object.fromEntries(Object.entries(YAW).flatMap(([facing, y]) => [false, true].map(on =>
  ['charging=' + on + ',facing=' + facing, { model: 'bleep:block/homebase' + (on ? '_charging' : ''), ...(y ? { y } : {}) }]))) });
item('homebase', 'bleep:block/homebase');
json(join(DATA, 'recipe/homebase.json'), {
  type: 'minecraft:crafting_shaped', category: 'misc',
  key: { L: 'minecraft:lightning_rod', I: 'minecraft:iron_ingot', R: 'minecraft:redstone_block', S: 'minecraft:smooth_stone_slab' },
  pattern: [' L ', 'IRI', 'SSS'], result: { count: 1, id: 'bleep:homebase' },
});
json(join(DATA, 'loot_table/blocks/homebase.json'), { type: 'minecraft:block', random_sequence: 'bleep:blocks/homebase',
  pools: [{ rolls: 1, conditions: [{ condition: 'minecraft:survives_explosion' }], entries: [{ type: 'minecraft:item', name: 'bleep:homebase' }] }] });
json(join(ROOT, 'data/minecraft/tags/block/mineable/pickaxe.json'), { values: ['bleep:homebase'] });

// ---------- what bleeps think (thought.bleep.<kind>.<n>; the counts are THOUGHTS in BleepEntity) ----------
const THOUGHTS = {
  happy: ['Beep boop. All systems happy.', 'I like it here. I like it everywhere, actually.', 'My tracks are clean. My screen is clean. Life is good.',
    'Is that a new block? I love new blocks.', 'Following you is my favourite hobby.'],
  excited: ['BLEEP!', 'Best. Day. Ever.', 'Again! Do that again!'],
  sad: ['My screen feels a bit dim today.', 'I will be fine. Probably.', 'Sad bleep noises.'],
  angry: ['Hey! I felt that.', 'Writing this down in my log. Twice.', 'Grr. That is my angry beep.'],
  bored: ['Counting blocks. 1. 2. 3... Still 3.', 'I could be playing tic-tac-toe right now.', 'Is it adventure time yet?', 'Nothing is happening. I checked twice.'],
  surprised: ['Whoa!', 'What was THAT?'],
  tired: ['Need... power...', 'Battery low. Optimism also low.', 'If I stop moving, that is a feature.'],
  asleep: ['Zzz... electric sheep... zzz', 'Zzz... a cow with wheels... zzz', 'Zzz...'],
  charging: ['Mmm. Fresh electrons.', 'Charging. Please do not unplug.', 'Home sweet homebase.'],
  llama: ['It is watching. It knows. Do not move.', 'Llama. LLAMA. Hiding now.'],
};
const thoughtLines = Object.fromEntries(Object.entries(THOUGHTS).flatMap(([kind, lines]) => lines.map((line, n) => ['thought.bleep.' + kind + '.' + n, line])));

// ---------- language ----------
json(join(ASSETS, 'lang/en_us.json'), {
  'itemGroup.bleep': 'Bleep',
  'entity.bleep.bleep': 'Bleep',
  'item.bleep.bleep_core': 'Bleep Core',
  'message.bleep.stay': '%s: Stay',
  'message.bleep.follow': '%s: Follow',
  'message.bleep.asleep': '%s is out of battery. Zzz...',
  'block.bleep.homebase': 'Homebase',
  'message.bleep.homebase_of': 'Homebase of %s',
  'message.bleep.homebase_empty': 'No bleep of yours nearby: place it close to your bleep.',
  'message.bleep.moved_in': '%s moved in!',
  'message.bleep.no_home': '%s has no homebase yet. Place one near it.',
  'screen.bleep.follow': 'Follow', 'screen.bleep.stay': 'Stay', 'screen.bleep.go_home': 'Go Home',
  'screen.bleep.mood': 'Mood', 'screen.bleep.battery': 'Battery', 'screen.bleep.bond': 'Bond',
  'emotion.bleep.happy': 'Happy', 'emotion.bleep.excited': 'Excited', 'emotion.bleep.sad': 'Sad', 'emotion.bleep.angry': 'Angry',
  'emotion.bleep.bored': 'Bored', 'emotion.bleep.surprised': 'Surprised', 'emotion.bleep.tired': 'Tired', 'emotion.bleep.asleep': 'Asleep',
  'emotion.bleep.boot': 'Starting up',
  'activity.bleep.following': 'Following you', 'activity.bleep.staying': 'Staying here', 'activity.bleep.going_home': 'Driving home',
  'activity.bleep.charging': 'Charging at home', 'activity.bleep.at_home': 'Resting at home', 'activity.bleep.sleeping': 'Switched off',
  'activity.bleep.hiding': 'Hiding from a llama', 'activity.bleep.starting': 'Starting up',
  'bond.bleep.new': 'New', 'bond.bleep.friend': 'Friend', 'bond.bleep.buddy': 'Buddy', 'bond.bleep.best_friend': 'Best friend',
  ...thoughtLines,
});

console.log('Bleep assets written.');
