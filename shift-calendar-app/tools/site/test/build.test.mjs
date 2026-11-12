// 공개로 나가는 파일 검사 [WEB-22]: 사이트를 임시 폴더에 만든 뒤, 공개 저장소에 둘 수 없는 파일·내부 주소·지난해 숫자가 섞이면
// build.py가 실패하는지 봐요. 돌리기: node --test tools/site/test/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const site = path.resolve(here, '..');
const remote = path.resolve(site, '../../../remote/v1');
const py = (...args) => spawnSync('python3', [path.join(site, 'build.py'), ...args], { encoding: 'utf8', env: { ...process.env, BAMNAT_SITE_TODAY: '2026-11-12' } });

test('목록 밖 파일·내부 주소·지난해 숫자가 섞이면 실패해요', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bamnat-site-test-'));
  try {
    fs.cpSync(remote, path.join(root, 'remote/v1'), { recursive: true });
    const built = py(root, '--today', '2026-11-12');
    assert.equal(built.status, 0, built.stdout + built.stderr);
    assert.match(built.stdout, /검사 통과/);
    // 만든 쪽에는 추적 번호([WEB-12] 같은 것)가 남지 않아요.
    const js = fs.readFileSync(path.join(root, 'calc/calc.js'), 'utf8');
    assert.doesNotMatch(js, /\[(?:WEB|U50|T50|I50a|SPREAD)-\d+\]/);
    assert.match(js, /\/\*@rates\*\/\{"rates":/, '자료를 못 받을 때 값은 rates.json에서 채워요');
    // [I50c-02] 내 근무 달력: 홈 화면 설치 정보의 아이콘이 모두 있고, 시작 주소는 앱 표시(?app=1), 서비스 워커는 이 사이트 GET만.
    const mf = JSON.parse(fs.readFileSync(path.join(root, 'shift/manifest.webmanifest'), 'utf8'));
    assert.equal(mf.start_url, './?app=1');
    assert.equal(mf.scope, './');
    for (const i of mf.icons) assert.ok(fs.statSync(path.join(root, 'shift', i.src)).size > 1000, i.src);
    assert.ok(mf.icons.some((i) => i.purpose === 'maskable' && i.sizes === '512x512'));
    const sw = fs.readFileSync(path.join(root, 'shift/sw.js'), 'utf8');
    assert.match(sw, /r\.method !== 'GET' \|\| u\.origin !== location\.origin/);
    const shift = fs.readFileSync(path.join(root, 'shift/index.html'), 'utf8');
    assert.match(shift, /<link rel="apple-touch-icon" href="\.\.\/assets\/icon-180\.png">/);
    assert.ok(fs.statSync(path.join(root, 'assets/icon-180.png')).size > 1000);

    const bad = (file, text, expect) => {
      const f = path.join(root, file);
      fs.mkdirSync(path.dirname(f), { recursive: true });
      fs.writeFileSync(f, text);
      const r = py(root, '--verify', '--today', '2026-11-12');
      fs.rmSync(f);
      assert.equal(r.status, 1, `${file}: 실패해야 해요\n${r.stdout}`);
      assert.match(r.stdout, expect, file);
    };
    bad('notes.txt', '메모', /공개 저장소에 둘 수 없는 파일: notes\.txt/);
    bad('calc/plan.md', '# 계획', /코드·문서 파일은 공개하지 않아요: calc\/plan\.md/);
    bad('shift/tool.py', 'print(1)', /코드·문서 파일은 공개하지 않아요: shift\/tool\.py/);
    bad('og/x.svg', '<svg>http://10.0.2.2:8789</svg>', /og\/x\.svg: 공개 파일에 "10\.0\.2\.2"/);
    bad('calc/x/index.html', '<p>2025년 최저시급</p>', /지난해 "2025년"/);
    bad('calc/y/index.html', '<!-- [WEB-12] -->', /추적 번호 \[WEB-12\]/);
    const ok = py(root, '--verify', '--today', '2026-11-12');
    assert.equal(ok.status, 0, ok.stdout);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
