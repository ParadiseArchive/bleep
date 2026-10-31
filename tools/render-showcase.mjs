// Renders pictures for the website, straight from the real models and textures:
//  - isometric pixel-art renders of the bleep (from the boxes in client/BleepModel.java) and its blocks
//  - every face on its display, on its black screen
//  - item icons for every recipe ingredient and result, and for the items the docs show,
//    taken from the vanilla Minecraft client jar (flat items as their texture, blocks rendered like in the inventory)
//  - recipes.js: every Bleep recipe as data, so the docs page draws its crafting grids from it
// Made from Setpoint's tools/render-showcase.mjs (the jar reader, PNG and renderer are the same).
// Run from the project root after a build (the jar comes from the Gradle cache):
//   node tools/render-showcase.mjs <output folder>
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { deflateSync, inflateRawSync, inflateSync } from 'node:zlib';

const ASSETS = 'src/main/resources/assets/bleep';
const OUT = process.argv[2] || 'build/showcase';
mkdirSync(OUT, { recursive: true });

// ---------- the vanilla client jar, read in place (a jar is a zip) ----------
function openJar(path) {
  const buf = readFileSync(path), entries = new Map();
  let end = buf.length - 22;
  while (buf.readUInt32LE(end) !== 0x06054b50) end--;
  let o = buf.readUInt32LE(end + 16);
  for (let i = 0, n = buf.readUInt16LE(end + 10); i < n; i++) {
    const nameLen = buf.readUInt16LE(o + 28), extraLen = buf.readUInt16LE(o + 30), commentLen = buf.readUInt16LE(o + 32);
    entries.set(buf.toString('utf8', o + 46, o + 46 + nameLen), {
      method: buf.readUInt16LE(o + 10), size: buf.readUInt32LE(o + 20), local: buf.readUInt32LE(o + 42),
    });
    o += 46 + nameLen + extraLen + commentLen;
  }
  return (name) => {
    const e = entries.get(name);
    if (!e) return null;
    const start = e.local + 30 + buf.readUInt16LE(e.local + 26) + buf.readUInt16LE(e.local + 28);
    const data = buf.subarray(start, start + e.size);
    return e.method === 8 ? inflateRawSync(data) : Buffer.from(data);
  };
}
const CACHE = join(homedir(), '.gradle/caches/neoformruntime/artifacts');
const JAR = existsSync(CACHE) && readdirSync(CACHE).filter((n) => /^minecraft_.*_client.jar$/.test(n)).sort().pop();
if (!JAR) throw new Error('Minecraft client jar not found in ' + CACHE + ': run ./gradlew build once');
const vanilla = openJar(join(CACHE, JAR));
const LANG = { ...JSON.parse(vanilla('assets/minecraft/lang/en_us.json').toString('utf8')),
  ...JSON.parse(readFileSync(join(ASSETS, 'lang/en_us.json'), 'utf8')) };

/** Bytes of an asset, from the mod or from the vanilla jar. */
function asset(ns, path) {
  if (ns === 'bleep') return readFileSync(join(ASSETS, path));
  const data = vanilla('assets/' + ns + '/' + path);
  if (!data) throw new Error('missing vanilla asset ' + ns + ':' + path);
  return data;
}
const split = (id) => (id.includes(':') ? id.split(':') : ['minecraft', id]);

// ---------- PNG read (any non-interlaced PNG: grey, RGB, palette, with alpha, 1 to 16 bit) / write (RGBA 8 bit) ----------
function readPng(buf) {
  let o = 8, w = 0, h = 0, depth = 8, type = 6, palette = null, trns = null;
  const idat = [];
  while (o < buf.length) {
    const len = buf.readUInt32BE(o), kind = buf.toString('ascii', o + 4, o + 8), data = buf.subarray(o + 8, o + 8 + len);
    if (kind === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); depth = data[8]; type = data[9]; }
    if (kind === 'PLTE') palette = data;
    if (kind === 'tRNS') trns = data;
    if (kind === 'IDAT') idat.push(data);
    o += 12 + len;
  }
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[type], bits = channels * depth;
  const bpp = Math.max(1, bits >> 3), stride = Math.ceil(w * bits / 8), raw = inflateSync(Buffer.concat(idat));
  const px = Buffer.alloc(w * h * 4), line = Buffer.alloc(stride), prev = Buffer.alloc(stride);
  const max = (1 << Math.min(depth, 8)) - 1;
  const sample = (x, c) => { // channel c of pixel x, scaled to 0..255 (palette indices left as they are)
    if (depth === 16) return line[(x * channels + c) * 2];
    if (depth === 8) return line[x * channels + c];
    const bit = (x * channels + c) * depth, v = (line[bit >> 3] >> (8 - depth - (bit & 7))) & max;
    return type === 3 ? v : Math.round(v * 255 / max);
  };
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let i = 0; i < stride; i++) {
      const x = raw[y * (stride + 1) + 1 + i], a = i >= bpp ? line[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
      const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
      line[i] = (x + [0, a, b, (a + b) >> 1, pa <= pb && pa <= pc ? a : pb <= pc ? b : c][f]) & 255;
    }
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      if (type === 3) {
        const k = sample(x, 0);
        palette.copy(px, i, k * 3, k * 3 + 3);
        px[i + 3] = trns && k < trns.length ? trns[k] : 255;
      } else if (type === 0 || type === 4) {
        px[i] = px[i + 1] = px[i + 2] = sample(x, 0);
        // grey with tRNS: one grey value is transparent
        px[i + 3] = type === 4 ? sample(x, 1) : trns && sample(x, 0) === Math.round(trns.readUInt16BE(0) * 255 / ((1 << depth) - 1)) ? 0 : 255;
      } else {
        for (let k = 0; k < 3; k++) px[i + k] = sample(x, k);
        // RGB with tRNS: one colour is transparent
        const key = trns && type === 2 && [0, 1, 2].every((k) => sample(x, k) === (depth === 16 ? trns[k * 2] : trns.readUInt16BE(k * 2)));
        px[i + 3] = type === 6 ? sample(x, 3) : key ? 0 : 255;
      }
    }
    line.copy(prev);
  }
  return { w, h, px };
}
const CRC = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = (buf) => { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]), crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function writePng(path, w, h, px) {
  const raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) px.copy(raw, y * (1 + w * 4) + 1, y * w * 4, (y + 1) * w * 4);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  writeFileSync(path, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]));
}

// ---------- models ----------
const textures = new Map();
const texture = (id) => {
  const [ns, path] = split(id);
  if (!textures.has(ns + ':' + path)) textures.set(ns + ':' + path, readPng(asset(ns, 'textures/' + path + '.png')));
  return textures.get(ns + ':' + path);
};
/** A model with its parents merged: textures, elements, and whether it is a flat (generated) item. */
function loadModel(id) {
  const [ns, path] = split(id);
  if (path === 'builtin/generated') return { textures: {}, elements: null, generated: true };
  const m = JSON.parse(asset(ns, 'models/' + path + '.json').toString('utf8'));
  const parent = m.parent ? loadModel(m.parent) : { textures: {}, elements: null, generated: false };
  const own = Object.fromEntries(Object.entries(m.textures || {}).map(([k, v]) => [k, typeof v === 'object' ? v.sprite : v]));
  return { textures: { ...parent.textures, ...own }, elements: m.elements || parent.elements, generated: parent.generated };
}
/** Tinted faces (the liquid in a tank's sight glass) are drawn in water blue, like in a plain world. */
const TINT = [0x3f, 0x76, 0xe4];

