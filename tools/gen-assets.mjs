// Generates Bleep's textures, item models and language file. Run from the project root:  node tools/gen-assets.mjs
// The bleep's texture follows the boxes in client/BleepModel.java (64 x 64, Minecraft's usual box layout); the faces
// on its display are 128 x 128, so the display (10 x 5 on the model) gets 20 x 10 pixels.
import { mkdirSync, readdirSync, writeFileSync } from 'node:fs';
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
for (const part of ['speed_chip', 'shield_chip', 'battery_chip', 'ball', 'bleep_display', 'bleep_tracks', 'bleep_arms', 'power_bank', 'party_hat', 'top_hat', 'antenna', 'sticker_star', 'sticker_heart', 'sticker_bolt']) {
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
for (const [name, dye] of [['sticker_star', 'yellow_dye'], ['sticker_heart', 'pink_dye'], ['sticker_bolt', 'light_blue_dye']]) {
  json(join(DATA, 'recipe/' + name + '.json'), { type: 'minecraft:crafting_shapeless', category: 'misc',
    ingredients: ['minecraft:paper', 'minecraft:slime_ball', 'minecraft:' + dye], result: { count: 4, id: 'bleep:' + name } });
}
// a power bank: copper on top, iron round two blocks of redstone
shaped('power_bank', [' C ', 'IRI', 'IRI'], { C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot', R: 'minecraft:redstone_block' });
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

// ---------- language ----------
json(join(ASSETS, 'lang/en_us.json'), {
  'itemGroup.bleep': 'Bleep',
  'entity.bleep.bleep': 'Bleep',
  'item.bleep.bleep_core': 'Bleep Core',
  'item.bleep.bleep_display': 'Bleep Display',
  'item.bleep.bleep_tracks': 'Bleep Tracks',
  'item.bleep.bleep_arms': 'Bleep Arms',
  'tooltip.bleep.bleep_core': 'The heart of a bleep, made of bedrock.\nUse it on the ground with a display,\ntracks and arms in your inventory.\nFound in dungeons and abandoned mineshafts,\nor crafted, at a price.',
  'tooltip.bleep.bleep_display': 'A bleep\'s face.\nOne of the three parts a core needs.',
  'tooltip.bleep.bleep_tracks': 'What a bleep drives on.\nOne of the three parts a core needs.',
  'tooltip.bleep.bleep_arms': 'Big enough to hold things.\nOne of the three parts a core needs.',
  'tooltip.bleep.homebase': 'Place it near your bleep: it moves in\nand charges here.',
  'message.bleep.missing_parts': 'Still missing: %s',
  // the Bleepedia
  'screen.bleep.bleepedia': 'Bleepedia', 'screen.bleep.bleepedia_of': 'Bleepedia of %s', 'screen.bleep.new': 'NEW',
  'screen.bleep.nothing_yet': 'Nothing here yet.', 'screen.bleep.day': 'Day %s',
  'screen.bleep.tab.mobs': 'Mobs', 'screen.bleep.tab.biomes': 'Biomes', 'screen.bleep.tab.finds': 'Finds', 'screen.bleep.tab.about': 'About me', 'screen.bleep.tab.diary': 'Diary',
  'screen.bleep.about.day': 'Day of its life', 'screen.bleep.about.together': 'Days together', 'screen.bleep.about.distance': 'Driven',
  'screen.bleep.about.mobs': 'Mobs met', 'screen.bleep.about.biomes': 'Biomes seen', 'screen.bleep.about.finds': 'Finds',
  'screen.bleep.about.fights': 'Fights won', 'screen.bleep.about.empty': 'Ran empty', 'screen.bleep.about.bond': 'Bond',
  'biome.bleep.unknown': 'Somewhere new. I am filing this under adventure.',
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
  'thought.bleep.work.harvest': 'Harvest time! I love a tidy field.', 'thought.bleep.work.tidy': 'All tidied up. Everything in its place.', 'thought.bleep.work.alarm': 'ALARM! Monsters at my post! ALARM!',
  'message.bleep.alarm': '%s: ALARM! A %s at my post!',
  'diary.bleep.hide_and_seek': 'We played hide and seek. I was brilliant at it. Obviously.',
  'diary.bleep.farmer': 'First harvest. I am a farmer now. Moo. No, wait.', 'diary.bleep.tidy': 'Tidied up for the first time. It felt good. Do not tell anyone.',
  'diary.bleep.guard': 'Kept watch at night and sounded the alarm. I was very brave.',
  'activity.bleep.working': 'Working', 'activity.bleep.hide_and_seek': 'Playing hide and seek', 'activity.bleep.trick': 'Doing a trick',
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
  'item.bleep.party_hat': 'Party Hat', 'item.bleep.top_hat': 'Top Hat', 'item.bleep.antenna': 'Antenna with a Bow',
  'item.bleep.sticker_star': 'Star Sticker', 'item.bleep.sticker_heart': 'Heart Sticker', 'item.bleep.sticker_bolt': 'Lightning Sticker',
  'tooltip.bleep.party_hat': 'Right-click your bleep: it wears it.\nShears take it off again.',
  'tooltip.bleep.top_hat': 'Right-click your bleep: it wears it.\nShears take it off again.',
  'tooltip.bleep.antenna': 'Right-click your bleep: it wears it.\nShears take it off again.',
  'tooltip.bleep.sticker_star': 'Right-click your bleep: it sticks on its front.\nShears take it off again.',
  'tooltip.bleep.sticker_heart': 'Right-click your bleep: it sticks on its front.\nShears take it off again.',
  'tooltip.bleep.sticker_bolt': 'Right-click your bleep: it sticks on its front.\nShears take it off again.',
  'tooltip.bleep.power_bank': 'One full charge for a bleep.\nRight-click your bleep to top it up;\nright-click a homebase to fill it again.',
  'jei.bleep.ball': 'A ball to play with. Right-click to throw it: it bounces, and your bleep (any bleep, from the first day) drives after it and brings it back. Playing ball raises the bond a little, five times a day. A bleep it hits catches it.\n\nA ball lying by its homebase, or in an item frame there, is a toy: a comfort that makes it charge faster.',
  'jei.bleep.power_bank': 'One full charge for a bleep, carried along: its bar shows what is left. Right-click your bleep with it to top it up, even when it is switched off. Right-click a homebase with it to fill it again.\n\nRedstone charges a bleep too: 8% a dust, 72% a block. And lightning fills it up at once, and makes it hyper for half a minute.',
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
  'jei.bleep.bleep_core': 'The heart of a bleep, made of bedrock. Found in dungeon chests (one in four) and abandoned mineshaft chests (about one in seven), or crafted from thirteen diamonds and four obsidian.\n\nRight-click the ground with it, a Bleep Display, Bleep Tracks and Bleep Arms in your inventory: a bleep is put together there, part by part, and it is yours.',
  'jei.bleep.bleep_display': 'A bleep\'s face. One of the three parts a Bleep Core needs.',
  'jei.bleep.bleep_tracks': 'What a bleep drives on. One of the three parts a Bleep Core needs.',
  'jei.bleep.bleep_arms': 'Big enough to hold things, like its favourite item when it brings it to you. One of the three parts a Bleep Core needs.',
  'jei.bleep.homebase': 'Place it near your bleep and it moves in. An empty bleep goes home to charge here, and Go Home on its info screen sends it here. The light on the post burns while it charges.',
  ...subtitleLines,
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
