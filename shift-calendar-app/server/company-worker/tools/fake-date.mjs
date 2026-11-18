// [RVD-02] 시험을 '다른 날'에 돌려 보는 시계 옮기기. `node --import ./tools/fake-date.mjs --test …`
// BAMNAT_FAKE_NOW=2026-12-01T00:10:00Z(그 시각부터 흐름) 또는 BAMNAT_FAKE_DAYS=30(오늘 + 30일).
// 시험 안의 t.mock.timers(고정 시계)는 그대로 그 시험의 시계를 덮어써요. 시험 DB(test/d1.mjs)의 SQL 'now'도 이 시계를 따라요.
const at = process.env.BAMNAT_FAKE_NOW ? Date.parse(process.env.BAMNAT_FAKE_NOW) : NaN;
const days = Number(process.env.BAMNAT_FAKE_DAYS || 0);
const RealDate = globalThis.Date;
const shift = Number.isFinite(at) ? at - RealDate.now() : days * 86400e3;
if (shift) {
  class ShiftedDate extends RealDate {
    constructor(...args) {
      if (args.length === 0) super(RealDate.now() + shift);
      else super(...args);
    }
    static now() {
      return RealDate.now() + shift;
    }
  }
  globalThis.Date = ShiftedDate;
}