/** Models made here rather than read from a file (the bleep itself), by name. */
const MODELS = new Map();
/** Elements of a block model with every face's texture resolved. Short names mean a Bleep block model. */
function model(name) {
  if (MODELS.has(name)) return MODELS.get(name);
  const m = loadModel(name.includes('/') ? name : 'bleep:block/' + name);
  const resolve = (ref) => { while (ref && ref.startsWith('#')) ref = m.textures[ref.slice(1)]; return ref; };
  return (m.elements || []).map((e) => ({ ...e, faces: Object.fromEntries(Object.entries(e.faces).map(([f, v]) => [f, { ...v, texture: resolve(v.texture) }])) }));
}

// face corners: origin at (u1, v1), then the u edge and the v edge, following Minecraft's uv layout
function faceQuad(face, [x1, y1, z1], [x2, y2, z2]) {
  switch (face) {
    case 'north': return { o: [x2, y2, z1], u: [x1, y2, z1], v: [x2, y1, z1], uv: [16 - x2, 16 - y2, 16 - x1, 16 - y1] };
    case 'south': return { o: [x1, y2, z2], u: [x2, y2, z2], v: [x1, y1, z2], uv: [x1, 16 - y2, x2, 16 - y1] };
    case 'east': return { o: [x2, y2, z2], u: [x2, y2, z1], v: [x2, y1, z2], uv: [16 - z2, 16 - y2, 16 - z1, 16 - y1] };
    case 'west': return { o: [x1, y2, z1], u: [x1, y2, z2], v: [x1, y1, z1], uv: [z1, 16 - y2, z2, 16 - y1] };
    case 'up': return { o: [x1, y2, z1], u: [x2, y2, z1], v: [x1, y2, z2], uv: [x1, z1, x2, z2] };
    case 'down': return { o: [x1, y1, z2], u: [x2, y1, z2], v: [x1, y1, z1], uv: [x1, 16 - z2, x2, 16 - z1] };
  }
}

// ---------- isometric rasteriser ----------
const VSCALE = 1.125; // vertical squash, 2:1 pixel-art isometric
const project = ([x, y, z]) => [x - z, (x + z) / 2 - y * VSCALE];
const VIEW = [1, 1 / VSCALE, 1];
const depth = ([x, y, z]) => x * VIEW[0] + y * VIEW[1] + z * VIEW[2];
const sub = (a, b) => a.map((v, i) => v - b[i]);
const rotY = ([x, y, z], deg) => { // blockstate y rotation, clockwise seen from above
  for (let r = 0; r < ((deg / 90) % 4 + 4) % 4; r++) [x, z] = [16 - z, x];
  return [x, y, z];
};
const SHADE = { up: 1, down: 0.5, north: 0.8, south: 0.8, east: 0.6, west: 0.6 };

/** parts: [{ model, at: [bx, by, bz] (blocks), y: rotation, xf: (pixel point) => moved point, after the rotation }] */
function render(file, parts, scale = 4, pad = 2, outline = true) {
  const quads = [];
  for (const { model: name, at = [0, 0, 0], y = 0, tints = [TINT, [255, 255, 255]], xf = (p) => p } of parts) {
    for (const e of model(name)) {
      for (const [face, f] of Object.entries(e.faces)) {
        const q = faceQuad(face, e.from, e.to);
        const turn = (p) => { // an element turned about its origin, about x, then y, then z
          if (!e.rotation) return p;
          const [ox, oy, oz] = e.rotation.origin;
          let [x, y, z] = [p[0] - ox, p[1] - oy, p[2] - oz];
          if (e.rotation.x) { const a = e.rotation.x * Math.PI / 180; [y, z] = [Math.cos(a) * y - Math.sin(a) * z, Math.sin(a) * y + Math.cos(a) * z]; }
          if (e.rotation.y) { const a = e.rotation.y * Math.PI / 180; [x, z] = [Math.cos(a) * x + Math.sin(a) * z, -Math.sin(a) * x + Math.cos(a) * z]; }
          if (e.rotation.z) { const a = e.rotation.z * Math.PI / 180; [x, y] = [Math.cos(a) * x - Math.sin(a) * y, Math.sin(a) * x + Math.cos(a) * y]; }
          return [x + ox, y + oy, z + oz];
        };
        const place = (p) => xf(rotY(turn(p), y)).map((v, i) => v + at[i] * 16);
        const o = place(q.o), u = place(q.u), v = place(q.v);
        const n = [ // normal from the edges, to cull back faces
          (u[1] - o[1]) * (v[2] - o[2]) - (u[2] - o[2]) * (v[1] - o[1]),
          (u[2] - o[2]) * (v[0] - o[0]) - (u[0] - o[0]) * (v[2] - o[2]),
          (u[0] - o[0]) * (v[1] - o[1]) - (u[1] - o[1]) * (v[0] - o[0]),
        ];
        // faces wind so that the normal points inwards; flip it
        if (-(n[0] * VIEW[0] + n[1] * VIEW[1] + n[2] * VIEW[2]) <= 1e-9) continue;
        const axis = Math.abs(n[1]) > Math.abs(n[0]) && Math.abs(n[1]) > Math.abs(n[2]) ? (n[1] < 0 ? 'up' : 'down')
          : Math.abs(n[0]) > Math.abs(n[2]) ? (n[0] < 0 ? 'east' : 'west') : (n[2] < 0 ? 'south' : 'north');
        quads.push({ o, u, v, uv: f.uv || q.uv, tex: texture(f.texture), shade: f.lit ? 1 : SHADE[axis], tint: f.tintindex === undefined ? null : tints[f.tintindex] || TINT });
      }
    }
  }
  // bounds
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const q of quads) for (const p of [q.o, q.u, q.v, q.u.map((c, i) => c + q.v[i] - q.o[i])]) {
    const [sx, sy] = project(p);
    minX = Math.min(minX, sx); maxX = Math.max(maxX, sx); minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
  }
  const W = Math.ceil(maxX - minX) + pad * 2, H = Math.ceil(maxY - minY) + pad * 2;
  const px = Buffer.alloc(W * H * 4), zbuf = new Float64Array(W * H).fill(-Infinity);
  for (const q of quads) {
    const s0 = project(q.o), su = sub(project(q.u), s0), sv = sub(project(q.v), s0);
    const det = su[0] * sv[1] - su[1] * sv[0];
    if (Math.abs(det) < 1e-9) continue;
    const xs = [0, su[0], sv[0], su[0] + sv[0]].map((v) => v + s0[0] - minX + pad), ys = [0, su[1], sv[1], su[1] + sv[1]].map((v) => v + s0[1] - minY + pad);
    const [u1, v1, u2, v2] = q.uv, du = sub(q.u, q.o), dv = sub(q.v, q.o);
    for (let py = Math.floor(Math.min(...ys)); py <= Math.ceil(Math.max(...ys)); py++) {
      for (let pxx = Math.floor(Math.min(...xs)); pxx <= Math.ceil(Math.max(...xs)); pxx++) {
        if (pxx < 0 || py < 0 || pxx >= W || py >= H) continue;
        const dx = pxx + 0.5 - (s0[0] - minX + pad), dy = py + 0.5 - (s0[1] - minY + pad);
        const a = (dx * sv[1] - dy * sv[0]) / det, b = (su[0] * dy - su[1] * dx) / det;
        if (a < 0 || a >= 1 || b < 0 || b >= 1) continue;
        const tex = (val, lo, hi) => Math.min(16 - 1e-6, Math.max(0, hi >= lo ? val : val - 1e-6));
        const tx = tex(u1 + a * (u2 - u1), u1, u2), ty = tex(v1 + b * (v2 - v1), v1, v2);
        const t = q.tex, fh = t.h > t.w ? t.w : t.h, ti = (Math.floor(ty * fh / 16) * t.w + Math.floor(tx * t.w / 16)) * 4; // animated textures (a tall strip): first frame
        if (t.px[ti + 3] < 128) continue;
        const d = depth(q.o.map((c, i) => c + a * du[i] + b * dv[i])), i = py * W + pxx;
        if (d < zbuf[i] - 1e-6) continue;
        zbuf[i] = d;
        for (let k = 0; k < 3; k++) px[i * 4 + k] = Math.round(t.px[ti + k] * q.shade * (q.tint ? q.tint[k] / 255 : 1));
        px[i * 4 + 3] = 255;
      }
    }
  }
  save(file, W, H, px, scale, outline);
}

