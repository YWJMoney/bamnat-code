// 1.36 [SEC-10] A small font that holds together the way the server reads one (table directory, head magic, glyph count, name
// table), with the words of its name table to choose. Not drawable: only the server's reading is under test.
const nameTable = (records) => {
  const strings = records.map(([, v]) => {
    const b = Buffer.alloc(v.length * 2);
    for (let i = 0; i < v.length; i++) b.writeUInt16BE(v.charCodeAt(i), i * 2);
    return b;
  });
  const head = Buffer.alloc(6 + records.length * 12);
  head.writeUInt16BE(0, 0);
  head.writeUInt16BE(records.length, 2);
  head.writeUInt16BE(head.length, 4);
  let at = 0;
  records.forEach(([id], i) => {
    const r = 6 + i * 12;
    head.writeUInt16BE(3, r);
    head.writeUInt16BE(1, r + 2);
    head.writeUInt16BE(0x0409, r + 4);
    head.writeUInt16BE(id, r + 6);
    head.writeUInt16BE(strings[i].length, r + 8);
    head.writeUInt16BE(at, r + 10);
    at += strings[i].length;
  });
  return Buffer.concat([head, ...strings]);
};

export const OFL = "This Font Software is licensed under the SIL Open Font License, Version 1.1.";

export function fakeFont({ family = "나눔손글씨 붓", maker = "", copyright = "", license = OFL, url = "https://openfontlicense.org", vendor = "TEST", pad = 0, fill = 7, drop = [] } = {}) {
  const name = nameTable([[0, copyright], [1, family], [8, maker], [13, license], [14, url]].filter(([, v]) => v));
  const head = Buffer.alloc(54);
  head.writeUInt32BE(0x00010000, 0);
  head.writeUInt32BE(0x5f0f3cf5, 12);
  head.writeUInt16BE(1000, 18);
  const maxp = Buffer.alloc(6);
  maxp.writeUInt32BE(0x00005000, 0);
  maxp.writeUInt16BE(2, 4);
  const os2 = Buffer.alloc(78);
  os2.write(vendor.padEnd(4).slice(0, 4), 58, "latin1");
  const all = { "OS/2": os2, cmap: Buffer.alloc(4), glyf: Buffer.alloc(16 + pad, fill), head, hhea: Buffer.alloc(36), hmtx: Buffer.alloc(8), loca: Buffer.alloc(6), maxp, name };
  const tags = Object.keys(all).filter((t) => !drop.includes(t));
  const dir = Buffer.alloc(12 + tags.length * 16);
  dir.writeUInt32BE(0x00010000, 0);
  dir.writeUInt16BE(tags.length, 4);
  const bodies = [];
  let off = dir.length;
  tags.forEach((t, i) => {
    const body = all[t];
    const r = 12 + i * 16;
    dir.write(t, r, "latin1");
    dir.writeUInt32BE(off, r + 8);
    dir.writeUInt32BE(body.length, r + 12);
    const padded = Buffer.concat([body, Buffer.alloc((4 - (body.length % 4)) % 4)]);
    bodies.push(padded);
    off += padded.length;
  });
  return Buffer.concat([dir, ...bodies]);
}
