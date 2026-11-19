// [RVN-22] 만든 파일이에요. 손으로 고치지 마세요.
// 원본: shift-calendar-app/core/src/main/kotlin/com/bamnat/core/plaza/TextRules.kt (글에 받지 않는 것, 검토 I10bb-03)
// 다시 만들기: BAMNAT_WRITE_TEXT_RULES=1 ./gradlew -p core test --tests '*TextRulesExportTest*' --rerun
// 낡으면 core TextRulesExportTest가 실패해요. 회사 서버 index.js의 personal()·contact()가 이 표를 읽어요.

/** Each rule: code, source and flags of its RegExp, and digits [min, max] a match must hold (null: any). */
export const TEXT_RULES = {"personal":[{"code":"email","source":"[^\\s@]+@[^\\s@]+\\.[a-z]{2,}","flags":"i","digits":null},{"code":"account","source":"(?<![\\d-])(?!(?:0|82-?)(?:1[016789]|2|[3-6][1-5]|70)-\\d{3,4}-\\d{4}(?![\\d-]))\\d{2,6}(?:-\\d{2,8}){1,4}(?![\\d-])","flags":"","digits":[10,16]},{"code":"account","source":"(\uc740\ud589|\uacc4\uc88c|\uc785\uae08|\uc1a1\uae08|\uc774\uccb4|\ubc45\ud06c|\uc6b0\uccb4\uad6d|\ub18d\ud611|\uc2e0\ud611|\uc0c8\ub9c8\uc744\uae08\uace0)[^\\d\\n]{0,12}\\d[\\d -]{6,}\\d","flags":"","digits":null},{"code":"phone","source":"(\\+?82[\\s.-]?0?|0)\\d{1,2}[\\s.-]?\\d{3,4}[\\s.-]?\\d{4}","flags":"","digits":null},{"code":"amount","source":"\\d[\\d,.]*\\s*(\uc6d0|\ub9cc\\s*\uc6d0|\ucc9c\\s*\uc6d0|\uc5b5\\s*\uc6d0|won\\b|krw\\b)|[\uc77c\uc774\uc0bc\uc0ac\uc624\uc721\uce60\ud314\uad6c\uc2ed\ubc31\ucc9c]+\\s*\ub9cc\\s*\uc6d0|\\d{1,3}(,\\d{3})+|\\d{5,}","flags":"i","digits":null}],"contact":[{"code":"email","source":"[^\\s@]+@[^\\s@]+\\.[a-z]{2,}","flags":"i","digits":null},{"code":"account","source":"(?<![\\d-])(?!(?:0|82-?)(?:1[016789]|2|[3-6][1-5]|70)-\\d{3,4}-\\d{4}(?![\\d-]))\\d{2,6}(?:-\\d{2,8}){1,4}(?![\\d-])","flags":"","digits":[10,16]},{"code":"account","source":"(\uc740\ud589|\uacc4\uc88c|\uc785\uae08|\uc1a1\uae08|\uc774\uccb4|\ubc45\ud06c|\uc6b0\uccb4\uad6d|\ub18d\ud611|\uc2e0\ud611|\uc0c8\ub9c8\uc744\uae08\uace0)[^\\d\\n]{0,12}\\d[\\d -]{6,}\\d","flags":"","digits":null},{"code":"phone","source":"(?<!\\d)(\\+?82[\\s.-]?0?|0)(1[016789]|2|[3-6][1-5]|70)[\\s.-]?\\d{3,4}[\\s.-]?\\d{4}(?!\\d)","flags":"","digits":null}]};

/** Texts and what personal() and contact() say of them (null: it goes). */
export const TEXT_RULE_SAMPLES = [
  {"text":"\uc57c\uac04 \ub05d\ub098\uace0 \ub2e4\ub4e4 \ubb50 \ub4dc\uc138\uc694?","personal":null,"contact":null},
  {"text":"\uc8fc\uac04 08:00~20:00 \u00b7 \uc57c\uac04 20:00~08:00","personal":null,"contact":null},
  {"text":"\uae30\ubcf8\uae09\uc758 10%, \uc57c\uac04\uc740 1.5\ubc30\uc608\uc694","personal":null,"contact":null},
  {"text":"2026-11-13\ubd80\ud130 \ubc14\ub00c\uc5b4\uc694","personal":null,"contact":null},
  {"text":"\uc774\ubc88 \ub2ec \uc57c\uac04 \uc218\ub2f9 32\ub9cc \uc6d0 \ubc1b\uc558\uc5b4\uc694","personal":"amount","contact":null},
  {"text":"\uc5f0\ubd09 300000000\uc6d0","personal":"amount","contact":null},
  {"text":"3\uc5b5 = 300000000\uc6d0","personal":"amount","contact":null},
  {"text":"\uc2dc\uae09 10,030\uc6d0","personal":"amount","contact":null},
  {"text":"\ubb38\uc758\ub294 010-1234-5678\ub85c","personal":"phone","contact":"phone"},
  {"text":"02 123 4567\ub85c \uc804\ud654 \uc8fc\uc138\uc694","personal":"phone","contact":"phone"},
  {"text":"+82 10 1234 5678","personal":"phone","contact":"phone"},
  {"text":"\uba54\uc77c a.b@example.com","personal":"email","contact":"email"},
  {"text":"110-123-456789 \ub85c \ubcf4\ub0b4 \uc8fc\uc138\uc694","personal":"account","contact":"account"},
  {"text":"3333-01-2345678 \uce74\uce74\uc624","personal":"account","contact":"account"},
  {"text":"\uce74\ub4dc 1234-5678-9012-3456","personal":"account","contact":"account"},
  {"text":"\uad6d\ubbfc\uc740\ud589 123456 78 901234","personal":"account","contact":"account"},
  {"text":"\uacc4\uc88c 12345678901 \uc785\ub2c8\ub2e4","personal":"account","contact":"account"},
  {"text":"\uc785\uae08\uc740 \uc6b0\ub9ac 1002 123 456789","personal":"account","contact":"account"},
  {"text":"2026-11-13~2026-12-13","personal":null,"contact":null},
  {"text":"82-10-1234-5678","personal":"phone","contact":"phone"},
  {"text":"031-123-4567","personal":"phone","contact":"phone"},
  {"text":"\uc0ac\ubc88 12345","personal":"amount","contact":null},
  {"text":"1\uc870 2\uc870 3\uc870 4\uc870","personal":null,"contact":null},
];