/** Integer upscale, with a 1 px dark outline around the silhouette unless asked not to. */
function save(file, W, H, px, scale, outline = true) {
  const out = Buffer.alloc(W * H * 4);
  px.copy(out);
  for (let y = 0; outline && y < H; y++) for (let x = 0; x < W; x++) {
    const i = (y * W + x) * 4;
    if (px[i + 3]) continue;
    const near = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([ox, oy]) => {
      const nx = x + ox, ny = y + oy;
      return nx >= 0 && ny >= 0 && nx < W && ny < H && px[(ny * W + nx) * 4 + 3];
    });
    if (near) { out[i] = 5; out[i + 1] = 6; out[i + 2] = 10; out[i + 3] = 255; }
  }
  const big = Buffer.alloc(W * scale * H * scale * 4);
  for (let y = 0; y < H * scale; y++) for (let x = 0; x < W * scale; x++) {
    out.copy(big, (y * W * scale + x) * 4, (((y / scale) | 0) * W + ((x / scale) | 0)) * 4, (((y / scale) | 0) * W + ((x / scale) | 0)) * 4 + 4);
  }
  mkdirSync(join(OUT, file, '..'), { recursive: true });
  writePng(join(OUT, file + '.png'), W * scale, H * scale, big);
  console.log('wrote', file, W * scale + 'x' + H * scale);
}

// ---------- the bleep, from the boxes in client/BleepModel.java ----------
// Keep this in step with BleepModel.createBodyLayer. An entity model has y going down and its front at -z; drawn in
// the world it stands with y up and its front to the south: x stays, y and z turn round (X = x + 8, Y = -y, Z = 8 - z).
// Boxes: [texture u, v, x, y, z, w, h, d] in the part's own pixels; a part has its pivot (relative to its parent) and
// its turn in radians, as the model's setupAnim leaves it.
const ARMS_FORWARD = -0.9;
function bleepParts(pose = {}) {
  const arm = pose.arms ?? ARMS_FORWARD;
  const wheels = [0, 1, 2].flatMap((i) => [
    { pivot: [-7.25, -2, -3.5 + 3.5 * i], boxes: [[56, 41, -0.5, -1.5, -1.5, 1, 3, 3]] },
    { pivot: [7.25, -2, -3.5 + 3.5 * i], boxes: [[56, 41, -0.5, -1.5, -1.5, 1, 3, 3]] },
  ]);
  const body = [0, -5, 0];
  return [
    { pivot: [0, 0, 0], boxes: [[0, 30, -4, -5, -4, 8, 2, 8], [0, 41, -7, -4, -5.5, 3, 4, 11], [28, 41, 4, -4, -5.5, 3, 4, 11]] },
    ...wheels,
    { name: 'body', pivot: body, step: 2, boxes: [[0, 16, -4, -6, -3.5, 8, 6, 7], [32, 16, -3, -5.5, -4.5, 6, 5, 1], [32, 16, -3, -5.5, 3.5, 6, 5, 1], [32, 30, -2, -8, -2, 4, 2, 4]] },
    { name: 'head', pivot: [0, -13, 0], step: 4, turn: pose.head, boxes: [[0, 0, -6, -7, -4, 12, 7, 8], [56, 48, -7, -5, -1.5, 1, 3, 3], [56, 48, 6, -5, -1.5, 1, 3, 3]], face: pose.face ?? 'happy' },
    { name: 'right_arm', pivot: [-4, -10, 0], step: 3, turn: { x: arm, z: pose.armSpread ? pose.armSpread : 0 }, boxes: [[40, 0, -2, 0, -1, 2, 6, 2], [48, 0, -2.5, 6, -1.5, 3, 2, 3]] },
    { name: 'left_arm', pivot: [4, -10, 0], step: 3, turn: { x: arm, z: pose.armSpread ? -pose.armSpread : 0 }, boxes: [[40, 0, 0, 0, -1, 2, 6, 2], [48, 0, -0.5, 6, -1.5, 3, 2, 3]] },
  ].filter((part) => (part.step || 1) <= (pose.built ?? 4)) // being put together: tracks, body, arms, head
    .map((part) => pose.ride === 'sled' ? { ...part, pivot: [part.pivot[0], part.pivot[1] - 2, part.pivot[2]] } : part); // on its sled: 2 pixels up
}
/** What it rides in (client/BoatModel and SledModel): boxes at the ground, in their own texture of tw x th. */
const RIDES = {
  boat: { texture: 'bleep:entity/bleep/boat', tw: 128, th: 64, boxes: [[0, 0, -8, -1, -8, 16, 1, 17], [0, 20, -9, -6, -8, 1, 6, 17], [40, 20, 8, -6, -8, 1, 6, 17], [0, 44, -8, -6, 9, 16, 6, 1], [36, 44, -7, -6, -10, 14, 6, 2], [72, 44, -5, -6, -12, 10, 6, 2], [100, 44, -2, -6, -13, 4, 6, 1]] },
  sled: { texture: 'bleep:entity/bleep/sled', tw: 64, th: 32, boxes: [[0, 0, -7.5, -2, -7, 15, 1, 14], [0, 16, -7, -1, -8, 1, 1, 16], [0, 16, 6, -1, -8, 1, 1, 16], [0, 16, -7, -3, -10, 1, 2, 2], [0, 16, 6, -3, -10, 1, 2, 2], [0, 16, -7, -4, -11, 14, 1, 1]] },
};
/**
 * What it wears, from client/AccessoryModel.java: pieces on its head, body or an arm, each with boxes [texture u, v, x, y,
 * z, w, h, d, grow] in that part's pixels (grow: a CubeDeformation), and an offset and turn of its own (the cape).
 */
