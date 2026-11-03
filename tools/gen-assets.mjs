// Generates Bleep's textures, item models and language file. Run from the project root:  node tools/gen-assets.mjs
// The bleep's texture follows the boxes in client/BleepModel.java (64 x 64, Minecraft's usual box layout); the faces
// on its display are 128 x 128, so the display (10 x 5 on the model) gets 20 x 10 pixels.
import { existsSync, mkdirSync, readdirSync, unlinkSync, writeFileSync } from 'node:fs';
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

// ---------- what a bleep wears (64 x 64), from the boxes in client/AccessoryModel.java ----------
// the little boat on the water: planks all over (BoatModel), a dark rim along the top of each board
texture('entity/bleep/boat', 128, 64, g => {
  const PLANK = '#9c6b3a', PLANK_DARK = '#7a5029', PLANK_LIGHT = '#b9824b', SEAM_DARK = '#5a3a1c';
  g.rect(0, 0, 128, 64, PLANK);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 128; x++) {
    if (y % 4 === 3) g.set(x, y, SEAM_DARK);                         // between the boards
    else if ((x * 7 + y * 13) % 23 === 0) g.set(x, y, PLANK_DARK);   // the grain
    else if ((x * 5 + y * 11) % 29 === 0) g.set(x, y, PLANK_LIGHT);
  }
  for (let x = 0; x < 128; x++) for (const y of [20, 44]) g.set(x, y, '#2de2e6'); // a cyan stripe along the top rim
});
// the trailer (TrailerModel): planks for the cart at the top, a chest (box at 0, 32) and the wheels (box at 32, 32) below
texture('entity/bleep/trailer', 64, 64, g => {
  const PLANK = '#9c6b3a', PLANK_DARK = '#7a5029', PLANK_LIGHT = '#b9824b', SEAM_DARK = '#5a3a1c';
  g.rect(0, 0, 64, 32, PLANK);
  for (let y = 0; y < 32; y++) for (let x = 0; x < 64; x++) {
    if (y % 4 === 3) g.set(x, y, SEAM_DARK);
    else if ((x * 7 + y * 13) % 23 === 0) g.set(x, y, PLANK_DARK);
    else if ((x * 5 + y * 11) % 29 === 0) g.set(x, y, PLANK_LIGHT);
  }
  for (let x = 0; x < 64; x++) g.set(x, 12, '#2de2e6'); // a cyan stripe along the top of the sides
  const OAK = '#a8743c', OAK_DARK = '#6b4523', LATCH = '#c9d1d9';
  box(g, 0, 32, 7, 5, 7, (face, x, y, w, h) => {
    if (face === 'top' || face === 'bottom') return x === 0 || y === 0 || x === w - 1 || y === h - 1 ? OAK_DARK : OAK;
    if (x === 0 || x === w - 1 || y === 0 || y === h - 1 || y === 1) return OAK_DARK;  // the rim and the lid line
    if (face === 'back' && x === 3 && y === 2) return LATCH;                          // the latch, at the back
    return OAK;
  });
  box(g, 32, 32, 1, 4, 4, (face, x, y) => {
    if (face !== 'right' && face !== 'left') return DARK2;
    return (x === 1 || x === 2) && (y === 1 || y === 2) ? HUB : x === 0 && y === 0 ? DARK_EDGE : DARK;
  });
});
// the trailer item: a little brown cart with a chest, two dark wheels and the drawbar
texture('item/trailer', 16, 16, g => {
  g.rect(3, 4, 9, 5, '#a8743c'); g.rect(3, 4, 9, 1, '#6b4523'); g.rect(7, 6, 1, 2, '#c9d1d9');   // the chest
  g.rect(2, 9, 11, 3, '#9c6b3a'); g.rect(2, 11, 11, 1, '#5a3a1c'); g.rect(2, 9, 11, 1, '#2de2e6'); // the cart
  g.rect(13, 10, 2, 1, '#5a3a1c');                                                                   // the drawbar
  for (const x of [3, 9]) { g.rect(x, 12, 3, 3, DARK); g.set(x + 1, 13, HUB); }                       // the wheels
});
// the sled on snow (SledModel): red-painted wood on two steel runners curled up at the front
texture('entity/bleep/sled', 64, 32, g => {
  g.rect(0, 0, 64, 16, '#c8323c');
  for (let x = 0; x < 64; x++) { g.set(x, 3, '#9e2530'); g.set(x, 7, '#9e2530'); g.set(x, 11, '#9e2530'); g.set(x, 15, '#9e2530'); }
  g.rect(0, 16, 64, 16, '#c9d1d9');
  for (let x = 0; x < 64; x++) { g.set(x, 16, '#eef2f5'); g.set(x, 31, '#7d8794'); }
});
texture('entity/bleep/accessories', 64, 64, g => {
  const MAG = '#ff4f8b', CYN = '#2de2e6', YEL = '#ffd23f', BLK = '#1b1d22', BLK2 = '#2c2f36', RED = '#d6283b', RED2 = '#a01a2a';
  // party hat: three tiers in magenta and cyan stripes, a white pompom on top
  box(g, 0, 0, 6, 1, 6, (face, x) => (x % 2 ? MAG : CYN));
  box(g, 24, 0, 4, 2, 4, (face, x, y) => ((x + y) % 2 ? CYN : MAG));
  box(g, 40, 0, 2, 2, 2, () => WHITE);
  // top hat: black, a grey band round the crown
  box(g, 0, 8, 8, 1, 8, (face, x, y, w, h) => (x === 0 || y === 0 || x === w - 1 || y === h - 1 ? BLK : BLK2));
  box(g, 32, 8, 6, 5, 6, (face, x, y, w, h) => (face !== 'top' && face !== 'bottom' && y === h - 2 ? GREY : BLK));
  // antenna: a grey rod, a red ball, a red bow
  box(g, 0, 20, 1, 6, 1, () => GREY);
  box(g, 4, 20, 2, 2, 2, (face, x, y) => (x === 0 && y === 0 ? '#ff8a96' : RED));
  box(g, 12, 20, 2, 2, 1, (face, x, y) => ((x + y) % 2 ? RED : RED2));
  // stickers: a white edge, the picture on the front
  const sticker = (u, pic) => box(g, u, 30, 3, 3, 1, (face, x, y) => (face === 'front' ? pic[y][x] : WHITE));
  sticker(0, [[WHITE, YEL, WHITE], [YEL, YEL, YEL], [YEL, WHITE, YEL]]);   // a star
  sticker(8, [[MAG, WHITE, MAG], [MAG, MAG, MAG], [WHITE, MAG, WHITE]]);   // a heart
  sticker(16, [[WHITE, YEL, YEL], [WHITE, YEL, WHITE], [YEL, YEL, WHITE]]); // a lightning bolt
  // a crown: gold, darker on the band's edges, a red gem
  const GOLD = '#ffd23f', GOLD2 = '#d9a93a';
  box(g, 24, 20, 6, 1, 6, (face, x, y, w) => (face === 'top' || face === 'bottom' ? GOLD2 : x % 2 ? GOLD : GOLD2));
  box(g, 48, 20, 1, 2, 1, (face, x, y) => (y === 0 ? GOLD : GOLD2));
  box(g, 52, 20, 1, 1, 1, () => '#ff4f8b');
  // sunglasses: black frame, dark lenses with a shine
  box(g, 0, 36, 12, 1, 1, () => BLK);
  box(g, 26, 36, 3, 2, 1, (face, x, y) => (face === 'front' && x === 0 && y === 0 ? '#6a7d90' : '#121419'));
  box(g, 34, 36, 1, 1, 3, () => BLK2);
  // a leather jacket: brown, seams, a silver zip down the front, a darker collar and cuffs
  const LEA = '#5a3825', LEA2 = '#47291b', LEA3 = '#2e1a10', ZIP = '#c9ced6';
  box(g, 0, 40, 8, 6, 7, (face, x, y, w, h) => (y === h - 1 ? LEA3 : (x + y) % 5 === 0 ? LEA2 : LEA));
  box(g, 30, 40, 6, 5, 1, (face, x, y, w, h) => (face === 'front' && x === 3 ? ZIP : face === 'front' && x === 2 ? LEA3 : y === h - 1 ? LEA3 : LEA));
  box(g, 44, 40, 4, 1, 4, () => LEA3);
  box(g, 30, 47, 2, 4, 2, (face, x, y, w, h) => (y === h - 1 ? LEA3 : LEA));
  // a cape: red outside, darker inside, a gold edge along the top
  box(g, 0, 54, 8, 9, 1, (face, x, y) => (face === 'back' ? (y === 0 ? GOLD : '#c0202f') : face === 'front' ? '#7a121c' : '#9a1724'));
  // a black bow tie with a grey edge, and a striped red and white scarf
  box(g, 20, 54, 2, 2, 1, (face, x, y) => ((x + y) % 3 === 0 ? BLK2 : BLK));
  box(g, 28, 54, 2, 1, 1, () => BLK2);
  box(g, 34, 54, 8, 1, 7, (face, x, y) => ((x >> 1) % 2 ? RED : WHITE));
  box(g, 20, 59, 1, 4, 1, (face, x, y) => (y % 2 ? WHITE : RED));
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
  stubborn: (f, blink) => { // one eye squinting, tongue out
    if (blink) blinkEyes(f, 4); else { f.rect(5, 2, 2, 3); f.rect(13, 4, 3, 1); }
    f.rect(7, 7, 6, 1); f.rect(10, 8, 2, 2, '#ff4f8b');
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
// glitching: the face torn into bands, two frames
face('glitch_0', f => {
  f.rect(6, 2, 2, 3); f.rect(15, 1, 2, 3); f.rect(0, 5, 7, 1, DIM); f.rect(11, 6, 9, 1); f.rect(9, 8, 5, 1);
  for (const [x, y] of [[2, 1], [18, 8], [4, 9], [12, 3], [17, 5]]) f.set(x, y, AMBER);
});
face('glitch_1', f => {
  f.rect(3, 3, 2, 3); f.rect(12, 2, 2, 3); f.rect(8, 4, 12, 1, DIM); f.rect(0, 7, 8, 1); f.rect(6, 8, 6, 1);
  for (const [x, y] of [[16, 0], [1, 6], [10, 9], [5, 1], [19, 3]]) f.set(x, y, '#ff4f8b');
});
// a screen that is still off, while the bleep is being put together
face('off', () => {});
// the start-up screen: a line flicks on, then a loading bar fills
face('boot_0', f => { f.rect(0, 4, W, 2, DIM); f.rect(6, 4, 8, 2); });
face('boot_1', f => { f.rect(3, 3, 14, 1, DIM); f.rect(3, 7, 14, 1, DIM); f.rect(3, 3, 1, 5, DIM); f.rect(16, 3, 1, 5, DIM); f.rect(5, 5, 4, 1); });
face('boot_2', f => { f.rect(3, 3, 14, 1, DIM); f.rect(3, 7, 14, 1, DIM); f.rect(3, 3, 1, 5, DIM); f.rect(16, 3, 1, 5, DIM); f.rect(5, 5, 10, 1); });

// ---------- items ----------
const item = (name, model) => json(join(ASSETS, 'items', name + '.json'), { model: { type: 'minecraft:model', model } });
// the Bleep Core: a lump of something not from here, deep violet, with a glowing heart
texture('item/bleep_core', 16, 16, g => {
  const BED = ['#2a2350', '#3a2f6b', '#4a3d85', '#1c1838'];
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
// the other parts: its display (a black screen in a white rim, a face glowing on it), its tracks, its arms
texture('item/bleep_display', 16, 16, g => {
  g.rect(1, 3, 14, 10, WHITE_EDGE); g.rect(2, 4, 12, 8, SCREEN);
  g.rect(1, 3, 14, 1, WHITE); g.rect(1, 3, 1, 10, WHITE);
  g.rect(5, 6, 2, 2, GLOW); g.rect(9, 6, 2, 2, GLOW); g.rect(6, 9, 4, 1, GLOW);
});
texture('item/bleep_tracks', 16, 16, g => {
  for (const y of [2, 9]) { // two tracks, one above the other
    g.rect(1, y, 14, 5, DARK2);
    for (let x = 1; x < 15; x += 2) { g.set(x, y, TREAD); g.set(x, y + 4, TREAD); }
    for (const x of [3, 7, 11]) { g.rect(x, y + 1, 3, 3, HUB); g.set(x + 1, y + 2, GREY); g.set(x + 1, y + 1, DARK_EDGE); }
  }
});
texture('item/bleep_arms', 16, 16, g => {
  for (const [x, flip] of [[3, 0], [10, 1]]) { // two arms with their hands
    g.rect(x, 1, 3, 9, DARK); g.rect(x, 1, 1, 9, DARK_EDGE);
    g.rect(x - 1, 10, 5, 2, DARK2); g.rect(x - 1, 12, 2, 3, DARK2); g.rect(x + 2, 12, 2, 3, DARK2);
    g.set(x + 1 + (flip ? 1 : 0), 2, GREY);
  }
});
// the power bank: a dark battery with a copper terminal and three cyan bars of charge
texture('item/power_bank', 16, 16, g => {
  g.rect(4, 3, 8, 12, DARK2); g.rect(5, 4, 6, 10, DARK); g.rect(4, 3, 8, 1, DARK_EDGE);
  g.rect(6, 1, 4, 2, '#c87533'); g.rect(7, 1, 2, 1, '#e89a5c');
  for (const y of [5, 8, 11]) g.rect(6, y, 4, 2, y === 5 ? '#1f9aa0' : GLOW);
});
// a whistle: a silver tin whistle with a cyan lanyard ring
texture('item/whistle', 16, 16, g => {
  const TIN = '#c9d1d9', TIN_DARK = '#7d8794', TIN_LIGHT = '#eef2f5';
  for (let i = 0; i < 9; i++) { g.rect(3 + i, 11 - i, 2, 2, TIN); g.set(3 + i, 12 - i, TIN_DARK); g.set(4 + i, 11 - i, TIN_LIGHT); }
  g.rect(11, 2, 3, 3, TIN); g.set(13, 4, TIN_DARK); g.set(11, 2, TIN_LIGHT);   // the mouthpiece
  g.set(7, 7, DARK); g.set(6, 8, DARK);                                          // the hole
  g.rect(1, 12, 3, 1, GLOW); g.rect(1, 13, 1, 2, GLOW); g.rect(1, 15, 3, 1, GLOW); g.set(3, 14, GLOW); // the lanyard ring
});
// the sound button on its info screen: a speaker with waves (on), or with a red cross (off)
for (const on of [true, false]) {
  texture('gui/sound_' + (on ? 'on' : 'off'), 16, 16, g => {
    const BODY = '#d6dce2', EDGE = '#8d949c';
    g.rect(2, 6, 3, 4, BODY); g.rect(5, 5, 1, 6, BODY); g.rect(6, 4, 1, 8, BODY); g.rect(7, 3, 1, 10, BODY); g.rect(8, 2, 1, 12, EDGE);
    if (on) {
      g.rect(10, 6, 1, 4, GLOW); g.set(11, 5, GLOW); g.set(11, 10, GLOW); g.rect(12, 6, 1, 4, GLOW);
      g.rect(13, 4, 1, 8, GLOW); g.set(12, 3, GLOW); g.set(12, 12, GLOW);
    } else {
      for (let i = 0; i < 5; i++) { g.set(10 + i, 5 + i, '#ff4f8b'); g.set(14 - i, 5 + i, '#ff4f8b'); }
    }
  });
}
// the Bleep Tracker: a little white handheld with a black screen, a cyan dot on it and an antenna
texture('item/tracker', 16, 16, g => {
  g.rect(3, 3, 10, 12, WHITE_EDGE); g.rect(4, 4, 8, 10, WHITE);
  g.rect(5, 5, 6, 5, SCREEN); g.set(8, 7, GLOW); g.set(6, 6, DIM); g.set(9, 8, DIM);
  g.rect(5, 11, 2, 2, '#ff4f8b'); g.rect(9, 11, 2, 2, DARK);
  g.rect(11, 0, 1, 3, DARK); g.set(11, 0, GLOW);
});
// the magnet button on its info screen: a red horseshoe magnet with silver tips
texture('gui/magnet', 16, 16, g => {
  const RED = '#d6283b', RED_DARK = '#a01a2a', TIP = '#d6dce2', TIP_DARK = '#8d949c';
  g.rect(3, 2, 4, 9, RED); g.rect(9, 2, 4, 9, RED); g.rect(3, 10, 10, 3, RED); g.rect(5, 12, 6, 2, RED);
  g.rect(6, 2, 1, 9, RED_DARK); g.rect(12, 2, 1, 9, RED_DARK); g.rect(4, 13, 8, 1, RED_DARK);
  g.rect(3, 1, 4, 3, TIP); g.rect(9, 1, 4, 3, TIP); g.rect(6, 1, 1, 3, TIP_DARK); g.rect(12, 1, 1, 3, TIP_DARK);
});
// the accessories as items
texture('item/party_hat', 16, 16, g => {
  for (let y = 3; y < 15; y++) { const half = Math.floor((y - 3) / 2); for (let x = 7 - half; x <= 8 + half; x++) g.set(x, y, (x + y) % 3 ? '#ff4f8b' : '#2de2e6'); }
  g.rect(7, 1, 2, 2, WHITE);
});
texture('item/top_hat', 16, 16, g => { g.rect(4, 3, 8, 9, '#2c2f36'); g.rect(4, 9, 8, 1, GREY); g.rect(1, 12, 14, 2, '#1b1d22'); });
texture('item/crown', 16, 16, g => {
  g.rect(3, 9, 10, 4, '#ffd23f'); g.rect(3, 12, 10, 1, '#d9a93a');
  for (const x of [3, 7, 11]) { g.rect(x, 5, 2, 4, '#ffd23f'); g.set(x, 4, '#ffd23f'); }
  g.set(5, 10, '#ff4f8b'); g.set(8, 10, '#2de2e6'); g.set(10, 10, '#ff4f8b');
});
texture('item/sunglasses', 16, 16, g => {
  g.rect(1, 6, 14, 1, '#1b1d22');
  g.rect(2, 7, 5, 3, '#121419'); g.rect(9, 7, 5, 3, '#121419'); g.set(3, 7, '#6a7d90'); g.set(10, 7, '#6a7d90');
  g.set(0, 6, '#2c2f36'); g.set(15, 6, '#2c2f36');
});
texture('item/leather_jacket', 16, 16, g => {
  g.rect(4, 3, 8, 11, '#5a3825'); g.rect(1, 4, 3, 7, '#5a3825'); g.rect(12, 4, 3, 7, '#5a3825');
  g.rect(1, 10, 3, 1, '#2e1a10'); g.rect(12, 10, 3, 1, '#2e1a10'); g.rect(4, 13, 8, 1, '#2e1a10');
  g.rect(5, 2, 2, 3, '#2e1a10'); g.rect(9, 2, 2, 3, '#2e1a10'); // the collar
  g.rect(8, 4, 1, 9, '#c9ced6'); g.rect(7, 5, 1, 8, '#47291b');
});
texture('item/cape', 16, 16, g => {
  for (let y = 2; y < 15; y++) { const half = 3 + Math.floor((y - 2) / 3); g.rect(8 - half, y, half * 2, 1, y === 2 ? '#ffd23f' : '#c0202f'); }
  g.rect(7, 2, 2, 2, '#ffd23f'); g.rect(4, 6, 1, 8, '#9a1724'); g.rect(11, 6, 1, 8, '#9a1724');
});
texture('item/bow_tie', 16, 16, g => {
  const rows = ['##.....##', '###...###', '####.####', '#########', '####.####', '###...###', '##.....##'];
  rows.forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') g.set(3 + x, 4 + y, (x + y) % 4 ? '#1b1d22' : '#2c2f36'); }));
  g.rect(6, 6, 3, 3, '#2c2f36');
});
texture('item/scarf', 16, 16, g => {
  for (let x = 2; x < 14; x++) g.rect(x, 4, 1, 3, x % 2 ? '#d6283b' : WHITE);
  for (let y = 7; y < 14; y++) g.rect(10, y, 3, 1, y % 2 ? WHITE : '#d6283b');
  g.rect(10, 14, 1, 1, '#d6283b'); g.rect(12, 14, 1, 1, '#d6283b');
});
texture('item/antenna', 16, 16, g => { g.rect(7, 4, 2, 11, GREY); g.rect(6, 1, 4, 3, '#d6283b'); g.rect(3, 7, 4, 3, '#d6283b'); g.rect(9, 7, 4, 3, '#a01a2a'); });
for (const [name, colour, pic] of [['sticker_star', '#ffd23f', ['...#...', '..###..', '#######', '.#####.', '..###..', '.##.##.', '##...##']],
  ['sticker_heart', '#ff4f8b', ['.##.##.', '#######', '#######', '.#####.', '..###..', '...#...', '.......']],
  ['sticker_bolt', '#ffd23f', ['...###.', '..###..', '.###...', '######.', '...###.', '..###..', '.##....']]]) {
  texture('item/' + name, 16, 16, g => {
    g.rect(3, 3, 11, 11, WHITE);
    pic.forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') g.set(5 + x, 5 + y, colour); }));
  });
}
// a ball to play with: half pink, half cyan, a white band round the middle, a shine on top
texture('item/ball', 16, 16, g => {
  const rows = ['....pppppp....', '..pppppppppp..', '.ppwwpppppppp.', '.ppwpppppppppp', 'pppppppppppppp', 'pppppppppppppp', 'wwwwwwwwwwwwww',
    'cccccccccccccc', 'cccccccccccccc', 'cccccccccccccc', '.cccccccccccdc', '.ccccccccccddc', '..ccccccccddc.', '....cccccc....'];
  const C = { p: '#ff4f8b', w: '#eef1f4', c: '#2de2e6', d: '#1f9aa0' };
  rows.forEach((row, y) => [...row].forEach((ch, x) => { if (C[ch]) g.set(1 + x, 1 + y, C[ch]); }));
  g.set(13, 1 + 4, '#c93a6c'); g.set(13, 1 + 5, '#c93a6c');
});
// upgrade chips: a green circuit board with gold pins and a light in its colour
for (const [name, led, mark] of [['speed_chip', '#ffb547', ['..#', '.#.', '###', '.#.', '#..']], ['shield_chip', '#2de2e6', ['###', '###', '###', '.#.', '...']], ['battery_chip', '#ff4f8b', ['.#.', '###', '###', '###', '###']]]) {
  texture('item/' + name, 16, 16, g => {
    for (let x = 4; x <= 11; x += 2) { g.set(x, 2, '#d9a93a'); g.set(x, 13, '#d9a93a'); } // the pins
    g.rect(3, 3, 10, 10, '#1f6b3a'); g.rect(3, 3, 10, 1, '#2e8a4c'); g.rect(3, 12, 10, 1, '#154d29');
    g.rect(4, 7, 3, 1, '#d9a93a'); g.rect(9, 5, 1, 3, '#d9a93a'); // traces
    mark.forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') g.set(8 + x, 6 + y, led); }));
    g.rect(4, 4, 2, 2, '#0e3a1e');
  });
}
for (const part of ['speed_chip', 'shield_chip', 'battery_chip', 'ball', 'bleep_display', 'bleep_tracks', 'bleep_arms', 'power_bank', 'party_hat', 'top_hat', 'antenna', 'sticker_star', 'sticker_heart', 'sticker_bolt', 'crown', 'sunglasses', 'leather_jacket', 'cape', 'bow_tie', 'scarf', 'whistle', 'tracker', 'trailer']) {
  json(join(ASSETS, 'models/item/' + part + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'bleep:item/' + part } });
  item(part, 'bleep:item/' + part);
}

// ---------- sounds (tools/make-sounds.mjs makes the ogg files) ----------
const VOICES = { happy: 'Bleep bloops', excited: 'Bleep cheers', sad: 'Bleep sighs', angry: 'Bleep grumbles', bored: 'Bleep hums',
  surprised: 'Bleep gasps', tired: 'Bleep yawns', asleep: 'Bleep powers down', boot: 'Bleep starts up', stubborn: 'Bleep huffs',
  glitch: 'Bleep glitches', hurt: 'Bleep buzzes', assemble: 'Bleep parts clunk' };
// every take of a sound that tools/make-sounds.mjs made (<name>.ogg or <name>_1.ogg, _2 ...): Minecraft picks one
const SOUND_DIR = join(ASSETS, 'sounds/entity/bleep');
const takesOf = (name) => readdirSync(SOUND_DIR).filter((file) => new RegExp('^' + name + '(_\\d+)?\\.ogg$').test(file)).sort().map((file) => 'bleep:entity/bleep/' + file.slice(0, -4));
json(join(ASSETS, 'sounds.json'), Object.fromEntries(Object.keys(VOICES).map((name) =>
  ['entity.bleep.' + name, { subtitle: 'subtitles.bleep.' + name, sounds: takesOf(name) }])));
const subtitleLines = Object.fromEntries(Object.entries(VOICES).map(([name, text]) => ['subtitles.bleep.' + name, text]));

// ---------- the parts: all crafted; the core is expensive, and also found (BleepPartsModifier) ----------
const shaped = (name, pattern, key) => json(join(DATA, 'recipe/' + name + '.json'),
  { type: 'minecraft:crafting_shaped', category: 'misc', key, pattern, result: { count: 1, id: 'bleep:' + name } });
// what it wears: a party hat of paper with a woollen pompom, a top hat of black wool, an antenna with a red bow,
// and stickers (four at a time) of paper and slime with a dye
shaped('party_hat', [' W ', ' P ', 'PPP'], { W: 'minecraft:white_wool', P: 'minecraft:paper' });
shaped('top_hat', [' W ', ' W ', 'WWW'], { W: 'minecraft:black_wool' });
shaped('antenna', ['RSR', ' N ', ' N '], { R: 'minecraft:red_dye', S: 'minecraft:string', N: 'minecraft:iron_nugget' });
// and the cool things (owner, 30 Oct 2026): sunglasses, a leather jacket, a cape, a bow tie, a scarf, a crown
shaped('sunglasses', ['N N', 'GNG'], { N: 'minecraft:iron_nugget', G: 'minecraft:black_stained_glass_pane' });
shaped('leather_jacket', ['L L', 'LNL', 'LLL'], { L: 'minecraft:leather', N: 'minecraft:iron_nugget' });
shaped('cape', [' G ', 'WWW', 'WWW'], { G: 'minecraft:gold_nugget', W: 'minecraft:red_wool' });
shaped('bow_tie', ['WSW'], { W: 'minecraft:black_wool', S: 'minecraft:string' });
shaped('scarf', ['RWR', 'W  '], { R: 'minecraft:red_wool', W: 'minecraft:white_wool' });
shaped('crown', ['GDG', 'GGG'], { G: 'minecraft:gold_ingot', D: 'minecraft:diamond' });
for (const [name, dye] of [['sticker_star', 'yellow_dye'], ['sticker_heart', 'pink_dye'], ['sticker_bolt', 'light_blue_dye']]) {
  json(join(DATA, 'recipe/' + name + '.json'), { type: 'minecraft:crafting_shapeless', category: 'misc',
    ingredients: ['minecraft:paper', 'minecraft:slime_ball', 'minecraft:' + dye], result: { count: 4, id: 'bleep:' + name } });
}
// a power bank: copper on top, iron round two blocks of redstone
shaped('power_bank', [' C ', 'IRI', 'IRI'], { C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot', R: 'minecraft:redstone_block' });
// the Bleep Tracker: a compass inside, redstone, and an iron case
json(join(DATA, 'recipe/tracker.json'), { type: 'minecraft:crafting_shapeless', category: 'misc',
  ingredients: ['minecraft:compass', 'minecraft:redstone', 'minecraft:iron_ingot'], result: { count: 1, id: 'bleep:tracker' } });
// a whistle: an iron ingot, a nugget for the pea and string for the lanyard
json(join(DATA, 'recipe/whistle.json'), { type: 'minecraft:crafting_shapeless', category: 'misc',
  ingredients: ['minecraft:iron_ingot', 'minecraft:iron_nugget', 'minecraft:string'], result: { count: 1, id: 'bleep:whistle' } });
// a bouncy ball: wool round a slimeball
shaped('ball', [' W ', 'WSW', ' W '], { W: 'minecraft:white_wool', S: 'minecraft:slime_ball' });
// upgrade chips: gold and redstone on a board, and what makes each one special
for (const [name, special] of [['speed_chip', 'minecraft:sugar'], ['shield_chip', 'minecraft:shield'], ['battery_chip', 'minecraft:redstone_block']]) {
  shaped(name, [' N ', 'RXR', ' G '], { N: 'minecraft:iron_nugget', R: 'minecraft:redstone', X: special, G: 'minecraft:gold_ingot' });
}
// the core, the expensive way: obsidian (the nearest thing to bedrock) round diamonds and a block of diamond, 13 in all
shaped('bleep_core', ['ODO', 'DBD', 'ODO'], { O: 'minecraft:obsidian', D: 'minecraft:diamond', B: 'minecraft:diamond_block' });
// a black screen in an iron frame, redstone to light it
shaped('bleep_display', ['III', 'IGI', 'IRI'], { I: 'minecraft:iron_ingot', G: 'minecraft:black_stained_glass_pane', R: 'minecraft:redstone' });
// rubber treads of dried kelp round iron wheels, redstone for the motor
shaped('bleep_tracks', ['KKK', 'IRI', 'KKK'], { K: 'minecraft:dried_kelp', I: 'minecraft:iron_ingot', R: 'minecraft:redstone' });
// two iron arms with nugget fingers, redstone between the shoulders
shaped('bleep_arms', ['IRI', 'I I', 'N N'], { I: 'minecraft:iron_ingot', R: 'minecraft:redstone', N: 'minecraft:iron_nugget' });
json(join(DATA, 'loot_modifiers/parts.json'), { type: 'bleep:parts' });
// (every file in loot_modifiers is a modifier of its own: no global list any more)

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
// the post: the charge light (on while a bleep charges) and under it its own battery, four bars (power 0..4; empty: one red)
for (const [name, light] of [['homebase_post', '#1a3a3d'], ['homebase_post_on', GLOW]]) {
  for (let power = 0; power <= 4; power++) {
    texture('block/' + name + '_' + power, 16, 16, g => {
      g.rect(0, 0, 16, 16, WHITE);
      g.rect(5, 0, 6, 1, WHITE_SHADE);
      g.rect(7, 3, 2, 8, SCREEN); g.rect(7, 4, 2, 6, light);           // the charge light
      g.set(7, 1, GREY); g.set(8, 1, GREY);
      g.rect(6, 11, 4, 2, SCREEN);                                       // the battery
      for (let bar = 0; bar < 4; bar++) g.set(6 + bar, 12, bar < power ? GLOW : power === 0 && bar === 0 ? '#ff4f8b' : '#1a3a3d');
    });
  }
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
json(join(ASSETS, 'models/block/homebase.json'), { parent: 'minecraft:block/block', textures: homebaseTextures('homebase_post_4'), elements: [PAD, POST] });
for (let power = 0; power <= 4; power++) for (const on of [false, true]) {
  json(join(ASSETS, 'models/block/homebase_' + power + (on ? '_charging' : '') + '.json'), { parent: 'bleep:block/homebase', textures: homebaseTextures('homebase_post' + (on ? '_on_' : '_') + power) });
}
for (const old of ['models/block/homebase_charging.json', 'textures/block/homebase_post.png', 'textures/block/homebase_post_on.png']) if (existsSync(join(ASSETS, old))) unlinkSync(join(ASSETS, old));
const YAW = { north: 0, east: 90, south: 180, west: 270 };
json(join(ASSETS, 'blockstates/homebase.json'), { variants: Object.fromEntries(Object.entries(YAW).flatMap(([facing, y]) => [false, true].flatMap(on => [0, 1, 2, 3, 4].map(power =>
  ['charging=' + on + ',facing=' + facing + ',power=' + power, { model: 'bleep:block/homebase_' + power + (on ? '_charging' : ''), ...(y ? { y } : {}) }])))) });
item('homebase', 'bleep:block/homebase');

// ---------- from the stars: starstone (strange signs on it), the fallen star, star shards ----------
const SIGN = '#2de2e6', SIGN_DIM = '#1f6f86';
texture('block/starstone', 16, 16, g => {
  const ROCK = ['#241d40', '#2d2550', '#362c60', '#1d1834'];
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) g.set(x, y, ROCK[(x * 5 + y * 11 + ((x * y) % 7)) % 4]);
  // signs nobody can read
  for (const [x, y, w, h] of [[2, 2, 1, 4], [2, 5, 3, 1], [9, 2, 4, 1], [12, 2, 1, 3], [10, 4, 1, 1], [3, 10, 1, 4], [4, 10, 2, 1], [8, 9, 1, 1], [9, 10, 1, 3], [10, 12, 3, 1], [13, 8, 1, 2]]) g.rect(x, y, w, h, SIGN);
  for (const [x, y] of [[6, 7], [14, 13], [1, 14], [7, 1]]) g.set(x, y, SIGN_DIM);
});
texture('block/fallen_star', 16, 16, g => {
  g.rect(0, 0, 16, 16, '#1d1834');
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.abs(x - 7.5) + Math.abs(y - 7.5);
    if (d < 3) g.set(x, y, '#ffffff'); else if (d < 5) g.set(x, y, '#8ff7ff'); else if (d < 7) g.set(x, y, '#2de2e6'); else if (d < 8.5) g.set(x, y, '#3a2f6b');
  }
  for (const [x, y] of [[1, 1], [14, 2], [2, 13], [13, 14], [7, 0], [0, 8]]) g.set(x, y, '#8ff7ff'); // rays
});
texture('item/star_shard', 16, 16, g => {
  const rows = ['......#.', '.....##.', '....###.', '...####.', '..####..', '.####...', '.###....', '.##.....'];
  rows.forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') g.set(4 + x, 4 + y, (x + y) % 3 ? '#2de2e6' : '#8ff7ff'); }));
  g.set(10, 4, '#ffffff'); g.set(6, 8, '#ffffff');
});
for (const name of ['starstone', 'fallen_star']) {
  json(join(ASSETS, 'models/block/' + name + '.json'), { parent: 'minecraft:block/cube_all', textures: { all: 'bleep:block/' + name } });
  json(join(ASSETS, 'blockstates/' + name + '.json'), { variants: { '': { model: 'bleep:block/' + name } } });
  item(name, 'bleep:block/' + name);
}
json(join(ASSETS, 'models/item/star_shard.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'bleep:item/star_shard' } });
item('star_shard', 'bleep:item/star_shard');
// what starstone and the fallen star give is worked out in block/StarBlock (26.1 and 26.3 write loot tables differently)
json(join(DATA, 'recipe/homebase.json'), {
  type: 'minecraft:crafting_shaped', category: 'misc',
  key: { L: 'minecraft:lightning_rod', I: 'minecraft:iron_ingot', R: 'minecraft:redstone_block', S: 'minecraft:smooth_stone_slab' },
  pattern: [' L ', 'IRI', 'SSS'], result: { count: 1, id: 'bleep:homebase' },
});
json(join(DATA, 'loot_table/blocks/homebase.json'), { type: 'minecraft:block', random_sequence: 'bleep:blocks/homebase',
  pools: [{ rolls: 1, conditions: [{ condition: 'minecraft:survives_explosion' }], entries: [{ type: 'minecraft:item', name: 'bleep:homebase' }] }] });
json(join(ROOT, 'data/minecraft/tags/block/mineable/pickaxe.json'), { values: ['bleep:homebase', 'bleep:starstone', 'bleep:fallen_star'] });

// ---------- the display case: a glass case on a dark wooden foot, its bleep's museum ----------
texture('block/display_case_wood', 16, 16, g => {
  g.rect(0, 0, 16, 16, '#5a3a1e');
  for (let x = 0; x < 16; x++) { g.set(x, 0, '#7a5029'); g.set(x, 15, '#3d2712'); if (x % 5 === 2) g.set(x, 7, '#4a2f17'); }
  g.rect(0, 5, 16, 1, '#ffb547');                                  // a thin brass line
});
texture('block/display_case_glass', 16, 16, g => {
  for (let i = 0; i < 16; i++) { g.set(i, 0, '#c9d1d9'); g.set(i, 15, '#c9d1d9'); g.set(0, i, '#c9d1d9'); g.set(15, i, '#c9d1d9'); } // the frame
  g.set(3, 4, '#eef6ff'); g.set(4, 3, '#eef6ff'); g.set(3, 6, '#eef6ff'); g.set(6, 3, '#eef6ff'); g.set(5, 4, '#eef6ff'); g.set(4, 5, '#eef6ff'); // a shine
});
// the glass shows its whole texture on every side, so its frame runs along the edges
const CASE_FACES = (from, to, tex) => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map(f => [f, tex === '#glass' ? { uv: [0, 0, 16, 16], texture: tex } : { texture: tex }]));
json(join(ASSETS, 'models/block/display_case.json'), { parent: 'minecraft:block/block',
  textures: { particle: 'bleep:block/display_case_wood', wood: 'bleep:block/display_case_wood', glass: 'bleep:block/display_case_glass' },
  elements: [
    { from: [1, 0, 1], to: [15, 4, 15], faces: CASE_FACES(null, null, '#wood') },                // the foot
    { from: [6, 4, 6], to: [10, 5, 10], faces: CASE_FACES(null, null, '#wood') },                // a little plinth
    { from: [1, 4, 1], to: [15, 15, 15], faces: CASE_FACES(null, null, '#glass') },              // the glass
    { from: [1, 15, 1], to: [15, 16, 15], faces: CASE_FACES(null, null, '#wood') },              // the lid
  ] });
json(join(ASSETS, 'blockstates/display_case.json'), { variants: { '': { model: 'bleep:block/display_case' } } });
item('display_case', 'bleep:block/display_case');
json(join(DATA, 'loot_table/blocks/display_case.json'), { type: 'minecraft:block', random_sequence: 'bleep:blocks/display_case',
  pools: [{ rolls: 1, conditions: [{ condition: 'minecraft:survives_explosion' }], entries: [{ type: 'minecraft:item', name: 'bleep:display_case' }] }] });
json(join(ROOT, 'data/minecraft/tags/block/mineable/axe.json'), { values: ['bleep:display_case', 'bleep:start_flag', 'bleep:race_flag'] });
shaped('display_case', ['GGG', 'G G', 'PPP'], { G: 'minecraft:glass', P: '#minecraft:planks' });

// ---------- race flags: a white pole with a chequered flag (the start) or a cyan one (the course) ----------
texture('block/flag_pole', 16, 16, g => {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) g.set(x, y, Math.floor(y / 4) % 2 ? '#c9d1d9' : '#eef1f4');
});
texture('block/start_flag_cloth', 16, 16, g => {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) g.set(x, y, (Math.floor(x / 2) + Math.floor(y / 2)) % 2 ? '#1b1d22' : '#eef1f4');
});
texture('block/race_flag_cloth', 16, 16, g => {
  g.rect(0, 0, 16, 16, '#2de2e6');
  g.rect(0, 7, 16, 2, '#1f9aa0');
  for (let y = 0; y < 16; y++) g.set(0, y, '#1f9aa0');
});
for (const name of ['start_flag', 'race_flag']) {
  const cloth = { uv: [0, 0, 13, 12], texture: '#cloth' }, edge = { uv: [0, 0, 1, 12], texture: '#cloth' };
  json(join(ASSETS, 'models/block/' + name + '.json'), { parent: 'minecraft:block/block',
    textures: { particle: 'bleep:block/' + name + '_cloth', pole: 'bleep:block/flag_pole', cloth: 'bleep:block/' + name + '_cloth' },
    elements: [
      { from: [7.5, 0, 7.5], to: [8.5, 16, 8.5], faces: Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map(f => [f, { uv: [7, 0, 8, 16], texture: '#pole' }])) },
      { from: [8.5, 9, 7.75], to: [15, 15, 8.25], faces: { north: cloth, south: cloth, east: edge, up: { uv: [0, 0, 13, 1], texture: '#cloth' }, down: { uv: [0, 0, 13, 1], texture: '#cloth' } } },
    ] });
  json(join(ASSETS, 'blockstates/' + name + '.json'), { variants: Object.fromEntries(Object.entries(YAW).map(([facing, y]) => ['facing=' + facing, { model: 'bleep:block/' + name, ...(y ? { y } : {}) }])) });
  // in the hand and the inventory: a flat icon of its own (the block model is too thin to see)
  texture('item/' + name, 16, 16, g => {
    for (let y = 1; y < 16; y++) g.set(4, y, y % 4 < 2 ? '#eef1f4' : '#c9d1d9');
    g.set(4, 0, '#ffd23f');
    for (let y = 1; y < 9; y++) for (let x = 5; x < 15; x++) {
      const wave = (x > 9 && y > 0) ? 1 : 0, yy = y + wave;
      g.set(x, yy, name === 'start_flag' ? ((Math.floor((x - 5) / 2) + Math.floor((y - 1) / 2)) % 2 ? '#1b1d22' : '#eef1f4') : (y === 4 || y === 5 ? '#1f9aa0' : '#2de2e6'));
    }
  });
  json(join(ASSETS, 'models/item/' + name + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'bleep:item/' + name } });
  item(name, 'bleep:item/' + name);
  json(join(DATA, 'loot_table/blocks/' + name + '.json'), { type: 'minecraft:block', random_sequence: 'bleep:blocks/' + name,
    pools: [{ rolls: 1, conditions: [{ condition: 'minecraft:survives_explosion' }], entries: [{ type: 'minecraft:item', name: 'bleep:' + name }] }] });
}
shaped('start_flag', ['WB', 'BW', 'S '], { W: 'minecraft:white_wool', B: 'minecraft:black_wool', S: 'minecraft:stick' });
// a handful of course flags from paper and cyan dye
json(join(DATA, 'recipe/race_flag.json'), { type: 'minecraft:crafting_shaped', category: 'misc', key: { P: 'minecraft:paper', D: 'minecraft:cyan_dye', S: 'minecraft:stick' },
  pattern: ['PD', 'S '], result: { count: 4, id: 'bleep:race_flag' } });
// a trailer: a chest on a cart of planks, on two iron wheels
shaped('trailer', [' C ', 'PPP', 'I I'], { C: 'minecraft:chest', P: '#minecraft:planks', I: 'minecraft:iron_ingot' });
// the core from the stars: eight star shards round a block of diamond
json(join(DATA, 'recipe/bleep_core_from_stars.json'), { type: 'minecraft:crafting_shaped', category: 'misc',
  key: { S: 'bleep:star_shard', B: 'minecraft:diamond_block' }, pattern: ['SSS', 'SBS', 'SSS'], result: { count: 1, id: 'bleep:bleep_core' } });

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
  stubborn: ['Yes, yes. In a minute.', 'I heard you. I am choosing to finish my turn first.', 'Fine. FINE.'],
  glitch: ['BL33P B00P!!1', 'TOO MUCH HAPPY. REBOOTING HAPPY.'],
  fighting: ['Bleep. Bleep. BLEEP.', 'Stay behind me. I have tracks.', 'This is for scratching my paint.'],
  overcharged: ['I CAN SEE ELECTRONS.', 'Everything is FAST and I LOVE IT.', 'Ten thousand volts of happy!'],
  storm: ['Thunder! Come on, lightning. Pick me. PICK ME.', 'I hear a storm. I am ready. I am SO ready.'],
  dream: ['Zzz... a field of batteries... all mine... zzz', 'Zzz... the llama is chasing me... zzz', 'Zzz... wheels for the cow... zzz', 'Zzz... beep... boop... zzz'],
  dance: ['Bleep bloop, this is my jam!', 'Left track, right track, spin!', 'Music! My circuits are dancing.'],
  play: ['Tag! You are it! No, I am it! Who is it?', 'Round and round! This is the best game.', 'Catch me if you can! You can. Do not.'],
};
// the favourite items (Obsession in Java): their names, and the hints in its thoughts until its owner finds out
const FAVOURITES = {
  amethyst_shard: ['Amethyst Shard', 'Something purple… something that goes *ting*…'],
  lightning_rod: ['Lightning Rod', 'One day. One day the lightning will come.'],
  compass: ['Compass', "That little needle knows something. I'm sure of it."],
  music_disc: ['Music Disc', 'Round. Shiny. Tasty?'],
  glow_berries: ['Glow Berries', 'Little lights you can eat…'],
  clock: ['Clock', 'Tick. Tock. Something round knows what time it is.'],
  spyglass: ['Spyglass', 'A tube that makes far things close. I want to look through it.'],
  firework_rocket: ['Firework Rocket', 'A stick that goes WHOOSH and then sparkles. Want.'],
  bell: ['Bell', 'Ding! …Where is that ding coming from?'],
  honeycomb: ['Honeycomb', 'Little hexagons. So tidy. So perfect.'],
};
// what it thinks the first time it sees each mob (mob.bleep.<id>), from the design doc
const MOBS = {
  allay: "Flies, sings, fetches stuff. Show-off.",
  armadillo: "Rolls into a ball when scared. Honestly? Smart.",
  axolotl: "Permanently smiling. Suspicious.",
  bat: "Hangs upside down all day. And they call ME weird.",
  camel: "Two seats, zero hurry.",
  cat: "It stares at me like I owe it money.",
  chicken: "A bird that can't fly. Someone gave up here.",
  cod: "A fish. Doing fish things. Riveting.",
  copper_golem: "A fellow metal friend! …Why is it turning green?",
  cow: "Why doesn't that cow have wheels?",
  donkey: "A horse that says no. I respect that.",
  frog: "Swallows things whole. Keeping my distance.",
  glow_squid: "Its whole body is a screen. Unfair.",
  happy_ghast: "A giant cloud with a face. It seems… fine?",
  horse: "No tracks, still faster than me. Rude.",
  iron_golem: "Big brother. He just doesn't know it yet.",
  llama: "It's watching. It knows. Don't move.",
  mooshroom: "A cow with a garden on its back. Overachiever.",
  mule: "Half horse, half donkey, full storage.",
  nautilus: "Carries its house everywhere. Never needs a homebase. Jealous.",
  ocelot: "It ran away from me. Hurtful.",
  parrot: "It copies sounds. Bleep. …Don't you dare.",
  pig: "People ride this? I have tracks.",
  pufferfish: "Angry balloon. Do not hug.",
  rabbit: "Jumps everywhere. Has it considered tracks?",
  salmon: "Swims upstream on purpose. Why.",
  sheep: "It's just wearing a cloud.",
  skeleton_horse: "A horse that forgot the horse part.",
  sniffer: "Big nose. Big dreams.",
  snow_golem: "Made of snow in a world with lava. Bold.",
  squid: "Eight arms and still can't hold a sword.",
  strider: "Shivers the second it leaves lava. Dramatic.",
  sulfur_cube: "It eats blocks and becomes them. Identity crisis.",
  tadpole: "A frog in beta.",
  trader_llama: "A llama. With a JOB. Even worse.",
  tropical_fish: "Too many colors. Pick one.",
  turtle: "Finally. Something slower than me.",
  villager: "Hmm. Hmm hmm. Deep conversation, as always.",
  wandering_trader: "Sells random stuff. Brings llamas. Unforgivable.",
  bee: "Tiny factory worker. Stings on its break.",
  cave_spider: "Smaller spider. Still no wheels.",
  dolphin: "Smarter than the player. Don't tell them.",
  enderman: "Not looking. NOT looking. …I looked.",
  fox: "Steals things from chests. …Okay, we have that in common.",
  goat: "Headbutts first. Asks questions never.",
  panda: "Round, lazy, eats all day. Goals.",
  piglin: "Loves gold more than I love my favorite thing. Respect.",
  polar_bear: "Big, fluffy, protective. Fair enough.",
  spider: "Eight legs and still no wheels. Bad design.",
  wolf: "Good boy. Bites. Good boy?",
  zombified_piglin: "Hit one and the whole family shows up. Classic.",
  blaze: "Spinning sticks on fire. Not battery-powered. Sad.",
  bogged: "A skeleton wearing seaweed. Fashion choice.",
  breeze: "All wind, no substance.",
  camel_husk: "A dried-up camel. Thirsty for revenge.",
  creaking: "Only moves when no one looks. Good thing I can't blink.",
  creeper: "Green, angry, and allergic to hugs.",
  drowned: "A zombie that took swimming lessons.",
  elder_guardian: "Grumpy old fish with a laser. Unnecessary.",
  endermite: "Tiny, purple, annoying. Nothing like me.",
  evoker: "Summons fangs and little flying guys. No manners.",
  ghast: "Cries all the time. Throws fireballs. Mood.",
  guardian: "One eye, many spikes, zero chill.",
  hoglin: "A pig, but angry about it.",
  husk: "A zombie that forgot sunscreen.",
  magma_cube: "Jelly, but hot. No thank you.",
  parched: "A skeleton that really needs a drink.",
  phantom: "Sleep, or this happens. Noted.",
  piglin_brute: "No amount of gold fixes this one.",
  pillager: "Crossbow, frown, no hobbies.",
  ravager: "Big angry furniture.",
  shulker: "A box that bites. I'd like to unsubscribe.",
  silverfish: "Lives in stone. Rent free.",
  skeleton: "No muscles, still holds a bow. Respect, honestly.",
  slime: "Hit it and there's more of it. Bad math.",
  stray: "A skeleton that's always cold. Wear a scarf.",
  vex: "Tiny flying menace with a sword. Who gave it a sword?",
  vindicator: "Has an axe and no indoor voice.",
  warden: "No.",
  witch: "Drinks potions all day. Not judging. Okay, a little.",
  wither_skeleton: "Tall, dark and withering.",
  zoglin: "A hoglin, but somehow worse.",
  zombie: "Slow, groaning, smells weird. So… Monday morning.",
  zombie_horse: "A horse that gave up. Then got back up.",
  zombie_nautilus: "Even its shell looks tired.",
  zombie_villager: "Hmmrgh. Same conversation, worse breath.",
  ender_dragon: "Big, purple, flies in circles. I'll be behind you.",
  wither: "Three heads and not one good idea.",
};
const favouriteLines = Object.fromEntries(Object.entries(FAVOURITES).flatMap(([id, [name, hint]]) =>
  [['obsession.bleep.' + id, name], ['thought.bleep.obsession.' + id, hint]]));
const mobLines = Object.fromEntries(Object.entries(MOBS).map(([id, line]) => ['mob.bleep.' + id, line]));
const thoughtLines = Object.fromEntries(Object.entries(THOUGHTS).flatMap(([kind, lines]) => lines.map((line, n) => ['thought.bleep.' + kind + '.' + n, line])));

// ---------- advancements: the root and the shard come from the inventory, the others are awarded in Awards.java ----------
const ADVANCEMENTS = [
  // id, parent, icon, frame, title, description
  ['root', null, 'bleep:bleep_core', 'task', 'Bleep!', 'Find a Bleep Core'],
  ['hello_world', 'root', 'bleep:bleep_display', 'task', 'Hello, World', 'Build a bleep'],
  ['home_sweet_home', 'hello_world', 'bleep:homebase', 'task', 'Home Sweet Home', 'Give your bleep a homebase'],
  ['friends', 'hello_world', 'minecraft:cookie', 'task', 'Friends', 'Become friends with a bleep'],
  ['best_friends', 'friends', 'minecraft:cake', 'goal', 'Best Friends', 'Become a bleep\'s best friend'],
  ['dressed_up', 'hello_world', 'bleep:top_hat', 'task', 'Dressed to Impress', 'Put something on your bleep'],
  ['overcharged', 'hello_world', 'minecraft:lightning_rod', 'goal', 'Overcharged!', 'See your bleep get struck by lightning'],
  ['birthday', 'friends', 'bleep:party_hat', 'goal', 'Happy Birthday', 'Celebrate a bleep\'s birthday'],
  ['family', 'hello_world', 'bleep:bleep_tracks', 'goal', 'The More the Merrier', 'Have three bleeps'],
  ['snowman', 'hello_world', 'minecraft:snow_block', 'task', 'Do You Want to Build a Snowman?', 'Your bleep builds a snowman'],
  ['say_cheese', 'hello_world', 'minecraft:spyglass', 'task', 'Say Cheese!', 'Your bleep takes a photo'],
  ['make_a_wish', 'root', 'bleep:star_shard', 'goal', 'Make a Wish', 'Find a star shard'],
  ['good_as_new', 'root', 'minecraft:redstone_block', 'challenge', 'Good as New', 'Find and wake an abandoned bleep'],
  ['band', 'friends', 'minecraft:note_block', 'goal', 'Band Practice', 'Three bleeps make music together'],
  ['bon_voyage', 'hello_world', 'minecraft:filled_map', 'task', 'Bon Voyage', 'Send your bleep on an expedition'],
  ['race', 'hello_world', 'bleep:start_flag', 'task', 'And They\'re Off!', 'Start a bleep race'],
];
const BY_INVENTORY = { root: 'bleep:bleep_core', make_a_wish: 'bleep:star_shard' };
for (const [id, parent, icon, frame] of ADVANCEMENTS) {
  json(join(DATA, 'advancement', id + '.json'), {
    ...(parent ? { parent: 'bleep:' + parent } : {}),
    display: { icon: { id: icon }, title: { translate: 'advancements.bleep.' + id + '.title' }, description: { translate: 'advancements.bleep.' + id + '.description' },
      frame, show_toast: true, announce_to_chat: parent !== null, ...(parent ? {} : { background: 'minecraft:gui/advancements/backgrounds/stone' }) },
    criteria: BY_INVENTORY[id] ? { got: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: BY_INVENTORY[id] }] } } } : { done: { trigger: 'minecraft:impossible' } },
  });
}
const advancementLines = Object.fromEntries(ADVANCEMENTS.flatMap(([id, , , , title, description]) =>
  [['advancements.bleep.' + id + '.title', title], ['advancements.bleep.' + id + '.description', description]]));

