<script>
  import { onMount } from 'svelte'

  const REPO = 'Bharathsencha/Gecko'
  const RELEASES = `https://github.com/${REPO}/releases/latest`

  // loading | ready | error
  let state = $state('loading')
  let release = $state(null)

  onMount(async () => {
    try {
      const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`)
      if (!res.ok) throw new Error(String(res.status))
      const data = await res.json()
      const apk = data.assets.find((a) => a.name.endsWith('.apk'))
      if (!apk) throw new Error('no apk')
      release = {
        version: data.tag_name.replace(/^v/, ''),
        url: apk.browser_download_url,
        size: (apk.size / 1048576).toFixed(0),
        sha: apk.digest ? apk.digest.replace('sha256:', '') : null
      }
      state = 'ready'
    } catch {
      state = 'error'
    }
  })

  const features = [
    'Ad and tracker blocking with uBlock Origin built in',
    'Anti-fingerprinting and encrypted DNS',
    'Tor and free-relay VPN',
    'Private tabs that leave nothing on the phone',
    'Password vault protected by the Android Keystore',
    'Video and audio downloader',
    'No accounts, no analytics, no ads'
  ]
</script>

<header class="bar">
  <a class="brand" href="./">
    <img src="./logo.png" alt="" width="40" height="40" />
    <span>Gecko</span>
  </a>
  <nav>
    <a href="https://github.com/{REPO}">Source</a>
    <a href="https://github.com/{REPO}/blob/main/PRIVACY.md">Privacy</a>
  </nav>
</header>

<main>
  <section class="hero">
    <h1>A private browser for Android</h1>
    <p>Built on Mozilla GeckoView. Free and open source.</p>

    {#if state === 'ready'}
      <a class="btn" href={release.url}>Download Gecko {release.version}</a>
      <p class="meta">APK, {release.size} MB. Android 8.0 or newer, 64-bit ARM.</p>
      {#if release.sha}
        <p class="meta sha">SHA-256 <code>{release.sha}</code></p>
      {/if}
    {:else if state === 'loading'}
      <span class="btn busy" aria-busy="true">Checking latest version</span>
    {:else}
      <a class="btn" href={RELEASES}>Open the latest release</a>
      <p class="meta">Could not reach GitHub to look up the version. The release page has the APK.</p>
    {/if}
  </section>

  <section>
    <h2>What it does</h2>
    <ul class="list">
      {#each features as f}
        <li>{f}</li>
      {/each}
    </ul>
  </section>

  <section>
    <h2>Install</h2>
    <ol class="list steps">
      <li>Download the APK on your phone.</li>
      <li>Open it. If Android asks, allow installs from your browser or file manager.</li>
      <li>Open Gecko and set it as your default browser if you like.</li>
    </ol>
  </section>

  <section>
    <h2>License</h2>
    <p>
      Gecko is free software under the
      <a href="https://github.com/{REPO}/blob/main/LICENSE">GNU General Public License v3.0 or later</a>.
      You can use, study, change and share it. The
      <a href="https://github.com/{REPO}">source code</a> is on GitHub.
    </p>
  </section>
</main>

<footer>
  <p>Copyright 2024-2026 Gibbly Gobbly. Gecko is not affiliated with Mozilla, the Tor Project or any project it builds on. The typeface is Space Grotesk (<a href="./licenses/SpaceGrotesk-OFL.txt">OFL 1.1</a>).</p>
</footer>

<style>
  .bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 12px 16px;
    border-bottom: 2px solid var(--ink);
    background: var(--surface);
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 20px;
    font-weight: 700;
    text-decoration: none;
  }
  .brand img {
    border: 2px solid var(--ink);
    border-radius: var(--radius);
    background: #f4f1ea;
  }
  nav { display: flex; gap: 4px; }
  nav a {
    display: inline-flex;
    align-items: center;
    min-height: 48px;
    padding: 0 10px;
    font-weight: 600;
  }

  main, footer {
    width: 100%;
    max-width: 680px;
    margin: 0 auto;
    padding: 0 16px;
  }

  .hero { padding: 48px 0 40px; }
  h1 { margin: 0 0 8px; font-size: 36px; line-height: 1.15; }
  .hero > p { margin: 0 0 24px; font-size: 18px; color: var(--muted); }

  .btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 52px;
    padding: 0 22px;
    background: var(--accent);
    color: var(--on-accent);
    border: 2px solid var(--ink);
    border-radius: var(--radius);
    box-shadow: 4px 4px 0 var(--ink);
    font-size: 17px;
    font-weight: 700;
    text-decoration: none;
    transition: transform 60ms, box-shadow 60ms;
  }
  a.btn:active { transform: translate(4px, 4px); box-shadow: 0 0 0 var(--ink); }
  .btn.busy { background: var(--surface); color: var(--ink); box-shadow: none; }

  .hero > .meta { margin: 20px 0 0; font-size: 14px; color: var(--muted); }
  .hero > .meta.sha { margin-top: 6px; }
  code { font-family: ui-monospace, 'SFMono-Regular', Menlo, monospace; font-size: 12px; word-break: break-all; }

  section { padding: 24px 0; border-top: 2px solid var(--ink); }
  .hero { border-top: 0; }
  h2 { margin: 0 0 12px; font-size: 20px; }
  section p { margin: 0; }

  .list { margin: 0; padding: 0; list-style: none; }
  .list li { padding: 10px 0; border-bottom: 1px solid var(--muted); }
  .list li:last-child { border-bottom: 0; }
  .steps { counter-reset: step; }
  .steps li { counter-increment: step; display: flex; gap: 12px; }
  .steps li::before {
    content: counter(step);
    flex: none;
    width: 26px;
    height: 26px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: var(--accent);
    color: var(--on-accent);
    border: 2px solid var(--ink);
    border-radius: 4px;
    font-size: 13px;
    font-weight: 700;
  }

  footer { padding-top: 24px; padding-bottom: 40px; border-top: 2px solid var(--ink); font-size: 13px; color: var(--muted); }
  footer p { margin: 0; }
  footer a { color: var(--muted); }

  @media (max-width: 420px) {
    h1 { font-size: 30px; }
    .btn { width: 100%; }
  }
</style>