const head = (boxes) => [{ on: 'head', boxes }], onBody = (boxes) => [{ on: 'body', boxes }];
const ACCESSORIES = {
  party_hat: head([[0, 0, -3, -8, -3, 6, 1, 6], [24, 0, -2, -10, -2, 4, 2, 4], [40, 0, -1, -12, -1, 2, 2, 2]]),
  top_hat: head([[0, 8, -4, -8, -4, 8, 1, 8], [32, 8, -3, -13, -3, 6, 5, 6]]),
  antenna: head([[0, 20, -0.5, -13, -0.5, 1, 6, 1], [4, 20, -1, -15, -1, 2, 2, 2], [12, 20, -2.5, -11, -0.5, 2, 2, 1], [12, 20, 0.5, -11, -0.5, 2, 2, 1]]),
  crown: head([[24, 20, -3, -8, -3, 6, 1, 6], [48, 20, -3, -10, -3, 1, 2, 1], [48, 20, 2, -10, -3, 1, 2, 1], [48, 20, -3, -10, 2, 1, 2, 1], [48, 20, 2, -10, 2, 1, 2, 1], [52, 20, -0.5, -9.5, -3.2, 1, 1, 1]]),
  sunglasses: head([[0, 36, -6, -6, -5, 12, 1, 1], [26, 36, -4, -5, -5, 3, 2, 1], [26, 36, 1, -5, -5, 3, 2, 1], [34, 36, -7, -6, -4, 1, 1, 3], [34, 36, 6, -6, -4, 1, 1, 3]]),
  sticker_star: onBody([[0, 30, 0, -4.5, -5.5, 3, 3, 1]]), sticker_heart: onBody([[8, 30, 0, -4.5, -5.5, 3, 3, 1]]), sticker_bolt: onBody([[16, 30, 0, -4.5, -5.5, 3, 3, 1]]),
  leather_jacket: [
    { on: 'body', boxes: [[0, 40, -4, -6, -3.5, 8, 6, 7, 0.35], [30, 40, -3, -5.5, -4.5, 6, 5, 1, 0.35], [30, 40, -3, -5.5, 3.5, 6, 5, 1, 0.35], [44, 40, -2, -7, -2, 4, 1, 4, 0.4]] },
    { on: 'right_arm', boxes: [[30, 47, -2, 0, -1, 2, 4, 2, 0.3]] }, { on: 'left_arm', boxes: [[30, 47, 0, 0, -1, 2, 4, 2, 0.3]] },
  ],
  cape: [{ on: 'body', offset: [0, -6, 4.9], turn: { x: 0.35 }, boxes: [[0, 54, -4, 0, 0, 8, 9, 1]] }],
  bow_tie: onBody([[20, 54, -3, -6.5, -5.5, 2, 2, 1], [20, 54, 1, -6.5, -5.5, 2, 2, 1], [28, 54, -1, -6, -5.7, 2, 1, 1]]),
  scarf: onBody([[34, 54, -4, -6.5, -3.5, 8, 1, 7, 0.5], [20, 59, -2.5, -5.5, -5.6, 1, 4, 1]]),
};
/** The bleep as block-model elements, so the isometric renderer can draw it. pose.color tints its shell (tintindex 0). */
function bleepElements(pose = {}) {
  const deg = (r) => (r || 0) * 180 / Math.PI;
  const elements = [];
  const parts = bleepParts(pose);
  // what it wears hangs on its head or its body, in its own texture and not dyed
  for (const name of [pose.hat, pose.sticker, pose.eyes, pose.body, pose.back, pose.neck].filter(Boolean)) {
    for (const piece of ACCESSORIES[name]) {
      const on = parts.find((p) => p.name === piece.on);
      if (!on) continue;
      const pivot = piece.offset ? on.pivot.map((c, i) => c + piece.offset[i]) : on.pivot;
      parts.push({ pivot, turn: piece.turn || on.turn, boxes: piece.boxes, texture: 'bleep:entity/bleep/accessories' });
    }
  }
  if (pose.ride) parts.push({ pivot: [0, 0, 0], ...RIDES[pose.ride] });
  for (const part of parts) {
    const T = part.texture || 'bleep:entity/bleep/bleep', tint = part.texture || pose.color === undefined ? undefined : 0;
    const [px, py, pz] = part.pivot;
    const rotation = part.turn ? { origin: [px + 8, -py, 8 - pz], x: deg(part.turn.x), y: -deg(part.turn.y), z: -deg(part.turn.z) } : null;
    for (const [u, v, x, y, z, w, h, d, g = 0] of part.boxes) {
      const x0 = px + x - g, y0 = py + y - g, z0 = pz + z - g; // grown on every side, the texture stays the same
      const ku = (part.tw || 64) / 16, kv = (part.th || 64) / 16, r = (a, b, c, e) => [a / ku, b / kv, c / ku, e / kv]; // the texture in a 16 wide uv space
      elements.push({
        from: [x0 + 8, -(y0 + h + 2 * g), 8 - (z0 + d + 2 * g)], to: [x0 + w + 2 * g + 8, -y0, 8 - z0], rotation,
        faces: {
          south: { uv: r(u + d, v + d, u + d + w, v + d + h), texture: T, tintindex: tint },          // its front
          north: { uv: r(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), texture: T, tintindex: tint },
          west: { uv: r(u, v + d, u + d, v + d + h), texture: T, tintindex: tint },
          east: { uv: r(u + d + w, v + d, u + 2 * d + w, v + d + h), texture: T, tintindex: tint },
          up: { uv: r(u + d, v, u + d + w, v + d), texture: T, tintindex: tint },
          down: { uv: r(u + d + w, v, u + d + 2 * w, v + d), texture: T, tintindex: tint },
        },
      });
    }
    if (part.face) { // the face on the display: a lit layer just in front of the head (128 px texture, display at 18..38, 18..28)
      elements.push({
        from: [px - 5 + 8, -(py - 1), 8 - (pz - 4) + 0.02], to: [px + 5 + 8, -(py - 6), 8 - (pz - 4) + 0.04], rotation,
        faces: { south: { uv: [18 / 8, 18 / 8, 38 / 8, 28 / 8], texture: 'bleep:entity/bleep/face/' + part.face, lit: true } },
      });
    }
  }
  return elements;
}
MODELS.set('bleep', bleepElements());
// the chest: its item is drawn by a special renderer, so it is built here from ChestModel's boxes. Unlike a mob's,
// that model stands upright as it is (y up, its lock at +z, in block pixels): [u, v, x, y, z, w, h, d]. A box's faces
// sit in its texture as always (the -y face at u + d, the +y face next to it), and its sides run upward with v.
function chestElements() {
  const T = 'minecraft:entity/chest/normal', r = (a, b, c, e) => [a / 4, e / 4, c / 4, b / 4]; // v flipped: sides run up
  return [[0, 19, 1, 0, 1, 14, 10, 14], [0, 0, 1, 9, 1, 14, 5, 14], [0, 0, 7, 7, 15, 2, 4, 1]].map(([u, v, x, y, z, w, h, d]) => ({
    from: [x, y, z], to: [x + w, y + h, z + d],
    faces: {
      north: { uv: r(u + d, v + d, u + d + w, v + d + h), texture: T }, south: { uv: r(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), texture: T },
      west: { uv: r(u, v + d, u + d, v + d + h), texture: T }, east: { uv: r(u + d + w, v + d, u + 2 * d + w, v + d + h), texture: T },
      down: { uv: [(u + d) / 4, v / 4, (u + d + w) / 4, (v + d) / 4], texture: T }, up: { uv: [(u + d + w) / 4, v / 4, (u + d + 2 * w) / 4, (v + d) / 4], texture: T },
    },
  }));
}
MODELS.set('chest', chestElements());

