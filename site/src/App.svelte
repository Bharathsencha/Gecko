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

  // Material Icons paths (Apache 2.0)
  const features = [
    { title: 'Ad and tracker blocking', text: 'uBlock Origin built in, plus a native request filter.', color: '#ffd84d',
      d: 'M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z' },
    { title: 'Anti-fingerprinting', text: 'Uniform user agent, screen and canvas. WebRTC never leaks your address.', color: '#7fd8c8',
      d: 'M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z' },
    { title: 'Tor and relay VPN', text: 'An embedded Tor client or free SOCKS5 relays. Both fail closed.', color: '#8ec5ff',
      d: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z' },
    { title: 'Private tabs', text: 'Kept in memory only. Nothing saved, screenshots blocked.', color: '#ff9ec7',
      d: 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM9 8V6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9z' },
    { title: 'Password vault', text: 'AES-256-GCM with the key in the Android Keystore. Opens with fingerprint or passcode.', color: '#b49cff',
      d: 'M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z' },
    { title: 'Video and audio downloader', text: 'Save media from the page you are on, remuxed on the phone.', color: '#ffb067',
      d: 'M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z' }
  ]

  const steps = [
    'Download the APK on your phone.',
    'Open it and allow installs from your browser or file manager if Android asks.',
    'Open Gecko. Set it as the default browser if you like.'
  ]
</script>

<header class="bar">
  <a class="brand" href="./">
    <img src="./logo.png" alt="" width="44" height="44" />
    <span>Gecko</span>
  </a>
  <nav>
    <a class="chip" href="https://github.com/{REPO}">Source</a>
    <a class="chip" href="https://github.com/{REPO}/blob/main/PRIVACY.md">Privacy</a>
  </nav>
</header>

<main>
  <section class="hero">
    <div class="hero-text">
      <h1>A <mark>private</mark> browser for Android</h1>
      <p class="lead">Built on Mozilla GeckoView. Free and open source.</p>

      {#if state === 'ready'}
        <a class="btn" href={release.url}>
          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z" /></svg>
          Download {release.version}
        </a>
        <ul class="facts">
          <li>{release.size} MB</li>
          <li>Android 8.0+</li>
          <li>64-bit ARM</li>
        </ul>
        {#if release.sha}
          <details>
            <summary>SHA-256 checksum</summary>
            <code>{release.sha}</code>
          </details>
        {/if}
      {:else if state === 'loading'}
        <span class="btn busy" aria-busy="true">Checking latest version</span>
      {:else}
        <a class="btn" href={RELEASES}>Open the latest release</a>
        <p class="note">Could not reach GitHub to look up the version. The release page has the APK.</p>
      {/if}
    </div>

    <img class="shot" src="./screenshot.png" alt="Gecko start page with favourites" width="270" height="537" />
  </section>

  <section>
    <h2>What it does</h2>
    <div class="grid">
      {#each features as f}
        <article class="tile" style="--tile: {f.color}">
          <svg viewBox="0 0 24 24" width="28" height="28" aria-hidden="true"><path d={f.d} /></svg>
          <h3>{f.title}</h3>
          <p>{f.text}</p>
        </article>
      {/each}
    </div>
    <p class="plain">No accounts. No analytics. No ads.</p>
  </section>

  <section>
    <h2>Install</h2>
    <ol class="steps">
      {#each steps as s}
        <li>{s}</li>
      {/each}
    </ol>
  </section>
</main>

<section class="license">
  <div>
    <h2>Free software, GPL v3</h2>
    <p>
      Gecko is released under the
      <a href="https://github.com/{REPO}/blob/main/LICENSE">GNU General Public License v3.0 or later</a>.
      You can use, study, change and share it.
      <a href="https://github.com/{REPO}">Read the source on GitHub.</a>
    </p>
  </div>
</section>

<footer>
  <p>Copyright 2024-2026 Gibbly Gobbly. Gecko is not affiliated with Mozilla, the Tor Project or any project it builds on. Typeface Space Grotesk (<a href="./licenses/SpaceGrotesk-OFL.txt">OFL 1.1</a>).</p>
</footer>

<style>
  .bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 16px;
    border-bottom: 2px solid var(--ink);
    background: var(--surface);
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 22px;
    font-weight: 700;
    text-decoration: none;
  }
  .brand img {
    border: 2px solid var(--ink);
    border-radius: var(--radius);
    background: #f4f1ea;
    box-shadow: 3px 3px 0 var(--ink);
  }
  nav { display: flex; gap: 10px; }
  .chip {
    display: inline-flex;
    align-items: center;
    min-height: 44px;
    padding: 0 14px;
    background: var(--paper);
    border: 2px solid var(--ink);
    border-radius: var(--radius);
    box-shadow: 3px 3px 0 var(--ink);
    font-weight: 700;
    font-size: 14px;
    text-decoration: none;
  }
  .chip:active { transform: translate(3px, 3px); box-shadow: none; }

  main, footer, .license > div {
    width: 100%;
    max-width: 960px;
    margin: 0 auto;
    padding: 0 16px;
  }

  .hero {
    display: grid;
    grid-template-columns: 1fr;
    gap: 40px;
    align-items: center;
    padding: 40px 0 56px;
  }
  h1 { margin: 0 0 12px; font-size: 40px; line-height: 1.1; letter-spacing: -0.5px; }
  mark {
    background: var(--accent);
    color: var(--on-accent);
    padding: 0 8px;
    border: 2px solid var(--ink);
    border-radius: 4px;
    box-decoration-break: clone;
    -webkit-box-decoration-break: clone;
  }
  .lead { margin: 0 0 28px; font-size: 18px; color: var(--muted); }

  .btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    min-height: 60px;
    padding: 0 28px;
    background: var(--accent);
    color: var(--on-accent);
    border: 2px solid var(--ink);
    border-radius: var(--radius);
    box-shadow: 6px 6px 0 var(--ink);
    font-size: 20px;
    font-weight: 700;
    text-decoration: none;
    transition: transform 60ms, box-shadow 60ms;
  }
  .btn svg { fill: var(--on-accent); }
  a.btn:active { transform: translate(6px, 6px); box-shadow: 0 0 0 var(--ink); }
  .btn.busy { background: var(--surface); color: var(--ink); box-shadow: none; }

  .facts { display: flex; flex-wrap: wrap; gap: 8px; margin: 22px 0 0; padding: 0; list-style: none; }
  .facts li {
    padding: 4px 10px;
    border: 2px solid var(--ink);
    border-radius: 4px;
    background: var(--surface);
    font-size: 13px;
    font-weight: 700;
  }
  details { margin-top: 14px; font-size: 14px; color: var(--muted); }
  summary { cursor: pointer; min-height: 32px; font-weight: 600; }
  code { display: block; margin-top: 6px; font-family: ui-monospace, Menlo, monospace; font-size: 12px; word-break: break-all; }
  .note { margin: 16px 0 0; font-size: 14px; color: var(--muted); }

  .shot {
    justify-self: center;
    width: 270px;
    max-width: 70%;
    height: auto;
    border: 2px solid var(--ink);
    border-radius: var(--radius);
    box-shadow: 8px 8px 0 var(--accent), 8px 8px 0 2px var(--ink);
  }

  section { padding: 0 0 48px; }
  .hero { padding-bottom: 56px; }
  h2 { margin: 0 0 20px; font-size: 26px; }

  .grid { display: grid; grid-template-columns: 1fr; gap: 20px; }
  .tile {
    padding: 18px;
    background: var(--tile);
    color: #111;
    border: 2px solid var(--ink);
    border-radius: var(--radius);
    box-shadow: 5px 5px 0 var(--ink);
  }
  .tile svg { display: block; fill: #111; margin-bottom: 10px; }
  .tile h3 { margin: 0 0 4px; font-size: 18px; }
  .tile p { margin: 0; font-size: 15px; line-height: 1.4; }
  .plain { margin: 28px 0 0; font-size: 18px; font-weight: 700; }

  .steps { margin: 0; padding: 0; list-style: none; counter-reset: s; display: grid; gap: 14px; }
  .steps li {
    counter-increment: s;
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 14px;
    background: var(--surface);
    border: 2px solid var(--ink);
    border-radius: var(--radius);
  }
  .steps li::before {
    content: counter(s);
    flex: none;
    width: 34px;
    height: 34px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: var(--accent);
    color: var(--on-accent);
    border: 2px solid var(--ink);
    border-radius: 4px;
    font-weight: 700;
  }

  .license {
    padding: 40px 0;
    background: var(--accent);
    color: #111;
    border-top: 2px solid var(--ink);
    border-bottom: 2px solid var(--ink);
  }
  .license h2 { margin-bottom: 10px; }
  .license p { margin: 0; max-width: 620px; }
  .license a { color: #111; font-weight: 700; }

  footer { padding-top: 24px; padding-bottom: 40px; font-size: 13px; color: var(--muted); }
  footer p { margin: 0; }
  footer a { color: var(--muted); }

  @media (min-width: 760px) {
    .hero { grid-template-columns: 1.2fr 1fr; }
    h1 { font-size: 56px; }
    .grid { grid-template-columns: 1fr 1fr; }
    .steps { grid-template-columns: repeat(3, 1fr); }
    .steps li { flex-direction: column; align-items: flex-start; }
  }
  @media (max-width: 480px) {
    .btn { width: 100%; }
  }
</style>
