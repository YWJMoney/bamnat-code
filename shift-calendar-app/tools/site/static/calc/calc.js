// 밤낮 공개 계산기 공통: 요율 자료(remote/v1/rates.json·taxtable.json), 계산 공식, 칸 다루기, 결과 링크, 앱 받기 링크.
// 공식은 밤낮 앱과 같은 식이에요(실수령·3.3%·일용 세금·퇴직금·연차). 같은 사례로 웹과 앱을 함께 맞춰 봐요.
// 입력한 값은 이 화면에서만 계산하고 어디에도 보내지 않아요.
(function (G) {
  'use strict';
  var hasDoc = typeof document !== 'undefined';
  if (hasDoc) document.documentElement.className += ' js';
  var base = hasDoc && document.currentScript ? document.currentScript.src.replace(/calc\/calc\.js.*$/, '') : '';
  // 자료를 못 받을 때 쓰는 값: 사이트를 만들 때 rates.json을 그대로 채워 넣어요(손으로 적지 않아요).
  var FALLBACK = /*@rates*/null/*@*/;
  var B = G.Bamnat = { base: base, data: { rates: [], pensionBases: [], taxTables: [] }, f: {} };
  if (FALLBACK) { B.data.rates = FALLBACK.rates || []; B.data.pensionBases = FALLBACK.pensionBases || []; }

  // ---------- 날짜 (달 끝·윤년에 안전하게, 모두 그날 0시 UTC로) ----------
  var pad = function (n) { return (n < 10 ? '0' : '') + n; };
  var D = B.d = {
    parse: function (s) {
      var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(s || ''));
      if (!m) return null;
      var x = new Date(Date.UTC(+m[1], +m[2] - 1, +m[3]));
      return x.getUTCMonth() === +m[2] - 1 && x.getUTCFullYear() === +m[1] ? x : null;
    },
    iso: function (x) { return x.getUTCFullYear() + '-' + pad(x.getUTCMonth() + 1) + '-' + pad(x.getUTCDate()); },
    today: function () { var n = new Date(); return new Date(Date.UTC(n.getFullYear(), n.getMonth(), n.getDate())); },
    plusDays: function (x, n) { return new Date(x.getTime() + n * 864e5); },
    // 1월 31일 + 1개월 = 2월 28일(윤년 29일), 2월 29일 + 1년 = 2월 28일.
    plusMonths: function (x, n) {
      var m = x.getUTCMonth() + n, y = x.getUTCFullYear() + Math.floor(m / 12), mm = ((m % 12) + 12) % 12;
      var last = new Date(Date.UTC(y, mm + 1, 0)).getUTCDate();
      return new Date(Date.UTC(y, mm, Math.min(x.getUTCDate(), last)));
    },
    plusYears: function (x, n) { return D.plusMonths(x, 12 * n); },
    between: function (a, b) { return Math.round((b - a) / 864e5); },
    // 민법 제160조: 기간은 시작한 날과 같은 날짜의 전날 끝나고, 그 달에 같은 날짜가 없으면 그 달 마지막 날 끝나요.
    periodEnd: function (start, months) {
      var t = D.plusMonths(start, months);
      return t.getUTCDate() !== start.getUTCDate() ? t : D.plusDays(t, -1);
    },
    dot: function (x) { return x.getUTCFullYear() + '. ' + (x.getUTCMonth() + 1) + '. ' + x.getUTCDate() + '.'; },
    md: function (x) { return (x.getUTCMonth() + 1) + '/' + x.getUTCDate(); }
  };

  // ---------- 숫자 ----------
  // 칸 글 → 숫자. 전각 숫자·쉼표·'원'·띄어쓰기는 걸러 읽고, 음수·지수처럼 숫자가 아닌 글은 NaN.
  B.num = function (v) {
    var s = String(v == null ? '' : v).replace(/[０-９]/g, function (c) { return String.fromCharCode(c.charCodeAt(0) - 0xFEE0); })
      .replace(/．/g, '.').replace(/[,，\s원]/g, '');
    if (s === '') return 0;
    return /^(\d+(\.\d*)?|\.\d+)$/.test(s) ? parseFloat(s) : NaN;
  };
  B.won = function (n) { return Math.round(n).toLocaleString('ko-KR') + '원'; };
  B.minus = function (n) { return n > 0 ? '-' + B.won(n) : '0원'; };
  // digits: 소수 몇 자리까지(기본 2). 요율 3.595%처럼 셋째 자리가 있는 값을 3.6%로 줄이지 않게 그 자리에서 반올림해요(2.0.0 [I50b-01]).
  B.fmt = function (n, digits) { var d = digits == null ? 2 : digits, p = Math.pow(10, d); return (Math.round(n * p) / p).toLocaleString('ko-KR', { maximumFractionDigits: d }); };
  // 3000000 → "300만 원", 12345678 → "1,234만 5,678원", 150000000 → "1억 5,000만 원"
  B.read = function (n) {
    n = Math.floor(n);
    if (!(n > 0)) return '';
    var eok = Math.floor(n / 1e8), man = Math.floor((n % 1e8) / 1e4), rest = n % 1e4, out = [];
    if (eok) out.push(eok.toLocaleString('ko-KR') + '억');
    if (man) out.push(man.toLocaleString('ko-KR') + '만');
    if (rest) out.push(rest.toLocaleString('ko-KR'));
    return out.join(' ') + (rest ? '원' : ' 원');
  };
  // 받침을 보고 조사를 골라요: B.josa('4조 2교대', '는/은') → '4조 2교대는'
  B.josa = function (word, pair) {
    var two = pair.split('/'), c = String(word).replace(/[\s)\]]+$/, '').slice(-1), code = c.charCodeAt(0);
    var batchim = code >= 0xAC00 && code <= 0xD7A3 ? (code - 0xAC00) % 28 > 0 : /[0136781lmnr]$/i.test(c);
    var a = two[0], b = two[1];
    var hasB = ['은', '이', '을', '과', '아'].indexOf(a) >= 0;
    return word + ((batchim ? (hasB ? a : b) : (hasB ? b : a)));
  };

  // 원 × 요율을 원 미만·10원 미만 버림(공단 고지·간이세액표 방식). 쓴 소수 그대로 정수 분수로 곱해 26,999.999… 같은 오차가 없어요.
  var BI = typeof BigInt === 'function' ? BigInt : null;
  var pow10 = function (k) { var v = BI(1); while (k-- > 0) v = v * BI(10); return v; };
  var dec = function (r, sig) {
    var m = /^(\d+)(?:\.(\d+))?(?:e([+-]?\d+))?$/i.exec(sig ? Number(r).toPrecision(sig) : String(r));
    if (!m) return null;
    var frac = m[2] || '', exp = (m[3] ? +m[3] : 0) - frac.length, n = BI(m[1] + frac);
    return exp >= 0 ? [n * pow10(exp), BI(1)] : [n, pow10(-exp)];
  };
  var floorTen = function (n, d) { return n <= BI(0) ? 0 : Number(n / d / BI(10) * BI(10)); };
  B.cut10 = function (amount) {
    var rs = Array.prototype.slice.call(arguments, 1), v = Math.round(amount);
    if (BI) {
      var n = BI(v), d = BI(1);
      for (var i = 0; i < rs.length; i++) { var f = dec(rs[i], 12); if (!f) return 0; n = n * f[0]; d = d * f[1]; }
      return floorTen(n, d);
    }
    rs.forEach(function (r) { v *= r; });
    return Math.max(0, Math.floor(Math.floor(Math.round(v * 1e6) / 1e6) / 10) * 10);
  };
  // 원 × 위 ÷ 아래를 버림 (장기요양보험료 = 건강보험료 × 장기요양보험료율 ÷ 건강보험료율, 곱한 뒤 나눠요).
  B.cutRatio = function (amount, top, bottom) {
    if (BI) {
      var t = dec(top), b = dec(bottom);
      if (!t || !b) return 0;
      return floorTen(BI(Math.round(amount)) * t[0] * b[1], t[1] * b[0]);
    }
    return Math.max(0, Math.floor(Math.floor(Math.round(amount * top / bottom * 1e6) / 1e6) / 10) * 10);
  };
  var floor10 = function (n) { return Math.floor(Math.floor(n) / 10) * 10; };

  // ---------- 요율 ----------
  var byFrom = function (list) { return (list || []).slice().sort(function (a, b) { return a.from < b.from ? -1 : a.from > b.from ? 1 : 0; }); };
  var rowOn = function (day) { var rs = byFrom(B.data.rates), pick = null; rs.forEach(function (r) { if (r.from <= day) pick = r; }); return pick || rs[0] || {}; };
  var latest = function (day, key) { var v = null; byFrom(B.data.rates).forEach(function (r) { if (r.from <= day && r[key] != null) v = r[key]; }); return v; };
  // 국민연금법 제88조(2025-03-20 개정, 2026-01-01 시행): 2026년 9.5%에서 해마다 0.5%p씩 올라 2033년 13%. 자료에 그해 줄이 없을 때만 써요.
  B.LAW_PENSION = { 2028: 0.105, 2029: 0.11, 2030: 0.115, 2031: 0.12, 2032: 0.125, 2033: 0.13 };
  // 건강보험료 근로자 몫 월 상한: 보건복지부고시 제2025-222호(2026년 1월분부터) 보수월액보험료 상한 9,183,480원의 절반.
  B.HEALTH_CAP_2026 = 9183480 / 2;
  B.pensionRateOn = function (day) {
    var r = rowOn(day), y = +day.slice(0, 4);
    if (r.from && +r.from.slice(0, 4) >= y && r.pensionRate != null) return r.pensionRate;
    var law = null;
    Object.keys(B.LAW_PENSION).forEach(function (k) { if (+k <= y) law = B.LAW_PENSION[k]; });
    var known = latest(day, 'pensionRate');
    return law == null ? known : Math.max(law, known || 0);
  };
  // 그날의 요율 (화면에 보이는 값). 빈 칸(null)은 앞 해 값을 이어 써요.
  B.rateOn = function (day) {
    var pick = {};
    byFrom(B.data.rates).forEach(function (r) { if (r.from <= day) Object.keys(r).forEach(function (k) { if (r[k] != null) pick[k] = r[k]; }); });
    if (!pick.from) pick = Object.assign({}, rowOn(day));
    pick.pensionRate = B.pensionRateOn(day);
    return pick;
  };
  B.careRatioOn = function (day) {
    var pick = null;
    byFrom(B.data.rates).forEach(function (r) { if (r.from <= day && r.longTermCareRate != null && r.healthRate != null) pick = [r.longTermCareRate, r.healthRate]; });
    return pick || [0.1314, 1];
  };
  B.healthCapOn = function (day) { var v = latest(day, 'healthCapShare'); return v != null ? v : (+day.slice(0, 4) >= 2026 ? B.HEALTH_CAP_2026 : null); };
  B.healthFloorOn = function (day) { return latest(day, 'healthMinShare'); };
  B.pensionBaseOn = function (day) {
    var pick = null;
    byFrom(B.data.pensionBases).forEach(function (p) { if (p.from <= day) pick = p; });
    return pick || { min: 400000, max: 6370000 };
  };
  B.taxTableOn = function (day) {
    var ts = byFrom(B.data.taxTables), pick = null;
    ts.forEach(function (t) { if (t.from <= day) pick = t; });
    return pick || ts[ts.length - 1] || null;
  };
  // 칩의 해(올해·내년)를 계산할 날: 올해는 오늘, 내년은 1월 1일.
  B.dayOf = function (year) { var t = D.today(); return year === t.getUTCFullYear() ? D.iso(t) : year + '-01-01'; };
  B.rateFor = function (year) { return B.rateOn(B.dayOf(year)); };
  B.years = function () {
    var ys = {}, now = new Date().getFullYear();
    B.data.rates.forEach(function (r) { ys[r.from.slice(0, 4)] = true; });
    var out = Object.keys(ys).map(Number).filter(function (y) { return y >= now; }).sort();
    return out.length ? out : [now];
  };

  // ---------- 공식 (앱과 같은 식) ----------
  var F = B.f;
  // 3.3% 원천징수: 소득세 3%(10원 미만 버림) + 지방소득세 그 10%(10원 미만 버림).
  F.withholding = function (amount) {
    amount = Math.floor(amount);
    if (!(amount > 0)) return { income: 0, local: 0, total: 0 };
    var income = floor10(amount * 3 / 100), local = floor10(income / 10);
    return { income: income, local: local, total: income + local };
  };
  // 근로자 몫 4대보험. off: { pension, health, employment } 중 빼는 줄(주 15시간 미만 등).
  F.insurance = function (taxable, day, off) {
    off = off || {};
    var r = rowOn(day), pr = B.pensionRateOn(day), hr = r.healthRate != null ? r.healthRate : 0.0719, ur = r.unemploymentRate, pb = B.pensionBaseOn(day), notes = [];
    var pension = taxable <= 0 || off.pension ? 0 : B.cut10(Math.min(Math.max(Math.floor(taxable / 1000) * 1000, pb.min), pb.max), pr / 2);
    var floorShare = B.healthFloorOn(day);
    var hBase = floorShare == null && taxable >= 1 && taxable < 280000 ? 280000 : taxable;
    var raw = off.health ? 0 : B.cut10(hBase, hr / 2);
    var uncapped = floorShare != null && raw >= 1 && raw < floorShare ? floorShare : raw;
    if (uncapped > 0 && hBase !== taxable) notes.push('건강보험은 보수 하한 28만 원으로 계산했어요(국민건강보험법 시행령 제32조).');
    if (floorShare != null && uncapped !== raw) notes.push('건강보험은 월 하한 ' + B.won(floorShare) + '을 떼요.');
    var cap = B.healthCapOn(day), health = uncapped;
    if (cap != null && uncapped > cap) { health = cap; notes.push('건강보험은 월 상한 ' + B.won(cap) + '까지만 떼요.'); }
    var cr = B.careRatioOn(day), care = B.cutRatio(health, cr[0], cr[1]);
    var employment = off.employment ? 0 : B.cut10(taxable, ur / 2);
    return { pension: pension, health: health, care: care, employment: employment, notes: notes, pensionRate: pr, healthRate: hr, employmentRate: ur };
  };
  // 근로소득 간이세액표(소득세법 시행령 별표 2). 표가 없으면 null.
  F.incomeTax = function (pay, family, children, day) {
    var t = B.taxTableOn(day);
    if (!t) return null;
    var f = Math.max(1, family | 0);
    var col = function (r, n) { return n <= 11 ? r[n - 1] : Math.max(0, r[10] - (r[9] - r[10]) * (n - 11)); };
    var tax;
    if (pay < t.rows[0][0] * 1000) tax = 0;
    else if (pay < 10000000) {
      var row = null;
      for (var i = 0; i < t.rows.length; i++) if (pay >= t.rows[i][0] * 1000) row = t.rows[i];
      tax = row ? col(row.slice(2, 13), f) : 0;
    } else {
      tax = col(t.top, f);
      var o = null;
      t.over.forEach(function (x) { if (pay > x.above) o = x; });
      if (o) tax += o.add + B.cut10(pay - o.above, o.factor, o.rate);
    }
    var k = Math.max(0, children | 0);
    var kid = k === 0 ? 0 : k === 1 ? t.children.one : t.children.two + t.children.more * (k - 2);
    return Math.max(0, tax - kid);
  };
  // 월 실수령: 세전 − 4대보험 − 근로소득세 − 지방소득세(소득세의 10%, 10원 미만 버림).
  F.takeHome = function (o) {
    var g = Math.max(0, Math.floor(o.gross || 0)), free = Math.min(Math.max(0, Math.floor(o.free || 0)), g), taxable = g - free;
    var x = F.insurance(taxable, o.day, o.off), tax = F.incomeTax(taxable, o.family || 1, o.kids || 0, o.day);
    var local = tax ? Math.floor(tax / 100) * 10 : 0;
    var ded = x.pension + x.health + x.care + x.employment + (tax || 0) + local;
    return { gross: g, free: free, taxable: taxable, ins: x, tax: tax, local: local, ded: ded, net: g - ded, notes: x.notes.slice() };
  };
  // 주휴: 1주 소정근로 15시간 이상이면 min(분 ÷ 5, 8시간). 한 달은 365 ÷ 7 ÷ 12 = 약 4.345주.
  F.WEEKS = 365 / 7 / 12;
  F.juhyu = function (wage, weekMin) {
    weekMin = Math.round(weekMin);
    var hol = weekMin >= 900 ? Math.min(Math.floor(weekMin / 5), 480) : 0;
    var pay = hol / 60 * wage, monthHours = Math.round((weekMin + hol) / 60 * F.WEEKS);
    return { weekMin: weekMin, holMin: hol, pay: pay, monthPay: pay * F.WEEKS, monthHours: monthHours, monthTotal: monthHours * wage };
  };
  // 알바 월급. mode: none(안 뗌)·biz(3.3%)·ins(4대보험). weeks: 한 달 주 수(기본 4.345, 이번 달 달력이면 그달 날 수 ÷ 7).
  // 주 15시간 미만은 국민연금·건강보험이 빠지고, 고용보험은 3개월 넘게 일하면 떼요(고용보험법 제10조, 시행령 제3조).
  F.alba = function (o) {
    var weekMin = Math.round(o.weekMin), hol = weekMin >= 900 ? Math.min(Math.floor(weekMin / 5), 480) : 0;
    var hours = o.monthWorkMin != null ? Math.round((o.monthWorkMin + hol * o.weeks) / 60) : Math.round((weekMin + hol) / 60 * (o.weeks || F.WEEKS));
    var gross = hours * o.wage, out = { weekMin: weekMin, holMin: hol, hours: hours, gross: gross, mode: o.mode, notes: [] };
    if (o.mode === 'biz') { out.w = F.withholding(gross); out.ded = out.w.total; }
    else if (o.mode === 'ins') {
      var short = weekMin < 900;
      out.short = short;
      out.th = F.takeHome({ gross: gross, free: 0, family: 1, kids: 0, day: o.day, off: short ? { pension: true, health: true, employment: !o.over3m } : {} });
      out.ded = out.th.ded;
      out.notes = out.th.notes;
    } else out.ded = 0;
    out.net = gross - out.ded;
    return out;
  };
  // ---------- 근무 시간 (앱 core와 같은 식) ----------
  // 법정 휴게(근로기준법 제54조): 4시간이면 30분, 8시간 30분부터 1시간(앱 core LegalBreak.minutesFor).
  F.legalBreak = function (span) { return span >= 510 ? 60 : span >= 240 ? 30 : 0; };
  // 출근·퇴근(0시부터 분) → 걸친 분. 퇴근이 출근보다 이르면 다음 날까지, 같으면 24시간(앱 core StaffShift.spanMinutes).
  F.span = function (s, e) { var d = e - s; return d <= 0 ? d + 1440 : d; };
  // 22~06시 안의 분([from, to)는 그 주 월요일 0시부터 센 분).
  F.nightOf = function (from, to) {
    var n = 0;
    for (var d = Math.floor(from / 1440) - 1; d * 1440 < to; d++) {
      var a = Math.max(from, d * 1440 + 1320), b = Math.min(to, d * 1440 + 1800);
      if (b > a) n += b - a;
    }
    return n;
  };

  // ---------- 사장님 인건비 [I50b-01·T50-09] (앱 core LaborCost와 같은 식) ----------
  // 사업주 4대보험: 국민연금·건강보험·장기요양은 근로자와 같은 몫, 고용보험은 실업급여 몫 + 고용안정·직업능력개발 0.25%
  // (고용산재보험료징수법 시행령 제12조 제1항 제1호 가목, 150명 미만). 사업주 몫에는 건강보험 하한·상한을 두지 않아요(앱과 같음).
  // off.pension·off.health: 주 15시간 미만은 국민연금·건강보험 사업장 가입 대상이 아니에요(국민연금법 시행령 제2조, 국민건강보험법 시행령 제9조).
  F.STABILITY = 0.0025;
  F.employer = function (pay, day, off) {
    off = off || {};
    var r = rowOn(day), pr = B.pensionRateOn(day), hr = r.healthRate != null ? r.healthRate : 0.0719, ur = r.unemploymentRate, pb = B.pensionBaseOn(day);
    if (!(pay > 0)) return { pension: 0, health: 0, care: 0, employment: 0, total: 0, pensionRate: pr / 2, healthRate: hr / 2, employmentRate: ur / 2 + F.STABILITY };
    var pension = off.pension ? 0 : B.cut10(Math.min(Math.max(Math.floor(pay / 1000) * 1000, pb.min), pb.max), pr / 2);
    var health = off.health ? 0 : B.cut10(pay, hr / 2);
    var cr = B.careRatioOn(day), care = B.cutRatio(health, cr[0], cr[1]);
    var employment = off.employment ? 0 : B.cut10(pay, ur / 2 + F.STABILITY);
    return { pension: pension, health: health, care: care, employment: employment, total: pension + health + care + employment,
      pensionRate: pr / 2, healthRate: hr / 2, employmentRate: ur / 2 + F.STABILITY };
  };
  // o: { month: 'YYYY-MM', wage, days: [[요일 1(월)~7(일), 출근 분, 퇴근 분], …], breaks: 법정 휴게 빼기, insured: 4대보험, shortOff: 주 15시간 미만이면 연금·건강 빼기 }
  // 그달 날마다 그 요일 근무를 더하고(시간 × 시급, 원 미만 반올림), 주휴는 1주 15시간 이상이면 min(1주 분 ÷ 5, 8시간) × 그달 일요일 수
  // (주휴일이 그달에 든 주마다). 사업주 4대보험은 기본급 + 주휴에 그달 1일 요율로.
  F.labor = function (o) {
    var m = /^(\d{4})-(\d{2})$/.exec(o.month || ''), y = m ? +m[1] : 2026, mo = m ? +m[2] - 1 : 0;
    var last = new Date(Date.UTC(y, mo + 1, 0)).getUTCDate(), paid = {};
    (o.days || []).forEach(function (d) { var sp = F.span(d[1], d[2]); paid[d[0]] = sp - (o.breaks ? F.legalBreak(sp) : 0); });
    var minutes = 0, workDays = 0, sundays = 0;
    for (var i = 1; i <= last; i++) {
      var wd = new Date(Date.UTC(y, mo, i)).getUTCDay() || 7;
      if (paid[wd] != null) { minutes += paid[wd]; workDays++; }
      if (wd === 7) sundays++;
    }
    var week = Object.keys(paid).reduce(function (a, k) { return a + paid[k]; }, 0);
    var hol = week >= 900 ? Math.min(Math.floor(week / 5), 480) : 0;
    var base = Math.round(minutes / 60 * o.wage), holiday = o.holidayPay === false ? 0 : Math.round(hol / 60 * o.wage * sundays), pay = base + holiday;
    var short = week < 900, day = (m ? m[0] : '2026-01') + '-01';
    var ins = o.insured ? F.employer(pay, day, short && o.shortOff ? { pension: true, health: true } : null) : null;
    var insurance = ins ? ins.total : 0;
    return { minutes: minutes, workDays: workDays, week: week, holMin: hol, sundays: sundays, base: base, holiday: holiday, pay: pay,
      ins: ins, insurance: insurance, total: pay + insurance, short: short,
      perHour: minutes ? pay / (minutes / 60) : 0, costPerHour: minutes ? (pay + insurance) / (minutes / 60) : 0 };
  };

  // ---------- 한 주 연장·야간·휴일 [I50b-03] (앱 core MinuteClassifier 기본값과 같은 식) ----------
  // o.days: 월~일 7칸, 칸마다 { s: 출근 분, e: 퇴근 분, rest: 휴게 분 } 또는 null(쉬는 날). o.holidays: 월~일 7칸 true/false(유급휴일: 주휴일·공휴일).
  // 하루: 휴게는 근무 시간에 고르게 나눠 빼요(휴게 시각을 모를 때 앱과 같음). 휴일(그날 0~24시) 안의 분은 휴일 근로(8시간까지 50%, 넘으면 100%,
  // 제56조 제2항), 나머지는 하루 8시간까지 소정, 넘으면 연장. 휴일에 시작한 근무는 휴일 분이 먼저 8시간을 채워요(검토 PAYC-02).
  // 한 주: 월요일부터 날짜 순으로 쌓아 40시간을 넘는 소정 분은 연장. 휴일 근로도 40시간을 채우되 그 분은 휴일 가산만 받아요
  // (제2조 제1항 제7호 '1주는 휴일을 포함한 7일', 검토 PAYC-01). 야간(22~06시)은 무엇이든 50% 더해요(제56조 제3항).
  F.week = function (o) {
    var hols = o.holidays || [], isHol = function (i) { return i >= 0 && i < 7 && !!hols[i]; };
    var days = [], used = 0, T = { regular: 0, overtime: 0, daily: 0, hWithin: 0, hOver: 0, night: 0, worked: 0, rest: 0 };
    for (var i = 0; i < 7; i++) {
      var d = o.days && o.days[i];
      if (!d) { days.push(null); continue; }
      var gross = F.span(d.s, d.e), rest = Math.min(Math.max(0, Math.round(d.rest || 0)), gross), paid = gross - rest;
      var from = i * 1440 + d.s, to = from + gross, mid = (i + 1) * 1440;
      var grossNext = Math.max(0, to - Math.max(from, mid));
      var next = paid > 0 ? Math.min(Math.max(Math.round(grossNext * paid / gross), 0), paid) : 0;
      var night = paid > 0 ? Math.round(F.nightOf(from, to) * paid / gross) : 0;
      var onHol = (isHol(i) ? paid - next : 0) + (isHol(i + 1) ? next : 0), left = paid - onHol, slot = 480;
      if (onHol > 0 && left > 0 && isHol(i)) slot -= Math.min(onHol, Math.max(slot, 0));
      var hWithin = Math.min(onHol, 480), regular = Math.min(left, Math.max(slot, 0)), overtime = left - regular;
      days.push({ paid: paid, rest: rest, regular: regular, overtime: overtime, daily: overtime, hWithin: hWithin, hOver: onHol - hWithin, night: night });
    }
    days.forEach(function (x) {
      if (!x) return;
      var over = Math.min(Math.max(used + x.regular - 2400, 0), x.regular);
      used += x.regular - over + x.hWithin + x.hOver;
      x.regular -= over; x.overtime += over;
      ['regular', 'overtime', 'daily', 'hWithin', 'hOver', 'night', 'rest'].forEach(function (k) { T[k] += x[k]; });
      T.worked += x.paid;
    });
    T.weekly = T.overtime - T.daily;
    T.days = days;
    // 주휴: 1주 소정(휴일 근로 빼고 연장 전) 15시간 이상이면 min(분 ÷ 5, 8시간). 그 주를 모두 나왔다고 보고 셈해요.
    var plan = T.regular + T.weekly;
    T.holMin = plan >= 900 ? Math.min(Math.floor(plan / 5), 480) : 0;
    T.over52 = T.worked > 52 * 60;
    if (o.wage != null) {
      var h = function (x) { return x / 60 * o.wage; }, small = !!o.small;
      T.base = h(T.worked);
      T.ext = small ? 0 : h(T.overtime) * 0.5;
      T.nightP = small ? 0 : h(T.night) * 0.5;
      T.holP = small ? 0 : h(T.hWithin) * 0.5 + h(T.hOver) * 1.0;
      T.sum = T.base + T.ext + T.nightP + T.holP;
      T.holPay = h(T.holMin);
    }
    return T;
  };

  // 일용근로소득: 하루 (일당 − 15만 원) × 6% × (1 − 55%) = 2.7%를 원 미만 버림. 받을 때(지급 한 번)마다 그 날들의 합에서
  // 10원 미만을 버리고(국고금 관리법 제47조), 1,000원 미만이면 떼지 않아요(소액부징수). 지방소득세는 그 소득세의 10%에서 10원 미만 버림.
  // 날마다 받으면 날마다 한 번의 지급이에요. 국세청 '일용근로자 원천징수' 예: 20만 원 5일 한 번에 → 6,750원·670원. 앱 core DailyWorkerTax와 같은 식.
  // [I50b-02] 통상시급(근로기준법 시행령 제6조): (기본급 + 매달 정해진 통상 수당 + 정기상여 1년 합 ÷ 12) ÷ 월 기준시간, 원 단위 반올림.
  // 정기상여는 대법원 2024. 12. 19. 선고 2020다247190·2023다302838 전원합의체 판결 뒤의 임금부터 재직 조건이 붙어도 통상임금이에요.
  // 앱 core PayCalculator(ordinaryWage, 통상시급)와 같은 식이에요(상여 월할도 원 단위 반올림).
  F.ordinary = function (o) {
    var bonus = Math.round((o.bonusYear || 0) / 12), month = (o.base || 0) + (o.allow || 0) + bonus, h = Math.max(1, o.hours || 209);
    return { bonus: bonus, month: month, hours: h, hourly: Math.round(month / h), baseOnly: Math.round((o.base || 0) / h) };
  };
  // [I50b-02] 명세서 한 줄: 시간 × 통상시급 × 배율(연장 1.5, 야간 가산 0.5, 휴일 8시간까지 1.5·넘으면 2.0, 근로기준법 제56조).
  // 5명 미만은 가산 없이 연장 1.0·야간 0·휴일 1.0(제11조). 명세서 금액 ÷ 시간 ÷ 배율로 회사가 쓴 시급을 거꾸로 구해, 통상임금이나
  // 기본급만을 흔한 시수(180·209·226·243)로 나눈 값과 3% 안에서 맞는지 봐요(앱 core SlipRules의 시수 거꾸로 찾기와 같은 방법).
  F.SLIP = { ext: [1.5, 1.0], night: [0.5, 0], hol: [1.5, 1.0], hol8: [2.0, 1.0] };
  F.HOURS = [180, 209, 226, 243];
  F.slip = function (o) {
    var mult = F.SLIP[o.kind][o.small ? 1 : 0], want = Math.round(o.minutes / 60 * o.hourly * mult);
    var r = { mult: mult, want: want, diff: Math.round(o.paid) - want, ok: Math.abs(Math.round(o.paid) - want) <= Math.max(100, want * 0.005) };
    if (o.paid > 0 && o.minutes > 0 && mult > 0) {
      var rate = o.paid / (o.minutes / 60) / mult, best = null;
      r.rate = rate;
      [['ordinary', o.month], ['base', o.base]].forEach(function (w) {
        if (!(w[1] > 0)) return;
        F.HOURS.forEach(function (h) { var err = Math.abs(w[1] / h - rate) / (w[1] / h); if (err <= 0.03 && (!best || err < best.err)) best = { of: w[0], hours: h, err: err }; });
      });
      if (best) { r.of = best.of; r.hours = best.hours; }
    }
    return r;
  };
  F.dailyTaxOfDay = function (pay) { return Math.floor(Math.max(0, Math.floor(pay) - 150000) * 27 / 1000); };
  F.daily = function (o) {
    var whole = Math.floor(o.days), part = Math.round((o.days - whole) * 100) / 100, pays = [];
    for (var i = 0; i < whole; i++) pays.push(Math.floor(o.pay));
    if (part > 0) pays.push(Math.floor(o.pay * part));
    var per = pays.map(F.dailyTaxOfDay);
    var paid = function (days) { var t = floor10(days.reduce(function (a, b) { return a + b; }, 0)); t = t < 1000 ? 0 : t; return { income: t, local: floor10(t / 10) }; };
    var runs = o.paid === 'together' ? [paid(per)] : per.map(function (d) { return paid([d]); });
    var income = runs.reduce(function (a, r) { return a + r.income; }, 0), local = runs.reduce(function (a, r) { return a + r.local; }, 0);
    var gross = pays.reduce(function (a, b) { return a + b; }, 0), r = rowOn(o.day);
    var emp = !o.emp ? 0 : o.paid === 'together' ? B.cut10(gross, r.unemploymentRate / 2)
      : pays.reduce(function (a, p) { return a + B.cut10(p, r.unemploymentRate / 2); }, 0);
    return { gross: gross, income: income, local: local, emp: emp, net: gross - income - local - emp, perDay: per[0] || 0, dayCount: pays.length };
  };
  // 야간(22~06시)·연장(하루 8시간 넘게)·휴일 가산(근로기준법 제56조). 5명 미만은 가산 없음(제11조).
  F.allowance = function (o) {
    var s = o.start, e = o.end;
    if (e <= s) e += 1440;
    var total = 0, night = 0;
    for (var m = s; m < e; m++) { total++; var t = m % 1440; if (t >= 1320 || t < 360) night++; }
    var rest = Math.min(Math.floor(o.rest), total);
    if (o.restNight) night -= Math.min(rest, night);
    else night -= Math.min(Math.max(0, rest - (total - night)), night);
    var work = total - rest, over = Math.max(0, work - 480), h = function (x) { return x / 60; }, w = o.wage;
    var base = h(work) * w, ext = 0, nightP = 0, holP = 0;
    if (!o.small) {
      nightP = h(night) * w * 0.5;
      if (o.holiday) holP = h(Math.min(work, 480)) * w * 0.5 + h(over) * w * 1.0; else ext = h(over) * w * 0.5;
    }
    return { total: total, rest: rest, work: work, night: night, over: over, base: base, ext: ext, nightP: nightP, holP: holP, sum: base + ext + nightP + holP };
  };
  // 연차(근로기준법 제60조): 1년 미만은 한 달 개근마다 1일(최대 11일, 입사 1년까지 씀), 1년마다 15일 + 2년마다 1일(최대 25일).
  // basis 'fiscal'이면 회계연도(fiscalMonth월 1일) 기준: 첫해는 15일 × 재직일수 ÷ 365.
  F.yearDays = function (n) { return n < 1 ? 0 : Math.min(25, 15 + Math.floor((n - 1) / 2)); };
  F.leaveLots = function (hire, until, basis, fiscalMonth) {
    if (until < hire) return [];
    var out = [], firstYearEnd = D.periodEnd(hire, 12);
    for (var m = 1; m <= 11; m++) {
      var on = D.plusDays(D.periodEnd(hire, m), 1);
      if (on > until || on > firstYearEnd) break;
      out.push({ kind: 'month', days: 1, on: on, until: firstYearEnd, n: m });
    }
    if (basis !== 'fiscal') {
      for (var n = 1; ; n++) {
        var at = D.plusDays(D.periodEnd(hire, 12 * n), 1);
        if (at > until) break;
        out.push({ kind: 'year', days: F.yearDays(n), on: at, until: D.periodEnd(hire, 12 * (n + 1)), n: n });
      }
    } else {
      var fm = Math.min(12, Math.max(1, fiscalMonth || 1)), y = hire.getUTCFullYear();
      var start = new Date(Date.UTC(y, fm - 1, 1));
      if (start > hire) start = new Date(Date.UTC(y - 1, fm - 1, 1));
      if (!(start > hire)) start = D.plusYears(start, 1);
      var offset = D.between(hire, start) < 365 ? 0 : 1;
      for (var k = 0; !(start > until); k++) {
        var served = D.between(hire, start), prorated = k === 0 && offset === 0, years = k + offset;
        out.push({ kind: prorated ? 'part' : 'year', days: prorated ? Math.round(15 * served / 365 * 100) / 100 : F.yearDays(years), on: start, until: D.plusDays(D.plusYears(start, 1), -1), n: years, served: served });
        start = D.plusYears(start, 1);
      }
    }
    return out;
  };
  // 퇴직금(근로자퇴직급여 보장법 제8조): 1일 평균임금 × 30 × 재직일수 ÷ 365. 평균임금은 퇴직 전 3개월 임금(상여·연차수당은 3/12) ÷ 그 날수,
  // 통상임금이 더 높으면 통상임금(근로기준법 제2조 제2항). 달력으로 만 1년이 되어야 생겨요.
  F.severance = function (o) {
    if (!o.hire || !o.last || o.hire > o.last) return null;
    var days = D.between(o.hire, o.last) + 1, retire = D.plusDays(o.last, 1);
    var n3 = Math.max(1, D.between(D.plusMonths(retire, -3), retire));
    var total = o.monthPay * 3 + (o.bonus || 0) * 3 / 12 + (o.leavePay || 0) * 3 / 12;
    var avg = Math.round(total / n3), ordinary = Math.round(o.ordinaryDaily || 0), daily = Math.max(avg, ordinary);
    var fullYear = !(D.plusYears(o.hire, 1) > retire);
    return { days: days, n3: n3, total: total, avg: avg, daily: daily, byOrdinary: ordinary > avg, fullYear: fullYear, amount: fullYear ? Math.round(daily * 30 * days / 365) : 0 };
  };

  // ---------- 자료 받기 ----------
  var getJson = function (name) {
    if (typeof fetch !== 'function') return Promise.resolve(null);
    return fetch(base + 'remote/v1/' + name + '.json', { cache: 'no-cache' }).then(function (r) { return r.ok ? r.json() : null; }).catch(function () { return null; });
  };
  B.ready = getJson('rates').then(function (j) {
    if (j && j.data && j.data.rates && j.data.rates.length) B.data.rates = j.data.rates;
    if (j && j.data && j.data.pensionBases && j.data.pensionBases.length) B.data.pensionBases = j.data.pensionBases;
  });
  B.loadTaxTable = function () {
    return getJson('taxtable').then(function (j) { if (j && j.data && j.data.tables) B.data.taxTables = j.data.tables; return B.data.taxTables.length ? B.data.taxTables : null; });
  };

  // ---------- 앱 받기 링크 ----------
  // Play 설치 출처: utm_source는 늘 bamnat_web, 쪽 종류(계산기·달력·공휴일·양식·첫 화면)는 utm_medium, 단추 자리는 utm_campaign.
  // 커뮤니티 글처럼 ?utm_source=…로 들어온 방문이면 그 출처를 utm_term과 campaign 앞머리에만 실어요(우리 이름 bamnat_…은 버려요).
  B.medium = function () {
    var p = hasDoc ? location.pathname.replace(/^.*?\/(calc|shift|holiday|form|r)\/.*$/, '$1') : '';
    return /^(calc|shift|holiday|form|r)$/.test(p) ? (p === 'r' ? 'recipe' : p) : 'home';
  };
  B.visit = function () {
    var clean = function (v) { var s = String(v || '').toLowerCase().replace(/[^a-z0-9_.-]/g, '').slice(0, 32); return /^bamnat/.test(s) ? '' : s; };
    try {
      var q = new URLSearchParams(location.search), s = clean(q.get('utm_source'));
      if (s) { sessionStorage.setItem('bn_from', s); return s; }
      return clean(sessionStorage.getItem('bn_from'));
    } catch (e) { return ''; }
  };
  // 받는 쪽 페이지(레시피 링크 r/)는 body에 data-source="bamnat_share"를 둬요. 주소로는 바꿀 수 없고 이 두 값만 받아요.
  B.source = function () { return hasDoc && document.body && document.body.dataset.source === 'bamnat_share' ? 'bamnat_share' : 'bamnat_web'; };
  B.playLink = function (campaign, medium, extra) {
    var from = hasDoc ? B.visit() : '';
    var ref = 'utm_source=' + B.source() + '&utm_medium=' + (medium || B.medium()) + '&utm_campaign=' + (from ? from + '-' : '') + campaign + (from ? '&utm_term=' + from : '') + (extra || '');
    return 'https://play.google.com/store/apps/details?id=com.bamnat.app&referrer=' + encodeURIComponent(ref);
  };

  // ---------- 칸·칩·결과 표 ----------
  B.$ = function (id) { return hasDoc ? document.getElementById(id) : null; };
  // 칸 아래 한 줄 안내(범위·숫자 아님·금액 읽어 주기). 칸 바로 뒤에 저절로 생겨요.
  B.say = function (input, text, kind) {
    var id = input.id + '-say', el = document.getElementById(id);
    if (!el) {
      el = document.createElement('span'); el.id = id; el.className = 'say'; el.setAttribute('aria-live', 'polite');
      input.insertAdjacentElement('afterend', el);
      var d = input.getAttribute('aria-describedby');
      input.setAttribute('aria-describedby', (d ? d + ' ' : '') + id);
    }
    el.textContent = text || '';
    el.className = 'say' + (kind ? ' ' + kind : '');
    input.toggleAttribute('aria-invalid', kind === 'warn');
  };
  // 숫자 칸 읽기: min·max 밖이면 안쪽 값으로 셈하고 한 줄로 알려요. 금액 칸(data-money)은 "300만 원"도 읽어 줘요.
  B.val = function (id) {
    var el = B.$(id), v = B.num(el.value), min = el.dataset.min != null ? +el.dataset.min : 0, max = el.dataset.max != null ? +el.dataset.max : Infinity;
    var unit = el.dataset.unit || '';
    if (isNaN(v)) { B.say(el, '숫자만 적어 주세요', 'warn'); return 0; }
    if (el.value !== '' && v < min) { B.say(el, B.fmt(min) + unit + '부터 셈해요', 'warn'); return min; }
    if (v > max) { B.say(el, B.fmt(max) + unit + '까지만 셈해요', 'warn'); return max; }
    B.say(el, el.hasAttribute('data-money') ? B.read(v) : '');
    return v;
  };
  // 금액 칸: 치는 대로 쉼표를 넣고 커서는 같은 숫자 뒤에 둬요.
  B.money = function (el) {
    var tidy = function () {
      var raw = el.value, caret = el.selectionStart == null ? raw.length : el.selectionStart;
      var digitsLeft = raw.slice(0, caret).replace(/[^0-9０-９]/g, '').length;
      var n = B.num(raw);
      if (isNaN(n) || raw === '') return;
      var s = Math.floor(n).toLocaleString('ko-KR');
      if (s === raw) return;
      el.value = s;
      var pos = 0, seen = 0;
      while (pos < s.length && seen < digitsLeft) { if (/\d/.test(s[pos])) seen++; pos++; }
      try { if (document.activeElement === el) el.setSelectionRange(pos, pos); } catch (e) {}
    };
    el.addEventListener('input', tidy);
    tidy();
  };
  // 칩 묶음: 하나만 눌린 채로. 고르면 onPick(값).
  B.chips = function (box, onPick) {
    var all = function () { return box.querySelectorAll('.chip'); };
    var set = function (v) { all().forEach(function (c) { c.setAttribute('aria-pressed', c.dataset.v === String(v) ? 'true' : 'false'); }); };
    box.addEventListener('click', function (e) {
      var b = e.target.closest('.chip');
      if (!b || !box.contains(b) || b.getAttribute('aria-pressed') === 'true') return;
      set(b.dataset.v); onPick(b.dataset.v);
    });
    var cur = box.querySelector('.chip[aria-pressed="true"]');
    return { set: set, get: function () { var c = box.querySelector('.chip[aria-pressed="true"]'); return c ? c.dataset.v : (cur && cur.dataset.v); } };
  };
  // 최저시급 칩: 올해와 내년(고시된 해)만.
  B.yearChips = function (box, onPick, label) {
    box.innerHTML = '';
    B.years().forEach(function (y, i) {
      var b = document.createElement('button');
      b.type = 'button'; b.className = 'chip'; b.dataset.v = y;
      b.textContent = label ? label(y) : y + '년 최저 ' + B.rateOn(B.dayOf(y)).minimumHourlyWage.toLocaleString('ko-KR') + '원';
      b.setAttribute('aria-pressed', i === 0 ? 'true' : 'false');
      box.appendChild(b);
    });
    return B.chips(box, function (v) { onPick(+v); });
  };
  // 결과 표: [이름, 값, 꾸밈] 줄과 { note: '…' } 줄. 이름은 줄 머리(th)라 화면 낭독기가 '이름 – 값'으로 읽어요.
  B.rows = function (list) {
    return list.filter(Boolean).map(function (r) {
      if (r.note) return '<tr class="noterow"><td colspan="2" class="note' + (r.warn ? ' warn' : '') + '">' + r.note + '</td></tr>';
      return '<tr' + (r[2] ? ' class="' + r[2] + '"' : '') + '><th scope="row">' + r[0] + '</th><td>' + (r[1] == null ? '' : r[1]) + '</td></tr>';
    }).join('');
  };
  // 입력값을 주소 꼬리에 두어 '결과 링크 보내기'로 나눠요(서버로는 아무것도 가지 않아요).
  B.keep = function (ids, chipBoxes) {
    var q = new URLSearchParams(location.search);
    ids.forEach(function (id) {
      var el = B.$(id); if (!el || !q.has(id)) return;
      if (el.type === 'checkbox') el.checked = q.get(id) === '1';
      // [I50b-02] 받은 링크의 금액도 쉼표를 넣어 보여 줘요(전에는 '2500000'처럼 붙어 나왔어요).
      else { var v = q.get(id), n = B.num(v); el.value = el.hasAttribute('data-money') && v !== '' && !isNaN(n) ? Math.floor(n).toLocaleString('ko-KR') : v; }
    });
    Object.keys(chipBoxes || {}).forEach(function (k) { if (q.has(k)) chipBoxes[k].set(q.get(k)); });
    var timer = null;
    return function save() {
      clearTimeout(timer);
      timer = setTimeout(function () {
        var p = new URLSearchParams();
        ids.forEach(function (id) { var el = B.$(id); if (!el) return; var v = el.type === 'checkbox' ? (el.checked ? '1' : '0') : String(el.value).replace(/,/g, ''); if (v !== '') p.set(id, v); });
        Object.keys(chipBoxes || {}).forEach(function (k) { var v = chipBoxes[k].get(); if (v != null) p.set(k, v); });
        try { history.replaceState(null, '', location.pathname + '?' + p.toString()); } catch (e) {}
      }, 250);
    };
  };
  // [I50b-01·03] 요일 7줄: 요일 단추(일하는 날·쉬는 날, #on에 '12345'처럼 적어 결과 링크로 나눠요)와 출근·퇴근 칸. 쉬는 날은 칸이 잠겨요.
  // 사장님 인건비·한 주 계산기가 같이 써요. 칸 모양은 public_pages.py week_grid().
  B.weekGrid = function (onChange) {
    var on = B.$('on'), N = [1, 2, 3, 4, 5, 6, 7];
    var mins = function (t) { var p = /^(\d{1,2}):(\d{2})/.exec(t || ''); return p ? (+p[1]) * 60 + (+p[2]) : null; };
    var works = function (i) { return on.value.indexOf(String(i)) >= 0; };
    var paint = function () {
      N.forEach(function (i) {
        var w = works(i);
        B.$('w' + i).setAttribute('aria-pressed', w ? 'true' : 'false'); B.$('r' + i).classList.toggle('off', !w);
        B.$('s' + i).disabled = !w; B.$('e' + i).disabled = !w;
      });
    };
    var setOn = function (list) { on.value = N.filter(function (i) { return list.indexOf(i) >= 0; }).join(''); paint(); };
    N.forEach(function (i) {
      B.$('w' + i).addEventListener('click', function () {
        var list = N.filter(works), at = list.indexOf(i);
        if (at >= 0) list.splice(at, 1);
        else {
          // 새로 켠 날은 가장 가까운 일하는 날의 시각을 따라요(같은 시간을 일곱 번 넣지 않게).
          var near = list.slice().sort(function (a, b) { return Math.abs(a - i) - Math.abs(b - i) || a - b; })[0];
          if (near) { B.$('s' + i).value = B.$('s' + near).value; B.$('e' + i).value = B.$('e' + near).value; }
          list.push(i);
        }
        setOn(list); onChange();
      });
      ['s', 'e'].forEach(function (k) { var el = B.$(k + i); el.addEventListener('input', onChange); el.addEventListener('change', onChange); });
    });
    return {
      ids: ['on'].concat(N.map(function (i) { return 's' + i; }), N.map(function (i) { return 'e' + i; })),
      sync: paint,
      read: function () {
        return N.map(function (i) { var s = mins(B.$('s' + i).value), e = mins(B.$('e' + i).value); return { day: i, work: works(i) && s != null && e != null, s: s, e: e }; });
      },
      fill: function (days, s, e) {
        N.forEach(function (i) { if (days.indexOf(String(i)) >= 0) { B.$('s' + i).value = s; B.$('e' + i).value = e; } });
        setOn(N.filter(function (i) { return days.indexOf(String(i)) >= 0; }));
      },
      // '1@07:00-19:00,3@19:00-07:00': 요일마다 다른 근무(2교대 등).
      plan: function (spec) {
        var list = [];
        spec.split(',').forEach(function (p) {
          var m = /^([1-7])@(\d{2}:\d{2})-(\d{2}:\d{2})$/.exec(p);
          if (m) { list.push(+m[1]); B.$('s' + m[1]).value = m[2]; B.$('e' + m[1]).value = m[3]; }
        });
        setOn(list);
      }
    };
  };
  B.shareButton = function (btn, title) {
    var msg = document.createElement('span'); msg.className = 'say'; msg.setAttribute('aria-live', 'polite');
    btn.insertAdjacentElement('afterend', msg);
    btn.addEventListener('click', function () {
      var u = location.href;
      if (navigator.share) navigator.share({ title: title, url: u }).catch(function () {});
      else if (navigator.clipboard) navigator.clipboard.writeText(u).then(function () { msg.textContent = '링크를 복사했어요. 카톡에 붙여 넣어 보내요.'; }, function () { msg.textContent = '복사하지 못했어요. 주소창의 주소를 복사해 주세요.'; });
      else msg.textContent = '주소창의 주소를 복사해 보내요.';
    });
  };

  // [U50-15] 밤낮은 Android 앱이에요. 아이폰·아이패드에서는 받기 단추 자리에 한 줄로 알리고, 이 사이트에서 바로 쓰는 곳으로 이어요.
  B.ios = function (ua, touch) { return /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && touch > 1); };
  B.iosNote = function (a) {
    var onShift = /\/shift\/$/.test(location.pathname), p = document.createElement('p');
    p.className = 'ios-note';
    p.innerHTML = '밤낮 앱은 Android(Google Play)용이에요. 아이폰에서는 ' + (onShift ? '이 달력을 홈 화면에 더해 매일 오늘 근무를 봐요.' : '<a href="' + B.base + 'shift/">교대 근무 달력</a>과 계산기를 여기서 바로 써요.');
    a.replaceWith(p);
  };

  if (!hasDoc) return;
  document.addEventListener('DOMContentLoaded', function () {
    if (B.ios(navigator.userAgent, navigator.maxTouchPoints || 0)) {
      document.documentElement.classList.add('ios');
      document.querySelectorAll('a[data-play]').forEach(function (a) {
        if (a.closest('.dock') || a.classList.contains('get')) a.hidden = true; else if (!a.closest('.ios-skip')) B.iosNote(a);
      });
      var dk = document.getElementById('dock'); if (dk) dk.hidden = true;
    }
    document.querySelectorAll('a[data-play]').forEach(function (a) { a.href = B.playLink(a.dataset.play, a.dataset.medium); });
    document.querySelectorAll('input[data-money]').forEach(B.money);
    B.ui();
  });
  // 머리 띠(스크롤하면 진하게, 휴대폰 메뉴), 결과 숫자가 바뀌면 살짝 올라오기, 아래 카드 나타나기.
  B.ui = function () {
    var head = document.querySelector('.site-header');
    if (head) {
      var onScroll = function () { head.classList.toggle('scrolled', window.scrollY > 8); };
      onScroll(); window.addEventListener('scroll', onScroll, { passive: true });
      var btn = head.querySelector('.menu-btn'), menu = head.querySelector('.menu');
      // 접힌 메뉴는 보이지 않으니 자판 초점·화면 낭독기도 지나가지 않게(inert), 펼 때만 풀어요.
      var wide = window.matchMedia ? window.matchMedia('(min-width: 900px)') : null;
      var shut = function (on) { if (menu) { if (on) menu.setAttribute('inert', ''); else menu.removeAttribute('inert'); } };
      shut(true);
      if (btn) btn.addEventListener('click', function () {
        var open = head.classList.toggle('open');
        btn.setAttribute('aria-expanded', open ? 'true' : 'false');
        btn.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
        shut(!open);
        if (open) { var first = menu && menu.querySelector('a'); if (first) first.focus(); }
      });
      document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && head.classList.contains('open')) { btn.click(); btn.focus(); } });
      if (wide && wide.addEventListener) wide.addEventListener('change', function () { if (wide.matches && head.classList.contains('open')) btn.click(); });
    }
    if (window.MutationObserver) document.querySelectorAll('.big').forEach(function (el) {
      var last = el.textContent;
      new MutationObserver(function () {
        if (el.textContent === last) return; last = el.textContent;
        el.classList.remove('bump'); void el.offsetWidth; el.classList.add('bump');
      }).observe(el, { childList: true, characterData: true, subtree: true });
    });
    // 휴대폰 아래 받기 띠: 머리 그림을 지나 내려가면 나오고, 아래 앱 카드·앱에서 이어지는 것·바닥이 보이거나 입력 중(자판이 열림)이면 숨어요.
    // 첫 화면은 assets/home.js가 따로 다뤄요. 인쇄할 때는 늘 숨어요(calc.css).
    var dock = document.getElementById('dock');
    if (dock && !document.body.classList.contains('home') && 'IntersectionObserver' in window) {
      var vis = { hero: true, cta: false, next: false, foot: false }, typing = false;
      var upd = function () {
        var on = !vis.hero && !vis.cta && !vis.next && !vis.foot && !typing;
        dock.classList.toggle('on', on); dock.setAttribute('aria-hidden', on ? 'false' : 'true');
        dock.querySelector('a').tabIndex = on ? 0 : -1;
      };
      var watch = function (el, key) { if (el) new IntersectionObserver(function (e) { vis[key] = e[0].isIntersecting; upd(); }).observe(el); };
      watch(document.querySelector('.page-hero'), 'hero'); watch(document.querySelector('.cta'), 'cta'); watch(document.querySelector('.app-next'), 'next'); watch(document.querySelector('.site-footer'), 'foot');
      document.addEventListener('focusin', function (e) { if (/^(INPUT|SELECT|TEXTAREA)$/.test(e.target.tagName)) { typing = true; upd(); } });
      document.addEventListener('focusout', function () { typing = false; setTimeout(upd, 150); });
    }
    var items = document.querySelectorAll('.reveal');
    if (!('IntersectionObserver' in window)) { items.forEach(function (el) { el.classList.add('in'); }); return; }
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } });
    }, { rootMargin: '0px 0px -8% 0px' });
    items.forEach(function (el) { io.observe(el); });
  };
})(typeof window !== 'undefined' ? window : globalThis);