// ---------- the pictures ----------
const S = 180; // fronts toward the camera
// the bleep's textures are finer than a block's (4 and 8 pixels to a model pixel): draw it four times bigger, so every
// texture pixel gets a screen pixel, like a block's 16 x 16 do
const BIG = 4, big = (lift = 0) => ([x, y, z]) => [x * BIG, (y + lift) * BIG, z * BIG];
render('bleep', [{ model: 'bleep', xf: big() }], 2);
MODELS.set('bleep_excited', bleepElements({ arms: -0.2, armSpread: 2.5, face: 'excited' }));
render('bleep_excited', [{ model: 'bleep_excited', xf: big() }], 2);
MODELS.set('bleep_asleep', bleepElements({ arms: 0, head: { x: 0.45, z: 0.1 }, face: 'asleep' }));
render('bleep_asleep', [{ model: 'bleep_asleep', xf: big() }], 2);
render('homebase', [{ model: 'homebase_0', y: S }], 6); // empty: no bars lit on its post
render('homebase_full', [{ model: 'homebase_4', y: S }], 6);
render('starstone', [{ model: 'starstone', y: S }], 6);
render('fallen_star', [{ model: 'fallen_star', y: S }], 6);
render('homebase_charging', [{ model: 'homebase_4_charging', y: S }], 6);
render('display_case', [{ model: 'display_case', y: S }], 6);
// docked on its homebase: standing on the pad, facing out, charging
render('bleep_home', [{ model: 'homebase_4_charging', y: S, xf: big() }, { model: 'bleep', xf: big(2) }], 2);
// the core is a flat item: just scale it up
{
  const t = texture('bleep:item/bleep_core');
  const W = t.w + 2, H = t.h + 2, px = Buffer.alloc(W * H * 4);
  for (let y = 0; y < t.h; y++) t.px.copy(px, ((y + 1) * W + 1) * 4, y * t.w * 4, (y + 1) * t.w * 4);
  save('bleep_core', W, H, px, 8);
}
// dyed, and dressed up: Minecraft's dye colours (DyeColor.getTextureDiffuseColor)
const DYES = { red: [0xb0, 0x2e, 0x26], blue: [0x3c, 0x44, 0xaa], lime: [0x80, 0xc7, 0x1f], yellow: [0xfe, 0xd8, 0x3d], magenta: [0xc7, 0x4e, 0xbd], orange: [0xf9, 0x80, 0x1d], cyan: [0x16, 0x9c, 0x9c], pink: [0xf3, 0x8b, 0xaa] };
const DRESSED = [
  ['bleep_red', { color: 'red', hat: 'party_hat', face: 'excited', arms: -0.2, armSpread: 2.5 }],
  ['bleep_blue', { color: 'blue', hat: 'top_hat' }],
  ['bleep_lime', { color: 'lime', hat: 'antenna', sticker: 'sticker_heart' }],
  ['bleep_yellow', { color: 'yellow', sticker: 'sticker_star', face: 'stubborn' }],
  ['bleep_magenta', { color: 'magenta', hat: 'party_hat', sticker: 'sticker_bolt' }],
  ['bleep_cyan', { color: 'cyan', hat: 'antenna' }],
  ['bleep_cool', { color: 'red', eyes: 'sunglasses', body: 'leather_jacket' }],
  ['bleep_hero', { color: 'blue', hat: 'crown', back: 'cape', face: 'excited', arms: -0.2, armSpread: 2.5 }],
  ['bleep_dapper', { color: 'magenta', hat: 'top_hat', neck: 'bow_tie' }],
  ['bleep_cosy', { color: 'lime', neck: 'scarf', sticker: 'sticker_heart' }],
];
for (const [name, pose] of DRESSED) {
  MODELS.set(name, bleepElements(pose));
  render(name, [{ model: name, xf: big(), tints: [DYES[pose.color]] }], 2);
}
// the magnet button on its info screen
for (const g of ['magnet', 'sound_on', 'sound_off']) { const t = texture('bleep:gui/' + g); save('gui/' + g, t.w, t.h, t.px, 1, false); }
// out in the world: an abandoned one (switched off, paint faded in the sun) and one dancing to a record
MODELS.set('bleep_abandoned', bleepElements({ color: 'faded', arms: 0.15, head: { x: -0.35, z: -0.15 }, face: 'off' }));
render('bleep_abandoned', [{ model: 'bleep_abandoned', xf: big(), tints: [[0x8a, 0x6a, 0x4a]] }], 2);
MODELS.set('bleep_dancing', bleepElements({ color: 'orange', arms: -0.2, armSpread: 2.1, head: { x: 0, z: 0.25 }, face: 'excited', hat: 'party_hat' }));
render('bleep_dancing', [{ model: 'bleep_dancing', xf: big(), tints: [DYES.orange] }], 2);
// being put together: its tracks and body are on, the arms and head still to come
MODELS.set('bleep_assembling', bleepElements({ built: 2 }));
render('bleep_assembling', [{ model: 'bleep_assembling', xf: big() }], 2);
// on the water in its boat, and on snow on its sled, arms up
MODELS.set('bleep_boat', bleepElements({ ride: 'boat', face: 'happy' }));
render('bleep_boat', [{ model: 'bleep_boat', xf: big() }], 2);
MODELS.set('bleep_sled', bleepElements({ ride: 'sled', color: 'cyan', neck: 'scarf', face: 'excited', arms: -2.6, armSpread: 0.25 }));
render('bleep_sled', [{ model: 'bleep_sled', xf: big(), tints: [DYES.cyan] }], 2);
// the other parts are flat items too
for (const part of ['star_shard', 'speed_chip', 'shield_chip', 'battery_chip', 'ball', 'bleep_display', 'bleep_tracks', 'bleep_arms', 'power_bank', 'party_hat', 'top_hat', 'antenna', 'sticker_star', 'sticker_heart', 'sticker_bolt', 'crown', 'sunglasses', 'leather_jacket', 'cape', 'bow_tie', 'scarf', 'whistle', 'tracker']) {
  const t = texture('bleep:item/' + part);
  const W = t.w + 2, H = t.h + 2, px = Buffer.alloc(W * H * 4);
  for (let y = 0; y < t.h; y++) t.px.copy(px, ((y + 1) * W + 1) * 4, y * t.w * 4, (y + 1) * t.w * 4);
  save(part, W, H, px, 8);
}
// every face on the display, on its black screen
for (const file of readdirSync(join(ASSETS, 'textures/entity/bleep/face')).sort()) {
  const t = texture('bleep:entity/bleep/face/' + file.replace('.png', ''));
  const px = Buffer.alloc(20 * 10 * 4);
  for (let y = 0; y < 10; y++) for (let x = 0; x < 20; x++) {
    const from = ((18 + y) * t.w + 18 + x) * 4, to = (y * 20 + x) * 4;
    const lit = t.px[from + 3] > 0;
    px[to] = lit ? t.px[from] : 8; px[to + 1] = lit ? t.px[from + 1] : 9; px[to + 2] = lit ? t.px[from + 2] : 12; px[to + 3] = 255;
  }
  save('faces/' + file.replace('.png', ''), 20, 10, px, 8, false);
}

