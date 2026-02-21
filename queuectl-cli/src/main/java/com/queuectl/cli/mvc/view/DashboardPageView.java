package com.queuectl.cli.mvc.view;

public final class DashboardPageView {

  public String render(long refreshMs) {
    return """
          <!doctype html>
          <html lang="en">
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <title>queuectl dashboard</title>
            <style>
              @import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;600;700&family=IBM+Plex+Mono:wght@400;600&display=swap');
              :root {
                --bg: #f7f2e8;
                --card: #fffaf2;
                --ink: #1f1f1f;
                --muted: #6b6459;
                --accent: #0f766e;
                --warn: #b45309;
                --error: #b91c1c;
                --ok: #166534;
                --line: #e6d7bf;
                --shadow: 0 14px 30px rgba(25, 20, 10, 0.08);
              }
              * { box-sizing: border-box; }
              body {
                margin: 0;
                color: var(--ink);
                font-family: 'Space Grotesk', sans-serif;
                background: radial-gradient(circle at 85% 0%, #f7d8a6 0%, transparent 30%),
                            radial-gradient(circle at 0% 100%, #bce7d5 0%, transparent 35%),
                            var(--bg);
              }
              .wrap {
                max-width: 1200px;
                margin: 0 auto;
                padding: 20px;
              }
              .hero {
                background: linear-gradient(135deg, #fff8ef, #f8f0e1);
                border: 1px solid var(--line);
                border-radius: 20px;
                box-shadow: var(--shadow);
                padding: 20px;
                margin-bottom: 18px;
              }
              .title {
                margin: 0;
                font-size: clamp(24px, 4vw, 34px);
              }
              .sub {
                margin: 6px 0 0;
                color: var(--muted);
                font-size: 14px;
              }
              .cards {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
                gap: 12px;
                margin-bottom: 16px;
              }
              .card {
                background: var(--card);
                border: 1px solid var(--line);
                border-radius: 14px;
                box-shadow: var(--shadow);
                padding: 12px;
                transition: transform .18s ease;
              }
              .card:hover { transform: translateY(-2px); }
              .k { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: .05em; }
              .v { font-size: 24px; font-weight: 700; margin-top: 4px; }
              .grid {
                display: grid;
                grid-template-columns: 1fr;
                gap: 16px;
              }
              .panel {
                background: var(--card);
                border: 1px solid var(--line);
                border-radius: 14px;
                box-shadow: var(--shadow);
                padding: 14px;
                overflow: auto;
              }
              h2 {
                margin: 0 0 10px;
                font-size: 17px;
              }
              table {
                width: 100%;
                border-collapse: collapse;
                min-width: 760px;
              }
              th, td {
                text-align: left;
                padding: 9px 10px;
                border-bottom: 1px solid var(--line);
                font-size: 13px;
                vertical-align: top;
              }
              th { color: var(--muted); font-weight: 600; }
              code { font-family: 'IBM Plex Mono', monospace; font-size: 12px; }
              .pill {
                display: inline-flex;
                align-items: center;
                border-radius: 999px;
                padding: 2px 9px;
                font-size: 11px;
                font-weight: 700;
                text-transform: uppercase;
                letter-spacing: .04em;
              }
              .state-completed { background: #dcfce7; color: var(--ok); }
              .state-processing, .state-leased { background: #cffafe; color: var(--accent); }
              .state-pending { background: #fef3c7; color: var(--warn); }
              .state-failed, .state-dead, .state-canceled { background: #fee2e2; color: var(--error); }
              .muted { color: var(--muted); }
              .filters {
                display: flex;
                gap: 10px;
                flex-wrap: wrap;
                margin-bottom: 10px;
              }
              input, select {
                border: 1px solid var(--line);
                border-radius: 10px;
                padding: 8px 10px;
                font-family: inherit;
                background: #fff;
              }
              @media (max-width: 900px) {
                .wrap { padding: 14px; }
                .hero { border-radius: 14px; }
              }
            </style>
          </head>
          <body>
            <div class="wrap">
              <section class="hero">
                <h1 class="title">queuectl dashboard</h1>
                <p class="sub">Live view of running and historical jobs. Auto refresh every <span id="refresh"></span> ms.</p>
              </section>

              <section class="cards" id="cards"></section>

              <section class="panel">
                <h2>Jobs</h2>
                <div class="filters">
                  <select id="stateFilter">
                    <option value="">all states</option>
                    <option value="pending">pending</option>
                    <option value="leased">leased</option>
                    <option value="processing">processing</option>
                    <option value="completed">completed</option>
                    <option value="failed">failed</option>
                    <option value="dead">dead</option>
                    <option value="canceled">canceled</option>
                  </select>
                  <input id="queueFilter" placeholder="queue filter" />
                </div>
                <table>
                  <thead>
                    <tr>
                      <th>id</th>
                      <th>queue</th>
                      <th>state</th>
                      <th>attempts</th>
                      <th>run_at</th>
                      <th>command</th>
                      <th>error</th>
                    </tr>
                  </thead>
                  <tbody id="jobsBody"></tbody>
                </table>
              </section>
            </div>
            <script>
              const refreshMs = __REFRESH_MS__;
              document.getElementById('refresh').textContent = String(refreshMs);
              const cards = document.getElementById('cards');
              const jobsBody = document.getElementById('jobsBody');
              const stateFilter = document.getElementById('stateFilter');
              const queueFilter = document.getElementById('queueFilter');

              function esc(value) {
                return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
              }

              function card(label, value) {
                return `<article class=\"card\"><div class=\"k\">${esc(label)}</div><div class=\"v\">${esc(value)}</div></article>`;
              }

              function stateClass(state) {
                return `state-${state}`;
              }

              async function loadStatus() {
                const r = await fetch('/api/status');
                const data = await r.json();
                cards.innerHTML =
                  card('active workers', data.activeWorkers) +
                  card('pending', data.counts.pending) +
                  card('leased', data.counts.leased) +
                  card('processing', data.counts.processing) +
                  card('completed', data.counts.completed) +
                  card('failed', data.counts.failed) +
                  card('dead', data.counts.dead) +
                  card('canceled', data.counts.canceled) +
                  card('dlq', data.dlqSize);
              }

              async function loadJobs() {
                const q = new URLSearchParams();
                if (stateFilter.value) q.set('state', stateFilter.value);
                if (queueFilter.value.trim()) q.set('queue', queueFilter.value.trim());
                q.set('limit', '500');
                const r = await fetch('/api/jobs?' + q.toString());
                const data = await r.json();
                jobsBody.innerHTML = data.jobs.map(job => `
                  <tr>
                    <td><code>${esc(job.id)}</code></td>
                    <td>${esc(job.queue)}</td>
                    <td><span class=\"pill ${stateClass(job.state)}\">${esc(job.state)}</span></td>
                    <td>${esc(job.attempts)}/${esc(job.maxRetries)}</td>
                    <td class=\"muted\">${esc(job.runAt)}</td>
                    <td><code>${esc(job.command)} ${esc((job.args || []).join(' '))}</code></td>
                    <td class=\"muted\">${esc(job.lastError || '')}</td>
                  </tr>
                `).join('');
              }

              async function tick() {
                try {
                  await Promise.all([loadStatus(), loadJobs()]);
                } catch (e) {
                  console.error(e);
                }
              }

              stateFilter.addEventListener('change', tick);
              queueFilter.addEventListener('input', () => setTimeout(tick, 0));

              tick();
              setInterval(tick, refreshMs);
            </script>
          </body>
          </html>
          """
        .replace("__REFRESH_MS__", Long.toString(refreshMs));
  }
}