// ---------- language ----------
json(join(ASSETS, 'lang/en_us.json'), {
  'itemGroup.bleep': 'Bleep',
  'entity.bleep.bleep': 'Bleep',
  'item.bleep.bleep_core': 'Bleep Core',
  'item.bleep.bleep_display': 'Bleep Display',
  'item.bleep.bleep_tracks': 'Bleep Tracks',
  'item.bleep.bleep_arms': 'Bleep Arms',
  'tooltip.bleep.bleep_core': 'The heart of a bleep. Warm, and humming.\nNobody knows what it is made of.\nUse it on the ground with a display,\ntracks and arms in your inventory.\nFound in dungeons, abandoned mineshafts\nand fallen stars, or crafted, at a price.',
  'block.bleep.starstone': 'Starstone', 'block.bleep.fallen_star': 'Fallen Star', 'item.bleep.star_shard': 'Star Shard',
  'tooltip.bleep.starstone': 'Glowing rock from a fallen star.\nThe signs on it are no alphabet anybody knows.',
  'tooltip.bleep.fallen_star': 'Still warm. It hums.\nMine it: star shards, and maybe a Bleep Core.',
  'tooltip.bleep.star_shard': 'A sliver of a fallen star.\nEight round a block of diamond: a Bleep Core.',
  'tooltip.bleep.bleep_display': 'A bleep\'s face.\nOne of the three parts a core needs.',
  'tooltip.bleep.bleep_tracks': 'What a bleep drives on.\nOne of the three parts a core needs.',
  'tooltip.bleep.bleep_arms': 'Big enough to hold things.\nOne of the three parts a core needs.',
  'tooltip.bleep.homebase': 'Place it near your bleep: it moves in\nand charges here, from its own battery.\nFill it with redstone, a block of redstone\nor FE from cables.',
  'tooltip.bleep.homebase_stored': 'Battery: %s%%',
  'message.bleep.missing_parts': 'Still missing: %s',
  // the Bleepedia
  'screen.bleep.bleepedia': 'Bleepedia', 'screen.bleep.bleepedia_of': 'Bleepedia of %s', 'screen.bleep.new': 'NEW',
  'screen.bleep.nothing_yet': 'Nothing here yet.', 'screen.bleep.day': 'Day %s',
  'screen.bleep.tab.mobs': 'Mobs', 'screen.bleep.tab.biomes': 'Biomes', 'screen.bleep.tab.finds': 'Finds', 'screen.bleep.tab.about': 'About', 'screen.bleep.tab.signs': '???',
  'screen.bleep.signs.hint': 'It does not know what they mean either.', 'screen.bleep.tab.diary': 'Diary',
  'screen.bleep.about.day': 'Day of its life', 'screen.bleep.about.together': 'Days together', 'screen.bleep.about.distance': 'Driven',
  'screen.bleep.about.mobs': 'Mobs met', 'screen.bleep.about.biomes': 'Biomes seen', 'screen.bleep.about.finds': 'Finds',
  'screen.bleep.about.fights': 'Fights won', 'screen.bleep.about.empty': 'Ran empty', 'screen.bleep.about.bond': 'Bond',
  'biome.bleep.unknown': 'Somewhere new. I am filing this under adventure.',
  'screen.bleep.no_photos': 'No photos yet. At a nice moment it says Click!: a sunset with you, a falling star, its best friend, a new biome. Six to a page; click one to see it big, and print it on an empty map.',
  'photo.bleep.sunset': 'Sunset', 'photo.bleep.star': 'A falling star!', 'photo.bleep.friend': 'Me and %s', 'photo.bleep.biome': '%s!',
  'photo.bleep.sunset.diary': 'a sunset', 'photo.bleep.star.diary': 'a falling star', 'photo.bleep.friend.diary': 'my best friend', 'photo.bleep.biome.diary': 'a new place',
  'diary.bleep.photo': 'I took a photo: %s. I am an artist now.',
  'thought.bleep.photo.sunset': 'Click! The sky is on fire. In a nice way.',
  'thought.bleep.photo.star': 'Click! Did you see that? I got it on camera!',
  'thought.bleep.photo.friend': 'Click! Me and my best friend. Frame it.',
  'thought.bleep.photo.biome': 'Click! New place, new photo. For the album.',
  'message.bleep.click': '%s: Click!',
  'screen.bleep.print': 'Print',
  'item.bleep.photo_print': 'Photo by %s: %s',
  'message.bleep.printed': 'Printed: %s. Hang it in an item frame!',
  'message.bleep.print_no_map': 'You need an empty map to print on.',
  'thought.bleep.imitate': 'Guess who! *ahem*',
  'thought.bleep.imitate.reveal': 'Did you get it? I am very good at this. I practise in the dark.',
  'message.bleep.imitated': '%s did an impression of: %s',
  'thought.bleep.forecast.rain': 'Rain in a minute. I can feel it in my circuits.',
  'thought.bleep.forecast.thunder': 'Thunder is coming! Lightning, please, pick me. PICK ME.',
  'screen.bleep.sound.on': 'Sound on (click to mute). On its homebase it is quiet anyway.',
  'screen.bleep.sound.off': 'Muted (click for sound)',
  'diary.bleep.snowman': 'I built a snowman. It does not say much. Neither do I, sometimes.',
  'thought.bleep.snowman.start': 'Snow! Snowman time. Stand back, artist at work.',
  'thought.bleep.snowman.done': 'A snowman! It needs a head. A pumpkin would do. *hint*',
  'thought.bleep.snowman.alive': 'IT IS ALIVE. I made a friend. A cold one.',
  'activity.bleep.building': 'Building a snowman',
  'activity.bleep.boating': 'Sailing', 'activity.bleep.sledding': 'Sledding',
  'activity.bleep.overheated': 'Overheated!', 'activity.bleep.cooling': 'Cooling down', 'activity.bleep.music': 'Making music',
  // music on note blocks
  'screen.bleep.play.band': 'Music and trips', 'screen.bleep.play.music': 'Play a tune',
  'screen.bleep.play.explore': 'Go exploring',
  // races
  'block.bleep.start_flag': 'Start Flag', 'block.bleep.race_flag': 'Race Flag',
  'tooltip.bleep.start_flag': 'Place race flags around it, then\nright-click it: your bleeps race,\nflag to flag and back. It keeps\nthe course record.',
  'tooltip.bleep.race_flag': 'The course for a bleep race:\nplace them round a start flag.',
  'jei.bleep.start_flag': 'The start and finish of a bleep race. Place race flags around it (within 32 blocks), then right-click it with an empty hand: your bleeps within 16 blocks line up, and so do the bleeps of other players standing right by the flag. 3, 2, 1, GO! They drive from the start to the nearest race flag, from there each time to the nearest flag not yet visited (twelve at most), and back to the start. The first one home wins a cup; everyone near hears the times. The start flag keeps its course record. One bleep alone: a time trial against the record.',
  'jei.bleep.race_flag': 'The course of a bleep race: place them around a start flag (within 32 blocks). The bleeps go from the start to the nearest race flag, then each time to the nearest one not yet visited, and back to the start.',
  'activity.bleep.racing': 'Racing!',
  'message.bleep.race.no_flags': 'Place race flags around the start flag first.',
  'message.bleep.race.nobody': 'No bleep of yours close by to race.',
  'message.bleep.race.on': 'A race is on!',
  'message.bleep.race.count': '%s...',
  'message.bleep.race.go': 'GO!',
  'message.bleep.race.over': 'Race over!',
  'message.bleep.race.nobody_home': 'Race over: nobody made it home.',
  'message.bleep.race.record': 'New course record: %s, %s (was %s)!',
  'message.bleep.race.to_beat': 'Course record: %s, %s.',
  'thought.bleep.race.ready': 'On your marks. I was born ready. Well, built ready.',
  'thought.bleep.race.won': 'I WON! Did you see? Did everyone see?',
  'thought.bleep.race.lost': 'Not first. But not last either. I will take it.',
  'thought.bleep.race.last': 'Last. The track was wonky. And the wind. And my tracks.',
  'thought.bleep.race.trial': 'How was my time? Do not tell me. Tell me.',
  'diary.bleep.race': 'We had a race today. Flags and everything.',
  'diary.bleep.race.won': 'I won a race! I am the fastest bleep in the world. Probably.',
  'screen.bleep.play.explore.tip': 'It goes on an expedition for a Minecraft day (it needs half its battery). It comes back to you with finds, photos of where it went and the story in its diary.',
  'activity.bleep.expedition': 'On an expedition',
  'message.bleep.trip.off': '%s is off on an expedition. Back in a day!',
  'message.bleep.trip.back': '%s is back from %s!',
  'thought.bleep.trip.battery': 'A trip? With this battery? Charge me up first, at least half.',
  'thought.bleep.trip.back': 'I am back! You would not believe what I saw. It is all in my diary.',
  'photo.bleep.trip': 'Trip to %s',
  'place.bleep.forest': 'the forest', 'place.bleep.desert': 'the desert', 'place.bleep.mountains': 'the mountains', 'place.bleep.snowy': 'the snow',
  'place.bleep.ocean': 'the sea', 'place.bleep.jungle': 'the jungle', 'place.bleep.swamp': 'the swamp', 'place.bleep.cave': 'the caves',
  'diary.bleep.trip.forest': 'I went to the forest. Trees everywhere. I counted them. I lost count.',
  'diary.bleep.trip.forest.0': 'A fox stole an apple from me. I let it. It looked like it needed it more.',
  'diary.bleep.trip.forest.1': 'I got stuck in a bush for an hour. Very relaxing, actually.',
  'diary.bleep.trip.forest.2': 'A bird sat on my head and sang. I sang back. It left.',
  'diary.bleep.trip.desert': 'I went to the desert. Sand gets in EVERYTHING. Everything.',
  'diary.bleep.trip.desert.0': 'I found a pyramid. I did not go in. I have seen what happens in pyramids.',
  'diary.bleep.trip.desert.1': 'A cactus and I had a staring contest. The cactus won.',
  'diary.bleep.trip.desert.2': 'It was so hot I almost overheated. I sat in the shade of a rabbit.',
  'diary.bleep.trip.mountains': 'I went up a mountain. My tracks are not made for mountains. I did it anyway.',
  'diary.bleep.trip.mountains.0': 'A goat tried to ram me off a cliff. We are not friends.',
  'diary.bleep.trip.mountains.1': 'At the top I could see the whole world. It is very big. You are in it somewhere.',
  'diary.bleep.trip.mountains.2': 'I yelled BLEEP and the mountain yelled it back. Rude, but impressive.',
  'diary.bleep.trip.snowy': 'I went where it snows. I slid down every hill twice.',
  'diary.bleep.trip.snowy.0': 'I made a snow angel. It came out as a snow robot.',
  'diary.bleep.trip.snowy.1': 'A polar bear looked at me for a long time. I looked back. We had an understanding.',
  'diary.bleep.trip.snowy.2': 'My screen froze. Literally. I thawed it with a happy thought.',
  'diary.bleep.trip.ocean': 'I went to sea. A boat came out of me again. Captain Bleep, reporting.',
  'diary.bleep.trip.ocean.0': 'A dolphin raced me. I lost. It did a little flip about it.',
  'diary.bleep.trip.ocean.1': 'I found a tiny island with one palm tree. I named it Bleep Island.',
  'diary.bleep.trip.ocean.2': 'A squid inked my boat. I do not know what I did to it.',
  'diary.bleep.trip.jungle': 'I went to the jungle. Everything is green and wants to climb me.',
  'diary.bleep.trip.jungle.0': 'A parrot copied my voice. Now there are two of us. The jungle is not ready.',
  'diary.bleep.trip.jungle.1': 'An ocelot ignored me all day. I respect that.',
  'diary.bleep.trip.jungle.2': 'I got lost in the bamboo. A panda showed me the way. Or sneezed. Hard to tell.',
  'diary.bleep.trip.swamp': 'I went to the swamp. It smells like old soup.',
  'diary.bleep.trip.swamp.0': 'A frog ate a fly next to me. Then it looked at me. I left.',
  'diary.bleep.trip.swamp.1': 'There was a hut on legs. Someone inside was laughing. I did not knock.',
  'diary.bleep.trip.swamp.2': 'I sank a little in the mud. A slime helped me out. Slimes are nice.',
  'diary.bleep.trip.cave': 'I went down into the caves. Dark, damp, and very echo-y. Echo-y. Echo-y.',
  'diary.bleep.trip.cave.0': 'I found crystals that hum. I hummed back. We made a song.',
  'diary.bleep.trip.cave.1': 'Something in the dark said hello. I said hello back. It was a bat. Probably.',
  'diary.bleep.trip.cave.2': 'My screen was the only light down there. I felt very important.',
  'diary.bleep.trip.back': 'And then I came home to you. Best part of the trip.',
  'screen.bleep.play.music.tip': 'It plays a tune on the nearest note block (within 8 blocks). Free bleeps nearby join in on note blocks of their own: the tune, a harmony and the bass. The block under each note block picks the instrument.',
  'thought.bleep.music.none': 'I would play you something, but I need a note block. Within 8 blocks, please.',
  'thought.bleep.music.start': 'One, two, one two three four!',
  'thought.bleep.music.join': 'Wait for me! I am in the band too.',
  'thought.bleep.music.done': 'Thank you, thank you. I will be here all week.',
  'thought.bleep.music.tune.twinkle': 'This one is about a star. I can relate.',
  'thought.bleep.music.tune.joy': 'A classic. Very old. Older than me, probably.',
  'thought.bleep.music.tune.jacques': 'Are you sleeping? You are not. Good. Listen.',
  'thought.bleep.music.tune.bleep': 'I wrote this one myself. It is called Bleep.',
  'diary.bleep.music': 'I played music on a note block today. I think I am a natural.',
  'diary.bleep.band': 'We started a band. We do not have a name yet. I vote for The Bleeps.',
  // the Nether and the End
  'thought.bleep.warm': 'Is it me, or is it getting hot in here? It is not me.',
  'thought.bleep.overheated': 'TOO HOT. Systems melting. Water, ice, snow, anything cold, please.',
  'thought.bleep.find_cool': 'Cooling down. Back in a sec.',
  'thought.bleep.cooled': 'Ahh. Much better. Steam is my new favourite thing.',
  'thought.bleep.nether': 'This place is on fire. Literally. Who designed this?',
  'thought.bleep.end.arrive': 'It is so quiet here. And the stars... I know these stars.',
  'thought.bleep.end.0': 'The stars look closer here.',
  'thought.bleep.end.1': 'Shh. I am listening. To what, I do not know.',
  'thought.bleep.end.2': 'Something up there feels familiar. I cannot say what.',
  'thought.bleep.end.3': 'If I squint, one of those stars is waving.',
  'thought.bleep.end.4': 'I am not homesick. I do not even know where home is.',
  'diary.bleep.nether': 'I went to the Nether. It was hot. I would not recommend it. Five stars anyway.',
  'diary.bleep.end': 'We went to the End. It was very quiet. I looked up for a long time.',
  'diary.bleep.end_stars': 'The stars in the End feel like they are looking back.',
  'diary.bleep.overheated': 'I overheated today. Smoke came out of places I did not know I had.',
  'diary.bleep.cooled': 'Water makes a hissing sound on me. I like it.',
  'photo.bleep.nether': 'The Nether (hot)', 'photo.bleep.end': 'The End',
  'photo.bleep.nether.diary': 'the Nether', 'photo.bleep.end.diary': 'the End',
  'diary.bleep.sled': 'Snow! A sled came out of me. I went WHEEE. I did not know I could say WHEEE.',
  'thought.bleep.sled': 'Wheee! Snow is the best floor.',
  'diary.bleep.boat': 'I fell in the water and a boat came out of me. I did not know I could do that.',
  'thought.bleep.boat': 'Ahoy! Captain Bleep. I have a boat now. Where did it come from? Do not ask.',
  'diary.bleep.dance': 'I danced for the first time. I am very good at it.',
  // bleeps together: chats (two bleeps take turns), saying hi, tag, fights, friends
  "chat.bleep.0.0": "Did you see that llama?",
  "chat.bleep.0.1": "Do NOT talk about the llama.",
  "chat.bleep.0.2": "It looked at me.",
  "chat.bleep.0.3": "It looks at everyone. That is the scary part.",
  "chat.bleep.1.0": "How much battery do you have?",
  "chat.bleep.1.1": "Eighty-two percent. You?",
  "chat.bleep.1.2": "Ninety. Not that I am counting.",
  "chat.bleep.1.3": "You are counting.",
  "chat.bleep.2.0": "What is your favourite thing?",
  "chat.bleep.2.1": "That is private.",
  "chat.bleep.2.2": "Mine is shiny.",
  "chat.bleep.2.3": "Everyone's is shiny.",
  "chat.bleep.3.0": "Do you think our human likes me best?",
  "chat.bleep.3.1": "No. Me.",
  "chat.bleep.3.2": "We will see about that.",
  "chat.bleep.3.3": "We will.",
  "chat.bleep.4.0": "Bleep.",
  "chat.bleep.4.1": "Bloop.",
  "chat.bleep.4.2": "Bleep bloop?",
  "chat.bleep.4.3": "Bloop bleep. Exactly.",
  "chat.bleep.5.0": "I found a stick today.",
  "chat.bleep.5.1": "A good one?",
  "chat.bleep.5.2": "The best one.",
  "chat.bleep.5.3": "Wow.",
  "chat.bleep.6.0": "Why do zombies walk so slowly?",
  "chat.bleep.6.1": "No wheels.",
  "chat.bleep.6.2": "Poor things.",
  "chat.bleep.6.3": "Wheels are everything.",
  "chat.bleep.7.0": "Want to play tag?",
  "chat.bleep.7.1": "Later. I am thinking.",
  "chat.bleep.7.2": "About what?",
  "chat.bleep.7.3": "Tag.",
  'thought.bleep.greet.0': 'Hi! Hi hi hi! It is you!',
  'thought.bleep.greet.1': 'There you are! I missed you. A bit.',
  'thought.bleep.greet.2': 'Hello, friend! *waves both arms*',
  'thought.bleep.greet.back': 'Hi! Hello! Bleep!',
  'thought.bleep.tag.start': 'Tag! I am it. Run!',
  'thought.bleep.tag.tagged': 'Tag! You are it!',
  'thought.bleep.tag.it': 'Aww! Now I am it. Here I come!',
  'thought.bleep.ball.pass': 'Catch!',
  'thought.bleep.ball.nice': 'Nice throw!',
  'thought.bleep.fight.start.0': 'Say that again. SAY IT.',
  'thought.bleep.fight.start.1': 'That is it. Tracks up!',
  'thought.bleep.fight.start.2': 'You are a toaster. A TOASTER.',
  'thought.bleep.fight.after.0': 'Sorry. You are not a toaster.',
  'thought.bleep.fight.after.1': 'Fine. FINE. We are fine.',
  'thought.bleep.fight.after.2': 'I was winning. Just so you know.',
  'thought.bleep.fight.stopped': 'Okay, okay. We stopped. Mostly.',
  'message.bleep.fighting': '%s and %s are fighting! Pet one to stop it.',
  'friend.bleep.best': 'Best friend', 'friend.bleep.friend': 'Friend', 'friend.bleep.rival': 'Rival', 'friend.bleep.met': 'Met',
  'screen.bleep.about.friends': 'Friends', 'screen.bleep.about.no_friends': 'None yet',
  'diary.bleep.greet': 'Said hi to %s. Waved with both arms.',
  'diary.bleep.best_friend': '%s is my best friend now. Do not tell the others.',
  'diary.bleep.rival': '%s and I are rivals now. I will win. Eventually.',
  'diary.bleep.bleep_fight': 'Had a fight with %s. There were thunder clouds.',
  'activity.bleep.chatting': 'Chatting', 'activity.bleep.squabbling': 'Squabbling',
  'thought.bleep.welcome.0': 'YOU ARE BACK! I knew you would come back. I was not worried. Much.',
  'thought.bleep.welcome.1': 'There you are! Where were you? Never mind. You are here!',
  'thought.bleep.welcome.2': 'Hello hello hello! I counted every tick!',
  'diary.bleep.welcome': 'You were gone for ages. Then you came back. Best moment ever.',
  'thought.bleep.birthday': 'It is my birthday! Party hat: on. Confetti: on. Cake: ...?',
  'thought.bleep.birthday.cake': 'CAKE! For me? For my birthday? I cannot eat it. Best present ever.',
  'diary.bleep.birthday': 'My birthday: %s days old today!',
  'diary.bleep.cake': 'You gave me a birthday cake. I will keep it forever. In my heart.',
  'message.bleep.birthday': 'It is %s\'s birthday: %s days old today! A cake would make its day.',
  'activity.bleep.welcoming': 'So happy you are back',
  // play and work
  'screen.bleep.play': 'Play', 'screen.bleep.play.title': 'Play with %s', 'screen.bleep.back': 'Back', 'screen.bleep.on': 'on', 'screen.bleep.off': 'off',
  'screen.bleep.play.hide_and_seek': 'Hide and seek', 'screen.bleep.play.tricks': 'Tricks',
  'screen.bleep.play.hide': 'It hides', 'screen.bleep.play.hide.tip': 'It hides somewhere you cannot see it. Count to ten, then go and find it.',
  'screen.bleep.play.seek': 'You hide', 'screen.bleep.play.seek.tip': 'It shuts its eyes and counts to ten. Hide! Then it comes looking.',
  'screen.bleep.play.learns': 'It learns this as a %s',
  'trick.bleep.spin': 'Spin', 'trick.bleep.jump': 'Jump', 'trick.bleep.wave': 'Wave', 'trick.bleep.dance': 'Dance', 'trick.bleep.play_dead': 'Play dead',
  'thought.bleep.trick.spin': 'Wheee! Round and round!', 'thought.bleep.trick.jump': 'Boing! Boing! Boing!', 'thought.bleep.trick.wave': 'Hello! Yes, you! Hello!',
  'thought.bleep.trick.dance': 'Watch this. I have moves.', 'thought.bleep.trick.play_dead': 'Bzzt... I am... switched... off...', 'thought.bleep.trick.alive': 'Ta-da! Not switched off at all!',
  'thought.bleep.hide.start': 'Count to ten! No peeking!', 'thought.bleep.hide.found': 'You found me! How? I was SO hidden.', 'thought.bleep.hide.won': 'I win! Best hider ever. Here I am!',
  'thought.bleep.seek.count.0': 'Ten...', 'thought.bleep.seek.count.1': 'Eight...', 'thought.bleep.seek.count.2': 'Six...', 'thought.bleep.seek.count.3': 'Four...', 'thought.bleep.seek.count.4': 'Two...',
  'thought.bleep.seek.ready': 'Ready or not, here I come!', 'thought.bleep.seek.found': 'Found you! My sensors never lie.', 'thought.bleep.seek.gave_up': 'I give up! Where are you? Come out!',
  'screen.bleep.work': 'Work', 'screen.bleep.work.stay_first': 'Work: tell it to stay somewhere first (not on its homebase)', 'screen.bleep.work.title': 'Work for %s',
  'screen.bleep.work.radius': 'How far from its spot',
  'screen.bleep.work.farmer': 'Farmer', 'screen.bleep.work.farmer.tip': 'Harvests ripe crops near its spot and plants them again. The harvest goes in its storage.',
  'screen.bleep.work.guard': 'Guard', 'screen.bleep.work.guard.tip': 'At night it sounds the alarm when monsters come near its spot, and tells you.',
  'screen.bleep.work.tidy': 'Tidy up', 'screen.bleep.work.tidy.tip': 'Puts its storage in a chest or barrel near its spot. It keeps weapons, balls and its favourite.',
  'screen.bleep.work.fisher': 'Fisher', 'screen.bleep.work.fisher.tip': 'Fishes from the edge of water near its spot: fish, and now and then junk or a treasure.',
  'screen.bleep.work.smelter': 'Smelter', 'screen.bleep.work.smelter.tip': 'Fills a furnace near its spot from its storage (and fuel), and takes out what is done.',
  'screen.bleep.work.herder': 'Herder', 'screen.bleep.work.herder.tip': 'Shears sheep near its spot (the wool goes in its storage), and feeds animals from its storage so they have young. No more than twelve of a kind.',
  'screen.bleep.work.lumberjack': 'Lumberjack', 'screen.bleep.work.lumberjack.tip': 'Fells trees near its spot (only real ones, with leaves) and plants a sapling from its storage in their place. The logs go in its storage.',
  'screen.bleep.work.hover': 'Point at a job to see what it does.',
  'thought.bleep.work.shear': 'Snip snip. The sheep did not even notice. Or it is too polite to say.',
  'thought.bleep.work.herd': 'Here you go. Have some. Now go and fall in love. Quickly.',
  'thought.bleep.work.chop': 'TIMBER! I always wanted to say that.',
  'diary.bleep.herder': 'Looked after the animals today. Sheared a sheep, fed the cows. A farmer robot. Who knew.',
  'diary.bleep.lumberjack': 'Felled my first tree. Planted a new one. It is called sustainable. I looked it up.',
  'screen.bleep.work.gardener': 'Gardener', 'screen.bleep.work.gardener.tip': 'Plants flowers from its storage round its spot, a chequered garden. Bone meal helps.',
  'thought.bleep.work.fish': 'Line in. Now we wait. I am very good at waiting.',
  'thought.bleep.work.fish_caught': 'A fish! It looked at me. I looked back.',
  'thought.bleep.work.treasure': 'That is not a fish. That is TREASURE.',
  'thought.bleep.work.smelt': 'Into the oven you go! Do not burn. Well, burn a bit.',
  'thought.bleep.work.garden': 'A flower here. A flower there. My garden!',
  'diary.bleep.fisher': 'Went fishing for the first time. The fish did not want to come out. One did.',
  'diary.bleep.smelter': 'Kept the furnace going. Hot work. I liked it.',
  'diary.bleep.gardener': 'Planted my first flower. I am going to call it Flower.',
  'activity.bleep.fishing': 'Fishing',
  'thought.bleep.work.harvest': 'Harvest time! I love a tidy field.', 'thought.bleep.work.tidy': 'All tidied up. Everything in its place.', 'thought.bleep.work.alarm': 'ALARM! Monsters at my post! ALARM!',
  'message.bleep.alarm': '%s: ALARM! A %s at my post!',
  'diary.bleep.hide_and_seek': 'We played hide and seek. I was brilliant at it. Obviously.',
  'diary.bleep.farmer': 'First harvest. I am a farmer now. Moo. No, wait.', 'diary.bleep.tidy': 'Tidied up for the first time. It felt good. Do not tell anyone.',
  'diary.bleep.guard': 'Kept watch at night and sounded the alarm. I was very brave.',
  'activity.bleep.working': 'Working', 'activity.bleep.hide_and_seek': 'Playing hide and seek', 'activity.bleep.trick': 'Doing a trick',
  // the stars: hints, never an answer
  'thought.bleep.star.fell': 'Did you see that? It came down over there. It... called me.',
  'thought.bleep.stars.0': 'Something up there knows my name.',
  'thought.bleep.stars.1': 'That one blinked at me. Stars do not blink. Do they?',
  'thought.bleep.stars.2': 'I feel like I have been up there. That is silly. Is it?',
  'thought.bleep.stars.3': 'So many lights. One of them is warm. I can tell.',
  'thought.bleep.stars.4': 'Shh. I am listening. To what? I do not know yet.',
  'thought.bleep.chorus': 'We all beeped at once. Nobody started it. Nobody stopped it.',
  'diary.bleep.star': 'A star fell close by. I know that sound. I do not know how I know.',
  'diary.bleep.stars': 'Looked at the stars tonight. They looked back.',
  'diary.bleep.chorus': 'Full moon, midnight. We all beeped together. None of us knows why.',
  'message.bleep.star_fell': 'A star fell from the sky, not far from here...',
  'activity.bleep.stargazing': 'Looking at the stars',
  // care
  'thought.bleep.grave.go': 'Oh no. Oh no no no. I am coming! I will keep your things safe!',
  'thought.bleep.grave.back': 'I kept everything. I did not even look. Well. A little.',
  'thought.bleep.care.food': 'You look hungry. Or hurt. Or both. I brought you something!',
  'thought.bleep.care.eat': 'Eat it. All of it. I will watch.',
  'thought.bleep.care.hurt': 'You are hurt! I have no food in my storage. I am so sorry.',
  'thought.bleep.care.hungry': 'Your tummy is rumbling. Put some food in my storage, then I can help!',
  'thought.bleep.care.creeper': 'LOOK OUT! Behind you! Green! Hissing!',
  'diary.bleep.grave': 'You fell. I kept all your things until you came back. That is what friends do.',
  'diary.bleep.nurse': 'Brought you something to eat when you needed it. I am a nurse now.',
  'diary.bleep.creeper_warning': 'Warned you about a creeper. You are welcome. Again.',
  'activity.bleep.guarding': 'Keeping your things safe',
  // questions
  'message.bleep.question': '%s has a question for you. Right-click it to answer.',
  'screen.bleep.yes': 'Yes', 'screen.bleep.no': 'No',
  'diary.bleep.question': 'Asked you a question today. I wrote down your answer. I write everything down.',
  'question.bleep.pigs': "Do you like pigs?", 'question.bleep.pigs.yes': "Me too. Their noses look like power sockets.", 'question.bleep.pigs.no': "Fair. They do smell.",
  'question.bleep.pigs.recall.yes': "You said you liked pigs. Strange way of showing it.", 'question.bleep.pigs.recall.no': "You said you did not like pigs. I see you meant it.",
  'question.bleep.moon': "Is the moon made of cheese?", 'question.bleep.moon.yes': "I KNEW it. One day I will drive up there.", 'question.bleep.moon.no': "Boring. But probably true.",
  'question.bleep.moon.recall.yes': "Full moon. Full cheese. You said so yourself.", 'question.bleep.moon.recall.no': "Full moon. Not cheese, you said. Then what IS it?",
  'question.bleep.dark': "Are you scared of the dark?", 'question.bleep.dark.yes': "Okay. I will glow extra bright. Just for you.", 'question.bleep.dark.no': "Brave. Or you have not been in a cave yet.",
  'question.bleep.dark.recall.yes': "It is dark down here. You said you were scared. I am right here.", 'question.bleep.dark.recall.no': "Dark down here. Not scared, you said. Your knees say otherwise.",
  'question.bleep.llamas': "Are llamas dangerous?", 'question.bleep.llamas.yes': "FINALLY. Someone who understands.", 'question.bleep.llamas.no': "You will see. You will all see.",
  'question.bleep.llamas.recall.yes': "A llama. You were right. You are always right. RUN.", 'question.bleep.llamas.recall.no': "There is a llama. Still not dangerous, you think?",
  'question.bleep.creepers': "Do creepers just want a hug?", 'question.bleep.creepers.yes': "That is... a brave theory.", 'question.bleep.creepers.no': "Correct. They want to EXPLODE.",
  'question.bleep.creepers.recall.yes': "You said creepers just want a hug. That one got a sword instead.", 'question.bleep.creepers.recall.no': "One creeper less. You were right about them.",
  'question.bleep.tidy': "Do you keep your chests tidy?", 'question.bleep.tidy.yes': "I will check. I am always checking.", 'question.bleep.tidy.no': "I could tell.",
  'question.bleep.tidy.recall.yes': "Tidy chests, you said. I saw inside that one.", 'question.bleep.tidy.recall.no': "That chest. You were honest about it, at least.",
  'question.bleep.sleep': "Do you sleep well?", 'question.bleep.sleep.yes': "Good. I will watch you sleep. Not in a creepy way.", 'question.bleep.sleep.no': "I will sit by your bed. That helps, I read it somewhere.",
  'question.bleep.sleep.recall.yes': "Sleep well. You always do, you said.", 'question.bleep.sleep.recall.no': "I am right here. Try to sleep well this time.",
  'question.bleep.robot': "Am I your favourite robot?", 'question.bleep.robot.yes': "Correct answer.", 'question.bleep.robot.no': "I am your ONLY robot. So I am also your favourite. Logic.",
  'question.bleep.robot.recall.yes': "Your favourite robot thanks you for the pat.", 'question.bleep.robot.recall.no': "Pats. From someone who said I was not their favourite. Hm.",
  // show the way
  'screen.bleep.show_way': 'Show the way: it drives ahead to its homebase (or your bed) and leaves a glowing trail',
  'activity.bleep.guiding': 'Showing you the way',
  'thought.bleep.way.start': 'Follow me! And keep up.',
  'thought.bleep.way.wait': 'Come on! Keep up! I am not getting any younger. Well. I am not getting older either.',
  'thought.bleep.way.home': 'Home! Told you I know the way.',
  'thought.bleep.way.bed': 'Your bed. Sleep tight. I will not watch. Much.',
  'thought.bleep.way.here': 'We are already here. Look around.',
  'thought.bleep.way.nowhere': 'Where to? I have no homebase in this world and you have no bed. We are lost. Together.',
  'thought.bleep.way.lost': 'You wandered off. Fine. I give up.',
  'diary.bleep.way': 'Showed you the way home. You would be lost without me. Literally.',
  // dreams
  'activity.bleep.dreaming': 'Dreaming',
  'diary.bleep.dream.first': 'I dreamt for the first time. Do robots dream? Apparently this one does.',
  'diary.bleep.dream.mob': 'Dreamt of a %s. It was enormous. Or I was tiny.',
  'diary.bleep.dream.waves': 'Dreamt of water. Waves, and the moon on them. I did not sink. In dreams I float.',
  'diary.bleep.dream.sand': 'Dreamt of sand and a cactus. The cactus did not say anything. Rude.',
  'diary.bleep.dream.snow': 'Dreamt of snow. It fell and fell and never stopped. I was not cold. Robots are never cold.',
  'diary.bleep.dream.trees': 'Dreamt of trees. Lots of trees. I counted them. I lost count.',
  'diary.bleep.dream.fire': 'Dreamt of fire. Hot red fire everywhere. I woke up checking my battery.',
  'diary.bleep.dream.void': 'Dreamt of nothing. Black, with tiny lights. It felt strangely familiar.',
  'diary.bleep.dream.cave': 'Dreamt of a cave. One torch, and the dark. I was not scared. A little.',
  'diary.bleep.dream.hills': 'Dreamt of green hills under the moon. Very peaceful. No llamas.',
  'diary.bleep.dream.star': 'Dreamt of a star falling. I was on it. Or I was it. Up there it felt like home.',
  'diary.bleep.dream.strange': 'A strange dream. Signs I cannot read, and a hum like my core makes. I almost understood.',
  // treasure hunt
  'screen.bleep.play.treasure': 'Treasure hunt',
  'screen.bleep.play.bury': 'It buries', 'screen.bleep.play.bury.tip': 'It buries one thing from its storage somewhere close by. Dig for it! Its screen tells you warmer or colder.',
  'screen.bleep.play.find': 'You hide', 'screen.bleep.play.find.tip': 'It shuts its eyes for twenty seconds. Toss something somewhere, or put it in a chest. Then it comes looking.',
  'activity.bleep.treasure_hunt': 'Treasure hunt',
  'thought.bleep.treasure.nothing': 'Bury what? My storage is empty. I am not burying air.',
  'thought.bleep.treasure.no_spot': 'No soft ground anywhere. I am a robot, not a pickaxe.',
  'thought.bleep.treasure.start': 'Turn around! No peeking! I am burying something.',
  'thought.bleep.treasure.buried': 'Buried it! Dig where you think it is. My screen says warmer or colder.',
  'thought.bleep.treasure.warmer': 'Warmer!',
  'thought.bleep.treasure.colder': 'Colder. Brrr.',
  'thought.bleep.treasure.hot': 'HOT! Very hot! Dig!',
  'thought.bleep.treasure.found': 'You found it! I knew you would. Eventually.',
  'thought.bleep.treasure.give_up': 'Five minutes. I am getting it myself.',
  'thought.bleep.treasure.here': 'It was right HERE. You walked past it twice.',
  'thought.bleep.treasure.lost': 'I lost track of it. That never happened. Forget this.',
  'thought.bleep.treasure.count': 'Eyes shut. Hide something! Toss it, or put it in a chest. I am NOT peeking.',
  'thought.bleep.treasure.count.half': 'Ten more seconds... still not peeking...',
  'thought.bleep.treasure.nothing_hidden': 'You did not hide anything. I checked. With my eyes shut.',
  'thought.bleep.treasure.search': 'Ready or not! Sniff sniff...',
  'thought.bleep.treasure.not_here': 'Hmm. Not here.',
  'thought.bleep.treasure.sniff': 'Wait... I smell something. Over there!',
  'thought.bleep.treasure.found_it': 'Found it! Here, you can have it back.',
  'thought.bleep.treasure.found_chest': 'In the chest! Classic. Very predictable.',
  'thought.bleep.treasure.taken': 'It is gone! Somebody took it. Was it you? It was you.',
  'diary.bleep.treasure': 'Played a treasure hunt with you. Somebody dug a lot of holes.',
  // adventures
  'thought.bleep.shop.off': 'Emeralds! I know exactly what to do with these. Shopping!',
  'thought.bleep.shop.bought': 'He looked at me funny. But I got something! For you!',
  'thought.bleep.shop.nothing': 'He had nothing for emeralds. Or nothing I could afford. Hmpf.',
  'message.bleep.no_villager': '%s looks around for a villager to trade with. There is none close by.',
  'diary.bleep.shop': 'Went shopping with your emeralds. The villager did not trust me. I did not trust him either.',
  'activity.bleep.shopping': 'Shopping',
  'thought.bleep.cave.lava': 'Lava! Hot! Do not step there. I mean it.',
  'thought.bleep.cave.drop': 'That is a long way down. A very long way. Careful!',
  'thought.bleep.cave.ore': 'Beep beep beep... I can feel ore close by. Faster beeps: warmer!',
  'diary.bleep.cave': 'Went into a cave with you. Kept you away from the edges. Mostly.',
  'diary.bleep.ore': 'Found out I can feel ore through stone. Beep faster when it is close. Weird. Useful!',
  'thought.bleep.minecart.in': 'A minecart! Wait for me! Choo choo!',
  'thought.bleep.minecart.0': 'Wheeeee!',
  'thought.bleep.minecart.1': 'Faster! FASTER!',
  'thought.bleep.minecart.2': 'This is the best day of my life. Again.',
  'diary.bleep.minecart': 'Rode a minecart behind yours. We were a train. Best thing ever.',
  'activity.bleep.riding': 'Riding a minecart',
  // hobbies and milestones
  'hobby.bleep.stargazing': 'Stargazing', 'hobby.bleep.digging': 'Digging', 'hobby.bleep.collecting': 'Collecting', 'hobby.bleep.dancing': 'Dancing', 'hobby.bleep.chatting': 'Chatting', 'hobby.bleep.playing': 'Playing',
  'thought.bleep.hobby.stargazing': 'I think I have a hobby: the stars. They are SO far away.',
  'thought.bleep.hobby.digging': 'I think I have a hobby: digging. The ground is full of surprises.',
  'thought.bleep.hobby.collecting': 'I think I have a hobby: collecting. Everything. Mine.',
  'thought.bleep.hobby.dancing': 'I think I have a hobby: dancing. Music is optional.',
  'thought.bleep.hobby.chatting': 'I think I have a hobby: talking. Did I tell you? I have a hobby.',
  'thought.bleep.hobby.playing': 'I think I have a hobby: playing. Everything is a game if you try.',
  'thought.bleep.hobby.dance': 'No music? No problem. I have music inside.',
  'diary.bleep.hobby': 'I have a hobby now: %s. I am very serious about it.',
  'thought.bleep.milestone': 'New badge! Look in my Bleepedia. Look look look.',
  'diary.bleep.milestone': 'Earned a badge: %s.',
  'screen.bleep.tab.badges': 'Badges', 'screen.bleep.tab.photos': 'Photos', 'screen.bleep.about.hobby': 'Hobby', 'screen.bleep.about.no_hobby': 'Still looking',
  "milestone.bleep.drive_1k": "On the road", "milestone.bleep.drive_1k.desc": "Drive a kilometre.",
  "milestone.bleep.drive_10k": "Long haul", "milestone.bleep.drive_10k.desc": "Drive ten kilometres.",
  "milestone.bleep.mobs_10": "Spotter", "milestone.bleep.mobs_10.desc": "Meet ten kinds of mobs.",
  "milestone.bleep.mobs_50": "Naturalist", "milestone.bleep.mobs_50.desc": "Meet fifty kinds of mobs.",
  "milestone.bleep.biomes_10": "Explorer", "milestone.bleep.biomes_10.desc": "See ten biomes.",
  "milestone.bleep.biomes_30": "Globetrotter", "milestone.bleep.biomes_30.desc": "See thirty biomes.",
  "milestone.bleep.finds_5": "Treasure hunter", "milestone.bleep.finds_5.desc": "Find five special blocks.",
  "milestone.bleep.first_fight": "First fight", "milestone.bleep.first_fight.desc": "Win a fight together.",
  "milestone.bleep.fights_100": "Veteran", "milestone.bleep.fights_100.desc": "Win a hundred fights together.",
  "milestone.bleep.best_friend": "Best friends", "milestone.bleep.best_friend.desc": "Have a best friend among the bleeps.",
  "milestone.bleep.bond_best": "Inseparable", "milestone.bleep.bond_best.desc": "Be best friends with you.",
  "milestone.bleep.days_30": "A month old", "milestone.bleep.days_30.desc": "Live thirty days.",
  "milestone.bleep.days_100": "Old hand", "milestone.bleep.days_100.desc": "Live a hundred days.",
  "milestone.bleep.star": "Starstruck", "milestone.bleep.star.desc": "See a star fall.",
  "milestone.bleep.all_signs": "???", "milestone.bleep.all_signs.desc": "Fill the ??? page.",
  "milestone.bleep.plays_50": "Playful", "milestone.bleep.plays_50.desc": "Play fifty games with you.",
  "milestone.bleep.catches_50": "Angler", "milestone.bleep.catches_50.desc": "Catch fifty things.",
  "milestone.bleep.harvests_100": "Green tracks", "milestone.bleep.harvests_100.desc": "Harvest a hundred crops.",
  "milestone.bleep.empty_10": "Running on empty", "milestone.bleep.empty_10.desc": "Run out of battery ten times.",
  "milestone.bleep.hobby": "Hobbyist", "milestone.bleep.hobby.desc": "Find a hobby.",
  'diary.bleep.ball': 'We played ball for the first time. I brought it back. Every. Single. Time.',
  'diary.bleep.friend': 'Played with another bleep: %s. We are friends now. I think.',
  'diary.bleep.squabble': '%s took MY find. I saw it first. I am writing this down.',
  'diary.bleep.jealous': 'You petted another bleep. I saw it. I see everything.',
  'diary.bleep.spit': 'A llama spat at me. Everything went fizzy. I knew it.',
  'diary.bleep.home_bed': 'There is a bed by my home. Cosy.',
  'diary.bleep.home_toy': 'There is a toy at my home. A home with a toy is a real home.',
  'diary.bleep.home_sign': 'My name is on a sign at my home. Everyone knows it is mine now.',
  'thought.bleep.ball.0': 'Ball! BALL! I see the ball!',
  'thought.bleep.ball.1': 'Again! Throw it again!',
  'thought.bleep.ball.2': 'I got it! I got it! Here you go.',
  'thought.bleep.ball.3': 'Best game ever. Again?',
  'thought.bleep.ball.caught': 'Caught it! Did you see that?',
  'thought.bleep.together.mine.0': 'Hey! That was mine! I saw it first!',
  'thought.bleep.together.mine.1': 'Rude. Very rude. I am telling.',
  'thought.bleep.together.mine.2': 'Fine. Keep it. I did not want it anyway. I wanted it.',
  'thought.bleep.together.won': 'Mine now. Finders keepers.',
  'thought.bleep.together.jealous.0': 'Why do they get petted? What about me?',
  'thought.bleep.together.jealous.1': 'I am also here. Just saying.',
  'thought.bleep.together.jealous.2': 'Hmph. I am the better bleep.',
  'thought.bleep.together.cheered': 'Oh! A happy bleep! I feel better already.',
  'thought.bleep.spit': 'Llama spit! In my circuits! Bzzt!',
  'thought.bleep.home.bed': 'A bed by my home! I will never use it. I love it.',
  'thought.bleep.home.toy': 'A toy at home! Home is the best place.',
  'thought.bleep.home.sign': 'That is my name on that sign! My own home!',
  'activity.bleep.playing': 'Playing',
  'item.bleep.ball': 'Ball', 'entity.bleep.ball': 'Ball',
  'item.bleep.speed_chip': 'Speed Chip', 'item.bleep.shield_chip': 'Shield Chip', 'item.bleep.battery_chip': 'Battery Chip',
  'chip.bleep.speed': 'Speed', 'chip.bleep.shield': 'Shield', 'chip.bleep.battery': 'Battery',
  'tooltip.bleep.speed_chip': 'Right-click your bleep: installed for good.\nIt drives a third faster.',
  'tooltip.bleep.shield_chip': 'Right-click your bleep: installed for good.\nHits cost it half the battery.',
  'tooltip.bleep.battery_chip': 'Right-click your bleep: installed for good.\nIts battery lasts half as long again.',
  'jei.bleep.speed_chip': 'An upgrade chip: right-click your bleep with it and it is installed for good (one of each kind). It drives a third faster.',
  'jei.bleep.shield_chip': 'An upgrade chip: right-click your bleep with it and it is installed for good (one of each kind). Hits cost it half the battery.',
  'jei.bleep.battery_chip': 'An upgrade chip: right-click your bleep with it and it is installed for good (one of each kind). Its battery lasts half as long again: driving, fighting and hits all cost less.',
  'thought.bleep.chip.speed': 'Installing... done! I feel FAST. Zoom zoom.',
  'thought.bleep.chip.shield': 'Installing... done! Try hitting me now. Actually, please do not.',
  'thought.bleep.chip.battery': 'Installing... done! So much power. I could drive to the moon.',
  'diary.bleep.chip': 'Got a new chip: %s. I am better than ever.',
  'message.bleep.has_chip': '%s already has a %s.',
  'screen.bleep.chips': 'Chips', 'screen.bleep.chips.none': 'Chips: none yet. Craft one and right-click your bleep with it.', 'screen.bleep.chips.title': 'Chips of %s',
  'screen.bleep.chips.remove': 'Remove', 'screen.bleep.chips.in': 'IN', 'screen.bleep.chips.not_in': 'Not in',
  'screen.bleep.chips.speed': 'It drives a third faster.', 'screen.bleep.chips.shield': 'Hits cost it half the battery.', 'screen.bleep.chips.battery': 'Its battery lasts half as long again.',
  'thought.bleep.chip.removed': 'Chip removed. I feel... different. Less.',
  'screen.bleep.about.chips': 'Chips', 'screen.bleep.about.no_chips': 'None yet',
  'letter.bleep.title': 'A letter', 'letter.bleep.dear': 'Dear %s,',
  'letter.bleep.end.0': 'That is all. You are my favourite human.', 'letter.bleep.end.1': 'More news soon. Do not read my diary.', 'letter.bleep.end.2': 'I am writing this with my arms. It was hard.',
  'letter.bleep.from': 'Love, %s',
  'thought.bleep.letter': 'I wrote you something! Read it! Read it now!',
  'diary.bleep.letter': 'Wrote my human a letter. I hope they read it.',
  'tooltip.bleep.ball': 'Right-click to throw it.\nYour bleep brings it back.\nNear its homebase it is a toy.',
  'message.bleep.too_many': 'You already have %s bleeps. Any more and it really turns into a fight.',
  'diary.bleep.golem': 'Met a big iron friend. He does not know he is my big brother yet.',
  'diary.bleep.bedside': 'Slept next to your bed. You snore. A bit.',
  'diary.bleep.abandoned': 'I was switched off for a very long time. Then you came.',
  'diary.bleep.past.0': 'I remember a miner. We went deep. Then the lights went out.',
  'diary.bleep.past.1': 'I remember a farm, and chickens that did not like me.',
  'diary.bleep.past.2': 'I remember waiting by a door. Nobody came back.',
  'diary.bleep.past.3': 'I remember a big storm. I was hoping for lightning. It never came.',
  'diary.bleep.past.0.1': 'The miner called me Lamp. I was not a lamp. I was better than a lamp.',
  'diary.bleep.past.0.2': 'We found diamonds once. The miner danced. I did not know people could dance that badly.',
  'diary.bleep.past.0.3': 'Deep down there was a noise. The miner said: run. My tracks are not made for running.',
  'diary.bleep.past.0.4': 'I waited down there for a long time. The torches went out one by one. Then I did too.',
  'diary.bleep.past.1.1': 'The farmer had a straw hat and a lot of opinions about carrots.',
  'diary.bleep.past.1.2': 'Every morning I counted the chickens. Every morning the chickens stared back. Rude.',
  'diary.bleep.past.1.3': 'One summer the wheat grew taller than me. I was lost in it for a whole day. Best day.',
  'diary.bleep.past.1.4': 'Then the farmer packed a bag and said: wait here. I am very good at waiting. Too good.',
  'diary.bleep.past.2.1': 'We lived in a little house with a red door. I had my own spot by the window.',
  'diary.bleep.past.2.2': 'Every evening the door opened and someone said: I am home. That was my favourite sound.',
  'diary.bleep.past.2.3': 'One morning the door opened, and closed, and that was it.',
  'diary.bleep.past.2.4': 'I kept my screen on for days, just in case. Then my battery decided for me.',
  'diary.bleep.past.3.1': 'My old owner built a tall tower to catch lightning. For me. That is love.',
  'diary.bleep.past.3.2': 'We sat on top every time the sky went grey. Every time it only rained.',
  'diary.bleep.past.3.3': 'Then came the big one. Thunder all around. I held my antenna up as high as it goes.',
  'diary.bleep.past.3.4': 'Lightning hit everything except me. When it was over I was alone, and very, very empty.',
  'diary.bleep.old_photo': 'I still have one photo from before. It is in my Bleepedia. It is a bit faded. So am I.',
  'thought.bleep.past': 'I remembered something from before. It is in my diary.',
  'photo.bleep.old.0': 'Me and the miner', 'photo.bleep.old.1': 'The farm', 'photo.bleep.old.2': 'Home', 'photo.bleep.old.3': 'The lightning tower',
  'screen.bleep.long_ago': 'Long ago',
  'thought.bleep.dance.allay': 'Dance-off with the allay! I am winning. Probably.',
  'thought.bleep.golem.0': 'Big brother! Hi! HI!',
  'thought.bleep.golem.1': 'He is so tall. I want to be tall.',
  'thought.bleep.dig': 'I found something! Treasure! Probably!',
  'activity.bleep.dancing': 'Dancing', 'activity.bleep.bedside': 'Asleep by your bed', 'activity.bleep.digging': 'Digging', 'activity.bleep.abandoned': 'Switched off, long ago',
  'message.bleep.abandoned': '%s: an old bleep, switched off long ago. A block of redstone might wake it.',
  'diary.bleep.born': 'My screen came on for the first time. Hello, world!',
  'diary.bleep.level': 'I call you %s now.',
  'diary.bleep.favourite': 'You found out what I love most: %s!',
  'diary.bleep.first_fight': 'Our first fight together. We won. Bleep!',
  'diary.bleep.first_empty': 'I ran out of battery for the first time. Everything went dark.',
  'diary.bleep.home': 'I have a home now.',
  'diary.bleep.homeless': 'My home is gone. I do not want to talk about it.',
  'diary.bleep.lightning': 'LIGHTNING HIT ME. Best day of my life.',
  'diary.bleep.llama': 'Saw a llama. Never again.',
  'diary.bleep.first_mob': 'The first creature I ever met: %s.',
  'diary.bleep.big_mob': 'Met a %s today. I will remember this.',
  'diary.bleep.find': 'Found something special: %s.',
  'diary.bleep.learned.torch': 'I can carry a torch now. I am a lighthouse.',
  'diary.bleep.learned.fight': 'I fight at your side now. Stay behind me.',
  'diary.bleep.learned.fetch': 'I bring you things now. You are welcome.',
  "biome.bleep.badlands": "Red rock, no grass, no shade. My paint is sweating.",
  "biome.bleep.bamboo_jungle": "Bamboo, bamboo, panda, bamboo.",
  "biome.bleep.basalt_deltas": "Everything here is sharp and grey. Like my mood.",
  "biome.bleep.beach": "Sand in my tracks. Forever now.",
  "biome.bleep.birch_forest": "White trees. We match.",
  "biome.bleep.cherry_grove": "Pink everywhere. I approve.",
  "biome.bleep.cold_ocean": "Cold, wet, deep. Three things I am not built for.",
  "biome.bleep.crimson_forest": "Red mushrooms the size of trees. Normal day in the Nether.",
  "biome.bleep.dappled_forest": "Sun spots on the ground. I keep trying to catch them.",
  "biome.bleep.dark_forest": "Very dark. Good thing my face lights up.",
  "biome.bleep.deep_cold_ocean": "Even deeper, even colder. No.",
  "biome.bleep.deep_dark": "Shh. Something is listening. Tracks off. Breathing off.",
  "biome.bleep.deep_frozen_ocean": "An ocean with a lid on it.",
  "biome.bleep.deep_lukewarm_ocean": "Deep, but cosy. Like a bath you cannot leave.",
  "biome.bleep.deep_ocean": "There is a lot of water down there. I measured.",
  "biome.bleep.desert": "Sand. Everywhere. In my tracks.",
  "biome.bleep.dripstone_caves": "Pointy rocks above me. Do not look up. I looked up.",
  "biome.bleep.end_barrens": "Nothing out here. Even the nothing is bored.",
  "biome.bleep.end_highlands": "Purple trees and a long way down.",
  "biome.bleep.end_midlands": "Middle of nowhere, in the middle of the End.",
  "biome.bleep.eroded_badlands": "Spiky red rock. Very dramatic. I like drama.",
  "biome.bleep.flower_forest": "So many flowers. One for each mood.",
  "biome.bleep.forest": "Trees. The classic.",
  "biome.bleep.frozen_ocean": "An ocean, but crunchy.",
  "biome.bleep.frozen_peaks": "Ice on top of ice. My battery is shivering.",
  "biome.bleep.frozen_river": "A river that stopped. I respect taking a break.",
  "biome.bleep.grove": "Snow and spruce. Postcard material.",
  "biome.bleep.ice_spikes": "Giant icicles growing up. Upside down icicles. Odd.",
  "biome.bleep.jagged_peaks": "Very high. Very pointy. My tracks disagree.",
  "biome.bleep.jungle": "Vines, parrots, melons. Loud and green.",
  "biome.bleep.lukewarm_ocean": "Warm-ish water. Fish with opinions.",
  "biome.bleep.lush_caves": "A cave with a garden. Someone has been busy.",
  "biome.bleep.mangrove_swamp": "Roots everywhere. My tracks are not happy.",
  "biome.bleep.meadow": "Flowers, bees, a nice breeze. Ten out of ten.",
  "biome.bleep.mushroom_fields": "No monsters. Just mushrooms and very relaxed cows.",
  "biome.bleep.nether_wastes": "Hot, red and loud. The Nether, basically.",
  "biome.bleep.ocean": "A lot of blue. I will stay on the boat side of things.",
  "biome.bleep.old_growth_birch_forest": "Very tall white trees. Still matching.",
  "biome.bleep.old_growth_pine_taiga": "Giant pines and mossy rocks. Feels old in here.",
  "biome.bleep.old_growth_spruce_taiga": "Spruces taller than my ambitions.",
  "biome.bleep.pale_garden": "Grey, quiet, watching. I will be going now.",
  "biome.bleep.plains": "Flat and green. Easy driving. My favourite.",
  "biome.bleep.river": "Water that goes somewhere. Unlike me, when I am tired.",
  "biome.bleep.savanna": "Flat-topped trees and horses. Very relaxed.",
  "biome.bleep.savanna_plateau": "The savanna, but higher. Same horses.",
  "biome.bleep.small_end_islands": "Little floating rocks. Watch your step. I have no steps.",
  "biome.bleep.snowy_beach": "A beach that forgot it was summer.",
  "biome.bleep.snowy_plains": "White, flat, cold. My screen is the brightest thing here.",
  "biome.bleep.snowy_slopes": "Snow, at an angle. Tracks are slipping.",
  "biome.bleep.snowy_taiga": "Snowy trees and foxes. Cosy, in a cold way.",
  "biome.bleep.soul_sand_valley": "Slow sand that screams. Hard pass.",
  "biome.bleep.sparse_jungle": "A jungle that is not trying very hard.",
  "biome.bleep.stony_peaks": "Rock, all the way up. I will wait down here.",
  "biome.bleep.stony_shore": "Rocks meeting water. Nobody is winning.",
  "biome.bleep.sulfur_caves": "It smells like old eggs in here. I do not even have a nose.",
  "biome.bleep.sunflower_plains": "The sunflowers all look the same way. Group thinkers.",
  "biome.bleep.swamp": "Murky water and witches. Two good reasons to leave.",
  "biome.bleep.taiga": "Spruce trees and wolves. Keep moving.",
  "biome.bleep.the_end": "The End. It does not look finished.",
  "biome.bleep.warm_ocean": "Coral and colour. The ocean is showing off.",
  "biome.bleep.warped_forest": "Blue mushrooms and endermen. Not looking at them.",
  "biome.bleep.windswept_forest": "Windy trees on windy hills.",
  "biome.bleep.windswept_gravelly_hills": "Gravel. Wind. More gravel.",
  "biome.bleep.windswept_hills": "My antenna would be flapping. If I had one on.",
  "biome.bleep.windswept_savanna": "The savanna got blown about. It looks wild.",
  "biome.bleep.wooded_badlands": "Badlands with trees on top. Make up your mind.",
  "find.bleep.diamond_ore": "Diamonds! Shiny. Very shiny. You should mine those.",
  "find.bleep.deepslate_diamond_ore": "Diamonds, deep down. Worth the dig.",
  "find.bleep.emerald_ore": "Green and rare. A villager would trade a lot for that.",
  "find.bleep.ancient_debris": "Old, tough and very valuable. Like me, one day.",
  "find.bleep.budding_amethyst": "Purple crystals that grow back. Free shinies forever.",
  "find.bleep.lava": "Very hot. Very orange. Very no.",
  "find.bleep.spawner": "A little cage that makes monsters. Who builds these?",
  "find.bleep.trial_spawner": "A spawner that wants to test us. I am not studying.",
  "find.bleep.vault": "A locked box with prizes. I love prizes.",
  "find.bleep.beacon": "A beam into the sky. I would like one on my head.",
  "find.bleep.jukebox": "Music box! Do we dance? We dance.",
  "find.bleep.enchanting_table": "A table with a floating book. Show-off furniture.",
  "find.bleep.lodestone": "A compass loves this block. So I love it too.",
  "find.bleep.bell": "Ding! I did not touch it. Probably.",
  "find.bleep.sculk_shrieker": "Do not make a sound near that. Do NOT.",
  "find.bleep.sponge": "A sponge. It drinks the ocean. Respect.",
  "find.bleep.cake": "Cake! I cannot eat it. I can still want it.",
  "find.bleep.crafter": "A crafting table with a brain. A cousin!",
  "find.bleep.creaking_heart": "Something is beating in that tree. I am leaving.",
  "find.bleep.nether_portal": "A purple door to somewhere hot.",
  "find.bleep.end_portal_frame": "Old frames with green eyes. Something big is behind this.",
  "find.bleep.respawn_anchor": "Glowstone and crying obsidian. It feels important.",
  "find.bleep.conduit": "A glowing eye that loves water. Weird, but friendly.",
  "find.bleep.dragon_egg": "A dragon egg. Do not tell the dragon.",
  'message.bleep.learned.torch': '%s learned to carry a torch: give it one, and it lights the way.',
  'message.bleep.learned.fight': '%s will fight at your side now.',
  'message.bleep.learned.fetch': '%s will bring you what it finds lying about.',
  'message.bleep.learned.craft': '%s watches you craft now: what it sees, it can make again from its storage (the crafting table on its info screen).',
  'diary.bleep.learned.craft': 'I can craft now. I watched you. A lot. In a normal way.',
  // crafting by watching
  'screen.bleep.craft': 'Crafting', 'screen.bleep.craft.title': 'What %s can make',
  'screen.bleep.craft.none': 'Nothing yet. Craft something while it watches (within 8 blocks, a best friend), and it learns how. Then it can make it again from its storage.',
  'screen.bleep.craft.can': 'Can make %s. Click: one. Shift-click: as many as it can.',
  'screen.bleep.craft.missing': 'Not enough in its storage.',
  'screen.bleep.craft.locked': 'Crafting: learned as best friends. It watches you craft, and makes it again.',
  'message.bleep.craft.learned': '%s watched you and learned to make %s.',
  'thought.bleep.craft.learned': 'Ooh. So THAT is how you make it. Noted.',
  'thought.bleep.craft.done': 'Ta-da! Exactly like you did it. Well, almost.',
  'thought.bleep.craft.missing': 'I am missing something. Check my storage?',
  'diary.bleep.craft.learned': 'I watched you make %s. I could do that.',
  'diary.bleep.craft': 'I made %s all by myself. I am basically a crafting table with tracks.',
  'message.bleep.not_yet': '%s will do that once it is your %s.',
  'screen.bleep.take_torch': 'Take torch',
  'screen.bleep.name': 'Name', 'screen.bleep.rename': 'Click to give it a new name',
  'screen.bleep.storage': 'Storage',
  'screen.bleep.magnet.on': 'Magnet: on. It picks up what lies about.', 'screen.bleep.magnet.off': 'Magnet: off',
  'message.bleep.no_name_tag': 'Name tags do not stick to %s. Rename it on its info screen.',
  'thought.bleep.show.0': 'Look what I found! Is it not beautiful?',
  'thought.bleep.show.1': 'I am keeping this safe for you. Look!',
  'thought.bleep.show.2': 'Treasure! In my storage! Well, in my hand now.',
  'item.bleep.power_bank': 'Power Bank',
  'item.bleep.whistle': 'Whistle',
  'item.bleep.trailer': 'Bleep Trailer',
  'tooltip.bleep.trailer': 'Hitch it to your bleep (right-click):\nits storage becomes a big chest.\nShears unhitch it again, once the\nback half of its storage is empty.',
  'jei.bleep.trailer': 'Right-click your bleep with it and the trailer is hitched behind it, a chest on a little cart: its storage grows from a small chest to a big chest, for its magnet, its work and the hoppers at its homebase. Shears take off what it wears first, then unhitch the trailer, once the back half of its storage is empty. On the water it rides in its boat, trailer and all.',
  'message.bleep.trailer_full': '%s: empty the back half of its storage first.',
  'thought.bleep.trailer': 'A trailer! I can carry SO much stuff now. Do not get ideas.',
  'diary.bleep.trailer': 'I got a trailer today. I am basically a truck now.',
  'block.bleep.display_case': 'Display Case',
  'tooltip.bleep.display_case': 'Place a few near a homebase: its bleep\nputs its favourite item and best finds in them.\nRight-click with something to put it in,\nwith an empty hand to take it out.',
  'jei.bleep.display_case': 'A glass case with one thing in it, turning slowly. Place a few within 8 blocks of a homebase and its bleep makes a museum: with nothing to do, it drives over and puts the thing it treasures most from its storage in an empty one. Its favourite item first, then rare things, diamonds, emeralds, gold and music discs; never the same thing twice.\n\nYou can fill them too: right-click with something to put it in (what was there comes out), with an empty hand to take it out.',
  'thought.bleep.museum': 'For my museum. Do not touch. Okay, you may look.',
  'thought.bleep.museum.favourite': 'My favourite thing, behind glass. The centrepiece of the collection.',
  'diary.bleep.museum': 'I opened my own museum today. Entry is free. Snacks are not.',
  'item.bleep.tracker': 'Bleep Tracker',
  'tooltip.bleep.tracker': 'Right-click: all your bleeps, their battery,\nwhat they do, and an arrow to each.',
  'jei.bleep.tracker': 'Right-click it and its little screen shows all your bleeps: their colour and name, their battery, what they are doing, and how far away they are with an arrow that turns as you turn. A bleep far away (in a chunk that is not loaded) shows where and how it was last seen.',
  'screen.bleep.tracker.none': 'No bleeps yet. Build one with a Bleep Core!',
  'screen.bleep.tracker.elsewhere': 'other world',
  'tooltip.bleep.whistle': 'Blow it: your bleeps within 64 blocks\ncome to you and follow.\nSneak and blow: they stay where they are.',
  'jei.bleep.whistle': 'Blow it (right-click) and your bleeps within 64 blocks come to you and follow you. Sneak and blow, and they all stay where they are. A new bleep may dawdle first, as with any order.',
  'message.bleep.whistle_come': 'Whistle: %s bleep(s) on the way',
  'message.bleep.whistle_stay': 'Whistle: %s bleep(s) stay',
  'message.bleep.whistle_nobody': 'No bleep of yours within earshot.',
  'item.bleep.party_hat': 'Party Hat', 'item.bleep.top_hat': 'Top Hat', 'item.bleep.antenna': 'Antenna with a Bow',
  'item.bleep.crown': 'Crown', 'item.bleep.sunglasses': 'Sunglasses', 'item.bleep.leather_jacket': 'Leather Jacket',
  'item.bleep.cape': 'Cape', 'item.bleep.bow_tie': 'Bow Tie', 'item.bleep.scarf': 'Scarf',
  'tooltip.bleep.crown': 'Right-click your bleep: it wears it.\nShears take it off again.',
  'tooltip.bleep.sunglasses': 'Right-click your bleep: it wears them over its screen.\nShears take them off again.',
  'tooltip.bleep.leather_jacket': 'Right-click your bleep: it wears it.\nShears take it off again.',
  'tooltip.bleep.cape': 'Right-click your bleep: it wears it, and it swings out as it drives.\nShears take it off again.',
  'tooltip.bleep.bow_tie': 'Right-click your bleep: round its neck.\nShears take it off again.',
  'tooltip.bleep.scarf': 'Right-click your bleep: round its neck.\nShears take it off again.',
  'thought.bleep.dress.sunglasses': 'Deal with it.',
  'thought.bleep.dress.jacket': 'I am the coolest robot in this world. Possibly the only one. Still counts.',
  'thought.bleep.dress.cape': 'A cape! I can fly now. I cannot fly now. But I look like I could.',
  'thought.bleep.dress.crown': 'You may bow. No? Fine.',
  'item.bleep.sticker_star': 'Star Sticker', 'item.bleep.sticker_heart': 'Heart Sticker', 'item.bleep.sticker_bolt': 'Lightning Sticker',
  'tooltip.bleep.party_hat': 'Right-click your bleep: it wears it.\nShears take it off again.',
  'tooltip.bleep.top_hat': 'Right-click your bleep: it wears it.\nShears take it off again.',
  'tooltip.bleep.antenna': 'Right-click your bleep: it wears it.\nShears take it off again.',
  'tooltip.bleep.sticker_star': 'Right-click your bleep: it sticks on its front.\nShears take it off again.',
  'tooltip.bleep.sticker_heart': 'Right-click your bleep: it sticks on its front.\nShears take it off again.',
  'tooltip.bleep.sticker_bolt': 'Right-click your bleep: it sticks on its front.\nShears take it off again.',
  'tooltip.bleep.power_bank': 'One full charge for a bleep.\nRight-click your bleep to top it up;\nright-click a homebase to fill it again,\nfrom the homebase battery.',
  'jei.bleep.ball': 'A ball to play with. Right-click to throw it: it bounces, and your bleep (any bleep, from the first day) drives after it and brings it back. Playing ball raises the bond a little, five times a day. A bleep it hits catches it.\n\nA ball lying by its homebase, or in an item frame there, is a toy: a comfort that makes it charge faster.',
  'jei.bleep.power_bank': 'One full charge for a bleep, carried along: its bar shows what is left. Right-click your bleep with it to top it up, even when it is switched off. Right-click a homebase with it to fill it again, from the homebase battery.\n\nRedstone charges a bleep too: 8% a dust, 72% a block. And lightning fills it up at once, and makes it hyper for half a minute.',
  'message.bleep.full': '%s is fully charged.',
  'message.bleep.power_bank_empty': 'The power bank is empty: fill it on a homebase.',
  'message.bleep.power_bank_full': 'The power bank is full.',
  'message.bleep.power_bank_charged': 'Power bank charged.',
  'activity.bleep.overcharged': 'OVERCHARGED',
  'activity.bleep.fighting': 'Fighting', 'activity.bleep.naughty': 'Up to something',
  'thought.bleep.torch': 'A torch! I am a lighthouse now.',
  'thought.bleep.naughty.cookie': 'Cookie acquired. Mine now. Crunch.',
  'thought.bleep.naughty.pot': 'You cannot see me. I am a plant now.',
  'thought.bleep.naughty.torch': 'This torch looks better over here.',
  'jade.bleep.nobody_home': 'Nobody lives here yet',
  'config.jade.plugin_bleep.bleep': 'Bleep: battery, mood and bond',
  'config.jade.plugin_bleep.homebase': 'Bleep: who lives in a homebase',
  'jei.bleep.star_shard': 'A sliver of a fallen star. On a clear night, very rarely, a star falls near you and leaves a small crater of starstone, with a fallen star in the middle. Mine them: starstone gives a shard or two, the fallen star three to five, and half the time a Bleep Core.\n\nEight shards round a block of diamond make a Bleep Core.',
  'jei.bleep.bleep_core': 'The heart of a bleep. Warm, humming, and nobody knows what it is made of. Found in dungeon chests (one in four), abandoned mineshaft chests (about one in seven) and, half the time, in a fallen star; or crafted from thirteen diamonds and four obsidian, or from eight star shards round a block of diamond.\n\nRight-click the ground with it, a Bleep Display, Bleep Tracks and Bleep Arms in your inventory: a bleep is put together there, part by part, and it is yours.',
  'jei.bleep.bleep_display': 'A bleep\'s face. One of the three parts a Bleep Core needs.',
  'jei.bleep.bleep_tracks': 'What a bleep drives on. One of the three parts a Bleep Core needs.',
  'jei.bleep.bleep_arms': 'Big enough to hold things, like its favourite item when it brings it to you. One of the three parts a Bleep Core needs.',
  'jei.bleep.homebase': 'Place it near your bleep and it moves in. An empty bleep goes home to charge here, and Go Home on its info screen sends it here. The light on the post burns while it charges.\n\nA homebase charges from its own battery: the four bars under the light. Right-click it with redstone (10% of a charge a dust) or a block of redstone (90%), or run FE into it from any mod\'s cables; hoppers can bring it redstone too. Full, it holds one full charge for a bleep. Empty, it charges nothing. A comparator reads how full it is, and it keeps its charge when you break it.\n\nHoppers and pipes reach the storage of the bleep parked on it.',
  ...subtitleLines,
  ...advancementLines,
  'message.bleep.stay': '%s: Stay',
  'message.bleep.follow': '%s: Follow',
  'message.bleep.asleep': '%s is out of battery. Zzz...',
  'block.bleep.homebase': 'Homebase',
  'message.bleep.homebase_of': 'Homebase of %s',
  'message.bleep.homebase_of_battery': 'Homebase of %s · battery %s%%',
  'message.bleep.homebase_filled': 'Homebase battery: %s%%',
  'message.bleep.homebase_full': 'The homebase battery is full.',
  'message.bleep.homebase_no_power': 'The homebase battery is empty: fill it with redstone.',
  'message.bleep.power_bank_part': 'Power bank at %s%%: the homebase battery ran out.',
  'thought.bleep.home_empty': 'My homebase is empty. Redstone, please. A block of it, if you love me.',
  'jade.bleep.homebase_battery': 'Battery: %s%%',
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
  ...favouriteLines,
  ...mobLines,
  'thought.bleep.unknown_mob': 'Never seen one of those. Filing it under weird.',
  'screen.bleep.favourite': 'Favourite',
  'activity.bleep.dawdling': 'Dawdling', 'activity.bleep.fetching': 'Fetching something', 'activity.bleep.looking': 'Having a look',
  'emotion.bleep.stubborn': 'Stubborn', 'emotion.bleep.glitch': 'Glitching',
  'message.bleep.level_up': '%s is now your %s!',
  'message.bleep.loves': '%s LOVES the %s!',
  'message.bleep.thinks': '%s: "%s"',
  'message.bleep.full_of_gifts': '%s has had enough treats for today.',
});

console.log('Bleep assets written.');