{
  const FONT = { B: '##.#.###.#.###.', O: '####.##.##.####', L: '#..#..#..#..###', T: '###.#..#..#..#.' };
  const GLOW = [0x8f, 0xf7, 0xff], DIM = [0x2f, 0x8f, 0x96], AMBER = [0xff, 0xb5, 0x47], BROWN = [0xb9, 0x7a, 0x3e], CHIP = [0x5a, 0x3a, 0x1e];
  const PINK = [0xff, 0x4f, 0x8b], WHITE = [0xee, 0xf1, 0xf4];
  const screen = (file, draw) => {
    const px = Buffer.alloc(20 * 10 * 4);
    for (let i = 0; i < 200; i++) { px[i * 4] = 8; px[i * 4 + 1] = 9; px[i * 4 + 2] = 12; px[i * 4 + 3] = 255; }
    draw((x, y, c) => { if (x >= 0 && x < 20 && y >= 0 && y < 10) { const i = (y * 20 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; } });
    save('faces/' + file, 20, 10, px, 8, false);
  };
  screen('trick_name', (set) => { // its name, halfway across
    [...'BOLT'].forEach((ch, i) => [...FONT[ch]].forEach((p, g) => { if (p === '#') set(3 + i * 4 + g % 3, 2 + Math.floor(g / 3), GLOW); }));
    for (let x = 0; x < 20; x++) set(x, 8, DIM);
  });
  screen('trick_game', (set) => { // tic-tac-toe, X to win
    for (let i = 0; i < 9; i++) { set(7, i, DIM); set(11, i, DIM); }
    for (let i = 0; i < 11; i++) { set(4 + i, 2, DIM); set(4 + i, 6, DIM); }
    const X = (cx, cy) => [[0, 1], [2, 1], [1, 2], [0, 3], [2, 3]].forEach(([dx, dy]) => set(cx + dx, cy + dy, GLOW));
    const O = (cx, cy) => [[1, 1], [0, 2], [2, 2], [1, 3]].forEach(([dx, dy]) => set(cx + dx, cy + dy, AMBER));
    X(4, -1); X(8, 3); O(12, -1); O(4, 3); X(12, 7);
    set(18, 4, GLOW);
  });
  screen('trick_home', (set) => { // an arrow, home ahead
    for (let y = 0; y < 10; y++) for (let x = 0; x < 20; x++) {
      const u = x + 0.5 - 9.5, v = y + 0.5 - 4.5;
      if ((Math.abs(u) < 0.8 && v > -1 && v < 3.6) || (v <= -0.5 && v > -4 && Math.abs(u) < (v + 4.2) * 0.9)) set(x, y, GLOW);
    }
    set(1, 8, DIM); set(18, 8, DIM);
  });
  screen('trick_want', (set) => { // a cookie, please?
    ['.###.', '#####', '##o##', '#o###', '####o', '.###.'].forEach((row, y) => [...row].forEach((c, x) => { if (c !== '.') set(5 + x, 2 + y, c === 'o' ? CHIP : BROWN); }));
    [...'###..#.##....#.'].forEach((p, g) => { if (p === '#') set(13 + g % 3, 2 + Math.floor(g / 3), GLOW); });
  });
  screen('trick_talk', (set) => { // chatting with another bleep (client/DisplayTextures.talk)
    for (let x = 4; x <= 15; x++) { set(x, 1, GLOW); set(x, 6, GLOW); }
    for (let y = 2; y <= 5; y++) { set(3, y, GLOW); set(16, y, GLOW); }
    set(6, 7, GLOW); set(5, 8, GLOW);
    for (let i = 0; i < 3; i++) set(7 + i * 3, 4, AMBER);
  });
  screen('trick_hi', (set) => { // saying hi (client/DisplayTextures.hi)
    const I = '###.#..#..#.###';
    [...'#.##.#####.##.#'].forEach((p, g) => { if (p === '#') set(3 + g % 3, 2 + Math.floor(g / 3), GLOW); });
    [...I].forEach((p, g) => { if (p === '#') set(7 + g % 3, 2 + Math.floor(g / 3), GLOW); });
    ['.#.#.', '#####', '#####', '.###.', '..#..'].forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') set(12 + x, 2 + y, PINK); }));
  });
  screen('trick_birthday', (set) => { // HAPPY BIRTHDAY, halfway across (client/DisplayTextures: ticker)
    const F = { H: '#.##.#####.##.#', A: '####.#####.##.#', P: '####.#####..#..', Y: '#.##.#.#..#..#.' };
    [...'HAPPY'].forEach((ch, i) => [...F[ch]].forEach((p, g) => { if (p === '#') set(1 + i * 4 + g % 3, 2 + Math.floor(g / 3), GLOW); }));
    for (let x = 0; x < 20; x++) set(x, 8, DIM);
  });
  screen('trick_stars', (set) => { // full moon, midnight: a little constellation (client/DisplayTextures.stars)
    const STARS = [[2, 2], [6, 1], [10, 3], [14, 2], [17, 5], [4, 6], [9, 7], [13, 6]];
    STARS.forEach(([x, y], i) => { const bright = i % 4 === 0; set(x, y, bright ? [255, 255, 255] : GLOW); if (bright) { set(x - 1, y, DIM); set(x + 1, y, DIM); set(x, y - 1, DIM); set(x, y + 1, DIM); } });
    for (let x = 3; x < 6; x++) set(x, 4, DIM);
  });
  const RED = [0xff, 0x3b, 0x4e];
  screen('trick_care', (set) => { // its owner hurt or hungry: a red heart (client/DisplayTextures.care)
    ['.##.##.', '#######', '#######', '.#####.', '..###..', '...#...'].forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') set(7 + x, 2 + y, RED); }));
  });
  screen('trick_alert', (set) => { // look out! (client/DisplayTextures.alert)
    for (let y = 1; y <= 6; y++) { set(9, y, RED); set(10, y, RED); }
    set(9, 8, RED); set(10, 8, RED);
    for (let y = 2; y <= 7; y++) { set(3, y, DIM); set(16, y, DIM); }
  });
  const DROP = [0x5a, 0xb4, 0xff], WHITE2 = [255, 255, 255];
  screen('trick_yum', (set) => { // its owner ate something (client/DisplayTextures.yum)
    for (const x of [4, 9]) { set(x, 3, GLOW); set(x + 1, 2, GLOW); set(x + 2, 3, GLOW); }
    ['.#.#.', '#####', '.###.', '..#..'].forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') set(13 + x, 4 + y, PINK); }));
  });
  screen('trick_question', (set) => { // a question for you (client/DisplayTextures.question): a big ?, a Y and an N
    const glyph = '###..#.##....#.', Y = '#.##.#.#..#..#.', N = '##.#.##.##.##.#';
    [...glyph].forEach((p, g) => { if (p === '#') { const x = 7 + (g % 3) * 2, y = Math.floor(g / 3) * 2; set(x, y, AMBER); set(x + 1, y, AMBER); set(x, y + 1, AMBER); set(x + 1, y + 1, AMBER); } });
    for (let g = 0; g < 15; g++) { if (Y[g] === '#') set(1 + g % 3, 3 + Math.floor(g / 3), GLOW); if (N[g] === '#') set(16 + g % 3, 3 + Math.floor(g / 3), GLOW); }
  });
  screen('trick_curious', (set) => { // its owner opened a chest
    set(4, 3, GLOW); set(5, 3, GLOW); set(8, 4, GLOW); set(9, 4, GLOW);
    [...'###..#.##....#.'].forEach((p, g) => { if (p === '#') { const x = 12 + (g % 3) * 2, y = Math.floor(g / 3) * 2; set(x, y, AMBER); set(x + 1, y, AMBER); set(x, y + 1, AMBER); set(x + 1, y + 1, AMBER); } });
  });
  screen('trick_sweat', (set) => { // its owner fell
    set(4, 3, GLOW); set(5, 4, GLOW); set(4, 5, GLOW); set(10, 3, GLOW); set(9, 4, GLOW); set(10, 5, GLOW);
    set(15, 1, DROP); set(14, 2, DROP); set(15, 2, DROP); set(16, 2, DROP); set(15, 3, DROP); set(12, 5, DROP); set(12, 6, DROP);
  });
  screen('trick_tear', (set) => { // its owner killed an animal
    for (let x = 5; x <= 7; x++) set(x, 3, DIM);
    for (let x = 12; x <= 14; x++) set(x, 3, DIM);
    set(6, 4, GLOW); set(13, 4, GLOW); set(6, 5, DROP); set(6, 6, DROP); set(5, 6, DROP); set(7, 6, DROP);
    for (let x = 8; x <= 11; x++) set(x, 8, DIM);
  });
  screen('trick_sparkle', (set) => { // a level up, an advancement
    [[4, 3], [10, 5], [15, 2]].forEach(([x, y], i) => { set(x, y, i % 2 === 0 ? WHITE2 : AMBER); set(x - 1, y, AMBER); set(x + 1, y, AMBER); set(x, y - 1, AMBER); set(x, y + 1, AMBER); });
  });
  screen('trick_ore', (set) => { // ore close by (client/DisplayTextures.ore)
    ['..###..', '.#####.', '#######', '.#####.', '..###..', '...#...'].forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') set(7 + x, 2 + y, (x + y) % 3 === 0 ? WHITE2 : GLOW); }));
    set(15, 1, WHITE2); set(16, 2, AMBER); set(4, 6, AMBER);
  });
  screen('trick_treasure', (set) => { // treasure hunt: warmer, colder (client/DisplayTextures.warmth), three bars lit
    const WARM = [[0x5a, 0xb4, 0xff], [0x8f, 0xf7, 0xff], [0xff, 0xb5, 0x47], [0xff, 0x8a, 0x3d], [0xff, 0x4f, 0x4f]];
    for (let i = 0; i < 5; i++) for (let y = 6 - i; y <= 8; y++) for (let dx = 0; dx < 3; dx++) { if (i < 3) set(1 + i * 4 + dx, y, WARM[i]); else if (y === 8) set(1 + i * 4 + dx, y, DIM); }
  });
  screen('trick_dream', (set) => { // a dream of green hills under the moon (client/DisplayTextures.scene)
    const GREEN = [0x5b, 0xd1, 0x6a], SNOW = [0xee, 0xf1, 0xf4];
    for (let x = 0; x < 20; x++) { const top = 7 - Math.round(2 * Math.sin((x + 2) / 3)); for (let y = top; y < 10; y++) set(x, y, y === top ? GREEN : DIM); }
    set(15, 1, SNOW); set(16, 1, SNOW); set(15, 2, SNOW);
  });
  const CLOUD = [0xd6, 0xdc, 0xe2], STORM = [0x7d, 0x87, 0x94], BOLT = [0xff, 0xe1, 0x4a];
  const weather = (thunder) => (set) => { // the forecast (client/DisplayTextures.weather), frame 0
    ['...###....', '.#######..', '##########', '.########.'].forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') set(5 + x, 1 + y, thunder ? STORM : CLOUD); }));
    if (thunder) [[10, 5], [9, 6], [10, 6], [11, 6], [10, 7], [9, 8]].forEach(([x, y]) => set(x, y, BOLT));
    for (let i = 0; i < 4; i++) { const x = 6 + i * 2 + (i % 2), y = 5 + i % 4; if (!thunder || x < 8 || x > 11) set(x, y, DROP); }
  };
  screen('trick_rain', weather(false));
  screen('trick_thunder', weather(true));
  screen('trick_imitate', (set) => { // imitating a mob: guess who (client/DisplayTextures.imitate), frame 0
    [2, 4, 6, 3, 5, 2].forEach((hh, i) => { const h = Math.max(1, (hh + 8) % 7); for (let y = 0; y < h; y++) set(2 + i * 2, 5 + Math.floor(h / 2) - y, GLOW); });
    [...'###..#.##....#.'].forEach((p, g) => { if (p === '#') set(15 + g % 3, 2 + Math.floor(g / 3), AMBER); });
  });
  screen('trick_ball', (set) => { // shall we play? (client/DisplayTextures.ball)
    ['.pp.', 'pwpp', 'gggg', '.gg.'].forEach((row, y) => [...row].forEach((c, x) => { if (c !== '.') set(6 + x, 2 + y, c === 'p' ? PINK : c === 'g' ? GLOW : WHITE); }));
    for (let x = 6; x < 10; x++) set(x, 8, DIM);
    [...'###..#.##....#.'].forEach((p, g) => { if (p === '#') set(13 + g % 3, 2 + Math.floor(g / 3), AMBER); });
  });
}

// ---------- item icons and recipes for the docs ----------
// Items the docs page shows without a recipe: the treats, the favourite items, the name tag. Add any new ones here.
const SCREEN_ITEMS = ['minecraft:shears', 'minecraft:red_dye', 'bleep:bleep_core', 'bleep:bleep_display', 'bleep:bleep_tracks', 'bleep:bleep_arms', 'bleep:power_bank', 'minecraft:redstone_block', 'minecraft:torch', 'minecraft:cookie', 'minecraft:redstone', 'minecraft:name_tag', 'minecraft:amethyst_shard',
  'minecraft:lightning_rod', 'minecraft:compass', 'minecraft:music_disc_cat', 'minecraft:glow_berries', 'minecraft:clock',
  'minecraft:spyglass', 'minecraft:firework_rocket', 'minecraft:bell', 'minecraft:honeycomb', 'minecraft:book', 'minecraft:chest', 'minecraft:iron_sword', 'minecraft:iron_hoe', 'minecraft:wheat', 'minecraft:cake', 'minecraft:fishing_rod', 'minecraft:furnace', 'minecraft:poppy', 'minecraft:iron_axe', 'minecraft:compass', 'minecraft:map', 'minecraft:filled_map', 'minecraft:snow_block', 'minecraft:carved_pumpkin', 'minecraft:hopper', 'minecraft:comparator'];

/** Writes items/<namespace>/<name>.png: a flat item at 16x16, a block item rendered like in the inventory. */
function icon(id) {
  const [ns, name] = split(id);
  if (id === 'minecraft:shield') { // a special item model too: the front of the shield, cut from its texture (12 x 22 at 1, 1), fitted in 16 x 16
    const t = texture('minecraft:entity/shield/shield_base_nopattern'), k = t.w / 64, px = Buffer.alloc(16 * 16 * 4);
    for (let y = 0; y < 16; y++) for (let x = 3; x < 12; x++) {
      const u = 1 + Math.floor((x - 3) * 12 / 9), v = 1 + Math.floor(y * 22 / 16), from = ((v * k | 0) * t.w + (u * k | 0)) * 4;
      t.px.copy(px, (y * 16 + x) * 4, from, from + 4);
    }
    save('items/minecraft/shield', 16, 16, px, 1, false);
    return { name: LANG['item.minecraft.shield'], icon: 'items/minecraft/shield.png' };
  }
  if (id === 'minecraft:chest') { // a special item model: the chest built above
    render('items/minecraft/chest', [{ model: 'chest', y: 0 }], 1, 0, false);
    return { name: LANG['block.minecraft.chest'], icon: 'items/minecraft/chest.png' };
  }
  const definition = JSON.parse(asset(ns, 'items/' + name + '.json').toString('utf8'));
  // a plain model, or the first one inside a select or range (the clock, the compass): the picture you mostly see
  const first = (m) => (m.type === 'minecraft:model' ? m.model : first(m.cases?.[0]?.model ?? m.entries?.[0]?.model ?? m.fallback ?? m.on_false ?? m.models?.[0]));
  const modelId = first(definition.model);
  const m = loadModel(modelId);
  const file = 'items/' + ns + '/' + name;
  if (m.generated) {
    // every layer over the one before, the first tinted with its default dye colour if it has one (an undyed coverall is blue)
    const px = Buffer.alloc(16 * 16 * 4), tints = definition.model.tints || [];
    for (let layer = 0; m.textures['layer' + layer]; layer++) {
      const t = texture(m.textures['layer' + layer]), dye = tints[layer]?.type === 'minecraft:dye' ? tints[layer].default : null;
      const tint = dye === null ? [255, 255, 255] : [(dye >> 16) & 255, (dye >> 8) & 255, dye & 255];
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        const from = ((y * t.w / 16 | 0) * t.w + (x * t.w / 16 | 0)) * 4, to = (y * 16 + x) * 4;
        if (t.px[from + 3] === 0) continue;
        for (let c = 0; c < 3; c++) px[to + c] = t.px[from + c] * tint[c] / 255;
        px[to + 3] = t.px[from + 3];
      }
    }
    save(file, 16, 16, px, 1, false);
  } else {
    render(file, [{ model: modelId, y: 180 }], 1, 0, false); // front to the left, like the inventory
  }
  const key = ['item', 'block'].map((k) => k + '.' + ns + '.' + name).find((k) => LANG[k]);
  return { name: key ? LANG[key] : name, icon: file + '.png' };
}

