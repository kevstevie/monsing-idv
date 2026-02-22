/**
 * k6 handleSummary 공통 헬퍼
 * 응답 지표 + 임계값 pass/fail 결과를 터미널에 출력
 */

function ms(val) {
  return (val !== undefined && val !== null) ? (val.toFixed(0) + 'ms') : '-';
}

function pct(val) {
  return (val !== undefined && val !== null) ? ((val * 100).toFixed(2) + '%') : '-';
}

function rps(val) {
  return (val !== undefined && val !== null) ? (val.toFixed(1) + '/s') : '-';
}

export function buildSummary(data, scriptName) {
  var LINE = '─'.repeat(66);
  var out  = [];

  out.push('');
  out.push('  ┌' + '─'.repeat(64) + '┐');
  out.push('  │  ' + (scriptName || '테스트 결과').slice(0, 61).padEnd(61) + '│');
  out.push('  └' + '─'.repeat(64) + '┘');

  // ── HTTP 지표
  var dur  = data.metrics['http_req_duration'];
  var fail = data.metrics['http_req_failed'];
  var reqs = data.metrics['http_reqs'];

  if (dur && dur.values) {
    out.push('');
    out.push('  [HTTP 응답 시간]');
    out.push(
      '    avg=' + ms(dur.values.avg) +
      '  p90=' + ms(dur.values['p(90)']) +
      '  p95=' + ms(dur.values['p(95)']) +
      '  p99=' + ms(dur.values['p(99)']) +
      '  max=' + ms(dur.values.max)
    );
  }

  if (fail && fail.values) {
    out.push('');
    out.push('  [에러율]');
    out.push(
      '    실패율=' + pct(fail.values.rate) +
      '  (성공 ' + fail.values.passes + '건 / 실패 ' + fail.values.fails + '건)'
    );
  }

  if (reqs && reqs.values) {
    out.push('  [처리량]');
    out.push(
      '    총 요청=' + reqs.values.count + '건' +
      '  RPS=' + rps(reqs.values.rate)
    );
  }

  // ── WebSocket 지표 (존재할 때만)
  var wsCon  = data.metrics['ws_connecting'];
  var wsDur  = data.metrics['ws_session_duration'];
  var wsSent = data.metrics['ws_msgs_sent'];

  if (wsCon && wsCon.values) {
    out.push('');
    out.push('  [WebSocket]');
    out.push(
      '    연결시간 p95=' + ms(wsCon.values['p(95)']) +
      '  세션시간 p95=' + ms(wsDur && wsDur.values ? wsDur.values['p(95)'] : null) +
      '  전송=' + (wsSent && wsSent.values ? wsSent.values.count : 0) + '건'
    );
  }

  // ── 임계값 결과
  var rows = [];
  for (var name in data.metrics) {
    var metric = data.metrics[name];
    if (!metric.thresholds) continue;
    for (var cond in metric.thresholds) {
      rows.push({ name: name, cond: cond, ok: metric.thresholds[cond].ok });
    }
  }

  if (rows.length > 0) {
    out.push('');
    out.push('  ' + LINE);
    out.push('  임계값 결과');
    out.push('  ' + LINE);

    for (var i = 0; i < rows.length; i++) {
      var row  = rows[i];
      var icon = row.ok ? '  ✓ PASS' : '  ✗ FAIL';
      out.push(icon + '  ' + row.name + '  »  ' + row.cond);
    }

    var passed = rows.filter(function(r) { return r.ok; }).length;
    var failed = rows.filter(function(r) { return !r.ok; }).length;

    out.push('  ' + LINE);
    out.push(
      '  통과 ' + passed + '개 / 실패 ' + failed + '개' +
      '   ' + (failed === 0 ? '✓  전체 통과' : '✗  임계값 위반 있음')
    );
    out.push('  ' + LINE);
  }

  out.push('');
  return out.join('\n');
}

/**
 * @param {string} scriptName 스크립트 표시 이름
 * @returns {function} handleSummary 함수
 */
export function makeSummaryHandler(scriptName) {
  return function(data) {
    return { stdout: buildSummary(data, scriptName) };
  };
}
