/* Executed only by an explicit tap on the visible, authenticated Claude WebView.
 * No native bridge, token extraction, header spoofing or challenge bypass. */
(function () {
  'use strict';
  const key = __RESULT_KEY__;
  if (location.origin !== 'https://claude.ai' || window !== window.top) return;
  window[key] = null;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 40000);
  const text = (v, fallback) => typeof v === 'string' ? v.slice(0, 160) : fallback;
  const lane = v => v && typeof v === 'object' ? {
    utilization: typeof v.utilization === 'number' && Number.isFinite(v.utilization) && v.utilization >= 0 && v.utilization <= 100 ? v.utilization : null,
    resets_at: text(v.resets_at, null)
  } : null;
  async function get(path) {
    if (location.origin !== 'https://claude.ai') throw new Error('Origin changed');
    const r = await fetch(path, {credentials: 'include', mode: 'same-origin', redirect: 'error',
      headers: {Accept: 'application/json'}, signal: controller.signal});
    if (!r.ok) throw new Error('HTTP ' + r.status);
    if (!(r.headers.get('content-type') || '').includes('application/json')) throw new Error('Non-JSON response');
    return r.json();
  }
  (async function () {
    try {
      const orgs = await get('/api/organizations');
      if (!Array.isArray(orgs) || orgs.length > 200) throw new Error('Unexpected organization response');
      const rows = [];
      const seen = new Set();
      for (const org of orgs) {
        if (!org || typeof org.uuid !== 'string' || !/^[a-zA-Z0-9-]{1,100}$/.test(org.uuid) || seen.has(org.uuid)) continue;
        seen.add(org.uuid);
        const row = {id: 'claude:' + org.uuid, provider: 'claude', name: 'Claude · ' + text(org.name, 'Workspace')};
        const plan = text(org.rate_limit_tier, text(org.subscription_type, ''));
        if (plan) row.plan = plan;
        try {
          const u = await get('/api/organizations/' + encodeURIComponent(org.uuid) + '/usage');
          if (!u || Array.isArray(u) || typeof u !== 'object' || (!('five_hour' in u) && !('seven_day' in u))) throw new Error('Unexpected usage response');
          row.session = lane(u.five_hour);
          row.weekly = lane(u.seven_day);
          if (!row.session && !row.weekly) throw new Error('Usage unavailable for this workspace');
          row.updated = Date.now();
          const e = u.extra_usage;
          if (e && e.is_enabled && typeof e.monthly_limit === 'number' && typeof e.used_credits === 'number' && Number.isFinite(e.monthly_limit) && Number.isFinite(e.used_credits) && e.monthly_limit >= 0 && e.used_credits >= 0) {
            row.extra = 'Extra usage budget left: ' + (Math.max(0, e.monthly_limit - e.used_credits) / 100).toFixed(2) + ' ' + text(e.currency, 'USD');
          }
        } catch (e) {
          row.error = 'Stale · ' + safeError(e);
        }
        rows.push(row);
      }
      if (orgs.length && !rows.length) throw new Error('Unexpected organization response');
      window[key] = {rows: rows};
    } catch (e) { window[key] = {error: safeError(e)}; }
    finally { clearTimeout(timer); }
  })();
  function safeError(e) {
    const message = e && e.message;
    return /^(HTTP [0-9]{3}|Non-JSON response|Unexpected organization response|Unexpected usage response|Usage unavailable for this workspace|Origin changed)$/.test(message) ? message : 'Network error or timeout · retry on the Claude usage page';
  }
})();