// the lines a bleep thinks, straight from its language file: about each mob (with the mob's own name), its favourite
// items with their hints, and its other thoughts by kind
const mobs = Object.keys(LANG).filter((k) => k.startsWith('mob.bleep.')).sort().map((k) => {
  const id = k.slice('mob.bleep.'.length);
  return { id, name: LANG['entity.minecraft.' + id] || id, line: LANG[k] };
});
const favourites = Object.keys(LANG).filter((k) => k.startsWith('obsession.bleep.')).map((k) => {
  const id = k.slice('obsession.bleep.'.length), icon = { music_disc: 'music_disc_cat' }[id] || id;
  return { id, name: LANG[k], hint: LANG['thought.bleep.obsession.' + id], icon: 'items/minecraft/' + icon + '.png' };
});
const thoughts = {};
for (const k of Object.keys(LANG).filter((k) => /^thought\.bleep\.[a-z_]+\.\d+$/.test(k))) (thoughts[k.split('.')[2]] ||= []).push(LANG[k]);

// for the redrawn Bleepedia: the biomes and special blocks with its remarks, its diary lines, and a few mob faces
// (the front of the head, cut from the mob's own texture)
const biomes = Object.keys(LANG).filter((k) => /^biome\.bleep\.[a-z_]+$/.test(k) && k !== 'biome.bleep.unknown').sort()
  .map((k) => { const id = k.slice('biome.bleep.'.length); return { id, name: LANG['biome.minecraft.' + id] || id, line: LANG[k] }; });
const FIND_ICONS = { lava: 'lava_bucket', nether_portal: 'obsidian' };
const finds = Object.keys(LANG).filter((k) => k.startsWith('find.bleep.'))
  .map((k) => { const id = k.slice('find.bleep.'.length); return { id, name: LANG['block.minecraft.' + id] || id, line: LANG[k], item: 'minecraft:' + (FIND_ICONS[id] || id) }; });
const diary = {};
for (const k of Object.keys(LANG).filter((k) => k.startsWith('diary.bleep.'))) diary[k.slice('diary.bleep.'.length)] = LANG[k];
const MOB_FACES = [['zombie', 'zombie/zombie', 8, 8, 8, 8], ['creeper', 'creeper/creeper', 8, 8, 8, 8], ['skeleton', 'skeleton/skeleton', 8, 8, 8, 8],
  ['spider', 'spider/spider', 40, 12, 8, 8], ['villager', 'villager/villager', 8, 8, 8, 10], ['enderman', 'enderman/enderman', 8, 8, 8, 8], ['wolf', 'wolf/wolf', 4, 4, 6, 6]];
for (const [id, path, u, v, w, h] of MOB_FACES) {
  const t = texture('minecraft:entity/' + path), k = t.w / 64, px = Buffer.alloc(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) t.px.copy(px, (y * w + x) * 4, (((v + y) * k | 0) * t.w + ((u + x) * k | 0)) * 4, (((v + y) * k | 0) * t.w + ((u + x) * k | 0)) * 4 + 4);
  save('mobfaces/' + id, w, h, px, 1, false);
}

// the milestones, in the order of entity/Milestone.java, with their names and what earns them
const milestones = [...readFileSync('src/main/java/io/hookdev/bleep/entity/Milestone.java', 'utf8').matchAll(/^\s+([A-Z0-9_]+)\("([a-z_:]+)"/gm)].map(([, name, icon]) => {
  const id = name.toLowerCase();
  return { id, icon, name: LANG['milestone.bleep.' + id], desc: LANG['milestone.bleep.' + id + '.desc'] };
});

const items = {}, recipes = {}, shapeless = [], cooking = [];
const need = (id) => { if (!items[id]) items[id] = icon(id); };
const RECIPES = 'src/main/resources/data/bleep/recipe';
for (const fileName of readdirSync(RECIPES).sort()) {
  const r = JSON.parse(readFileSync(join(RECIPES, fileName), 'utf8'));
  if (r.type === 'minecraft:smelting' || r.type === 'minecraft:blasting') {
    need(r.ingredient); need(r.result.id);
    cooking.push({ type: r.type.slice(10), input: r.ingredient, result: r.result.id, xp: r.experience, seconds: r.cookingtime / 20 });
    continue;
  }
  if (r.type === 'minecraft:crafting_shapeless') {
    r.ingredients.forEach(need); need(r.result.id);
    shapeless.push({ inputs: r.ingredients, result: r.result.id, count: r.result.count || 1 });
    continue;
  }
  if (r.type !== 'minecraft:crafting_shaped') continue;
  const TAG_SHOWN = { "#minecraft:planks": "minecraft:oak_planks" }; // a tag shows one of its items
  const ingredient = (v) => { const id = Array.isArray(v) ? v[0] : v; return TAG_SHOWN[id] || id; };
  const rows = r.pattern.length === 1 ? ['   ', r.pattern[0], '   '] : r.pattern; // a one-row recipe sits in the middle
  const grid = [];
  for (let y = 0; y < 3; y++) for (let x = 0; x < 3; x++) {
    const c = (rows[y] || '')[x];
    const id = c && c !== ' ' ? ingredient(r.key[c]) : null;
    if (id) need(id);
    grid.push(id);
  }
  need(r.result.id);
  const recipe = { grid, count: r.result.count || 1 };
  recipes[r.result.id] ??= recipe; // the first recipe for an item (by file name), and every recipe under its own name too
  recipes['bleep:' + fileName.replace('.json', '')] = recipe;
}
SCREEN_ITEMS.forEach(need);
milestones.forEach((m) => need(m.icon));
for (const f of finds) { // a few blocks have a special item model (drawn by its own renderer): those show their name only
  try { need(f.item); } catch { f.item = null; }
}
writeFileSync(join(OUT, 'recipes.js'), '// Generated by bleep/tools/render-showcase.mjs: do not edit by hand.\n'
  + 'window.BLEEP_ITEMS = ' + JSON.stringify(items, null, 1) + ';\n'
  + 'window.BLEEP_RECIPES = ' + JSON.stringify(recipes, null, 1) + ';\n'
  + 'window.BLEEP_SHAPELESS = ' + JSON.stringify(shapeless, null, 1) + ';\n'
  + 'window.BLEEP_COOKING = ' + JSON.stringify(cooking, null, 1) + ';\n'
  + 'window.BLEEP_MOBS = ' + JSON.stringify(mobs, null, 1) + ';\n'
  + 'window.BLEEP_FAVOURITES = ' + JSON.stringify(favourites, null, 1) + ';\n'
  + 'window.BLEEP_THOUGHTS = ' + JSON.stringify(thoughts, null, 1) + ';\n'
  + 'window.BLEEP_BIOMES = ' + JSON.stringify(biomes, null, 1) + ';\n'
  + 'window.BLEEP_FINDS = ' + JSON.stringify(finds, null, 1) + ';\n'
  + 'window.BLEEP_DIARY = ' + JSON.stringify(diary, null, 1) + ';\n'
  + 'window.BLEEP_MILESTONES = ' + JSON.stringify(milestones, null, 1) + ';\n');
console.log('wrote recipes.js:', Object.keys(recipes).length, 'shaped,', shapeless.length, 'shapeless,', cooking.length, 'cooking recipes,', Object.keys(items).length, 'items');
