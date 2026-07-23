import React, { useEffect, useState } from 'react';
import GradientGenerator from './components/GradientGenerator';
import PersonaSelector from './components/PersonaSelector';

const BUILT_IN_API = import.meta.env.VITE_API_BASE || '';

// ─── Helpers ────────────────────────────────────────────────────────────────

function getStoredApiBase() {
  try { return localStorage.getItem('alchemyApiBase') || ''; } catch { return ''; }
}
function setStoredApiBase(v) {
  try { if (v) localStorage.setItem('alchemyApiBase', v); } catch {}
}
function getStoredDiscordSession() {
  try { return localStorage.getItem('alchemyDiscordSession') || ''; } catch { return ''; }
}
function setStoredDiscordSession(v) {
  try {
    if (v) localStorage.setItem('alchemyDiscordSession', v);
    else localStorage.removeItem('alchemyDiscordSession');
  } catch {}
}
function resolveApi(param) {
  return param || BUILT_IN_API || getStoredApiBase();
}
function buildApiUrl(base, path) {
  if (!base) return '';
  const cleanBase = base.replace(/\/+$/, '');
  const cleanPath = path.startsWith('/') ? path : `/${path}`;
  if (cleanBase.endsWith('/persona') || cleanBase.endsWith('/api/nickname')) {
    return cleanBase + cleanPath;
  }
  return cleanBase + '/persona' + cleanPath;
}

function stripTags(nick) {
  if (!nick) return '';
  return nick
    .replace(/<[^>]+>/g, '')
    .replace(/&#[0-9A-Fa-f]{6}/g, '')
    .replace(/&[0-9a-fk-or]/gi, '')
    .trim();
}

const makeStop = (hex, pos) => ({ id: Math.random().toString(36).slice(2), hex, pos });

function parseNickname(nick) {
  if (!nick) return null;
  const entries = [];
  let i = 0;
  while (i < nick.length) {
    const colorTag = nick.slice(i).match(/^<(?:color:|colour:)?(#[A-Fa-f0-9]{6})>/i);
    if (colorTag) {
      const color = colorTag[1].toUpperCase();
      i += colorTag[0].length;
      let modMatch;
      while ((modMatch = nick.slice(i).match(/^<[^>]+>/))) i += modMatch[0].length;
      if (i < nick.length && nick[i] !== '<') entries.push({ color, char: nick[i++] });
      continue;
    }
    if (nick[i] === ' ') { entries.push({ color: null, char: ' ' }); i++; continue; }
    if (nick[i] === '<') { const end = nick.indexOf('>', i); i = end !== -1 ? end + 1 : i + 1; continue; }
    i++;
  }
  if (entries.filter(e => e.color).length < 2) {
    entries.length = 0;
    const leg = /&#([A-Fa-f0-9]{6})((?:&[lnmok])*)([^&<])/g;
    let m;
    while ((m = leg.exec(nick)) !== null) entries.push({ color: '#' + m[1].toUpperCase(), char: m[3] });
  }
  const colored = entries.filter(e => e.color);
  if (colored.length < 2) return null;
  const text = entries.map(e => e.char).join('');
  const n = Math.min(colored.length, 7);
  const stops = Array.from({ length: n }, (_, i) => {
    const idx = Math.round(i / (n - 1) * (colored.length - 1));
    return makeStop(colored[idx].color, Math.round(i / (n - 1) * 100));
  });
  const formats = {
    bold:          /<b>|<bold>|&l/i.test(nick),
    italic:        /<i>|<italic>|&o/i.test(nick),
    underline:     /<u>|<underlined>|<underline>|&n/i.test(nick),
    strikethrough: /<st>|<strikethrough>|&m/i.test(nick),
  };
  return { text, stops, formats };
}

// ─── Toast ────────────────────────────────────────────────────────────────────

function Toast({ message, type = 'success', onDone }) {
  useEffect(() => {
    const t = setTimeout(onDone, 4000);
    return () => clearTimeout(t);
  }, [onDone]);
  const bg = type === 'error' ? 'bg-red-600' : 'bg-green-600';
  return (
    <div className={`fixed top-4 left-1/2 -translate-x-1/2 z-50 px-5 py-3 rounded-lg shadow-xl text-white text-sm font-medium ${bg}`}>
      {message}
    </div>
  );
}

// ─── DiscordButton ────────────────────────────────────────────────────────────

function DiscordButton({ apiBase, label = 'Login with Discord' }) {
  const clientId = '1497614113075892244';
  const redirectUri = encodeURIComponent(window.location.origin + window.location.pathname);
  const url = `https://discord.com/api/oauth2/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=token&scope=identify`;
  return (
    <a
      href={url}
      className="flex items-center justify-center gap-3 w-full py-3 rounded-lg font-bold text-sm text-white transition-all hover:brightness-110"
      style={{ background: '#5865F2' }}
    >
      <svg width="20" height="20" viewBox="0 0 71 55" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M60.1045 4.8978C55.5792 2.8214 50.7265 1.2916 45.6527 0.41542C45.5603 0.39851 45.468 0.440769 45.4204 0.525289C44.7963 1.6353 44.105 3.0834 43.6209 4.2216C38.1637 3.4046 32.7345 3.4046 27.3892 4.2216C26.905 3.0581 26.1886 1.6353 25.5617 0.525289C25.5141 0.443589 25.4218 0.401329 25.3294 0.41542C20.2584 1.2888 15.4057 2.8186 10.8776 4.8978C10.8384 4.9147 10.8048 4.9429 10.7825 4.9795C1.57795 18.7309 -0.943561 32.1443 0.293408 45.3914C0.299005 45.4562 0.335386 45.5182 0.385761 45.5576C6.45866 50.0174 12.3413 52.7249 18.1147 54.5195C18.2071 54.5477 18.305 54.5139 18.3638 54.4378C19.7295 52.5728 20.9469 50.6063 21.9907 48.5383C22.0523 48.4172 21.9935 48.2735 21.8676 48.2256C19.9366 47.4931 18.0979 46.6 16.3292 45.5858C16.1893 45.5041 16.1781 45.304 16.3068 45.2082C16.679 44.9293 17.0513 44.6391 17.4067 44.3461C17.471 44.2926 17.5606 44.2813 17.6362 44.3151C29.2558 49.6202 41.8354 49.6202 53.3179 44.3151C53.3935 44.2785 53.4831 44.2898 53.5502 44.3433C53.9057 44.6363 54.2779 44.9293 54.6529 45.2082C54.7816 45.304 54.7732 45.5041 54.6333 45.5858C52.8646 46.6197 51.0259 47.4931 49.0921 48.2228C48.9662 48.2707 48.9102 48.4172 48.9718 48.5383C50.038 50.6034 51.2554 52.5699 52.5959 54.435C52.6519 54.5139 52.7526 54.5477 52.845 54.5195C58.6464 52.7249 64.529 50.0174 70.6019 45.5576C70.6551 45.5182 70.6887 45.459 70.6943 45.3942C72.1747 30.0791 68.2147 16.7757 60.1968 4.9823C60.1772 4.9429 60.1437 4.9147 60.1045 4.8978ZM23.7259 37.3253C20.2276 37.3253 17.3451 34.1136 17.3451 30.1693C17.3451 26.225 20.1717 23.0133 23.7259 23.0133C27.308 23.0133 30.1626 26.2532 30.1066 30.1693C30.1066 34.1136 27.28 37.3253 23.7259 37.3253ZM47.3178 37.3253C43.8196 37.3253 40.9371 34.1136 40.9371 30.1693C40.9371 26.225 43.7636 23.0133 47.3178 23.0133C50.9 23.0133 53.7545 26.2532 53.6986 30.1693C53.6986 34.1136 50.9 37.3253 47.3178 37.3253Z" fill="currentColor"/>
      </svg>
      {label}
    </a>
  );
}

// ─── LinkCodeEntry ────────────────────────────────────────────────────────────

function LinkCodeEntry({ dsession, apiBase, onLinked }) {
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (code.trim().length !== 6) return;
    setLoading(true);
    setError('');
    try {
      const res = await fetch(buildApiUrl(apiBase, '/link-code'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dsession, code: code.trim().toUpperCase() })
      });
      if (res.ok) {
        const data = await res.json();
        onLinked(data); // { uuid, name }
      } else {
        const msg = await res.text();
        setError(msg.includes('expired') ? 'Code expired — run /linkpersona again.' : 'Invalid code. Check the code and try again.');
      }
    } catch {
      setError('Could not reach the server. Try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="birdflop-panel p-8 max-w-md w-full space-y-6">

        <div className="text-center space-y-2">
          <div className="text-4xl mb-2">🔗</div>
          <h1 className="text-2xl font-bold text-white">Link Minecraft Account</h1>
          <p className="text-[var(--text-secondary)] text-sm">
            Your Discord is logged in but not yet linked to a Minecraft account.
          </p>
        </div>

        <div className="birdflop-panel p-4 space-y-2 bg-black/30">
          <p className="text-sm font-bold text-white">In-game, run:</p>
          <div className="font-mono text-[var(--accent-blue)] text-lg font-bold">/linkpersona</div>
          <p className="text-xs text-[var(--text-secondary)]">
            A 6-character code will appear in chat. Enter it below.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1">
            <label className="text-xs font-medium text-[var(--text-secondary)] uppercase tracking-wide">
              Link Code
            </label>
            <input
              type="text"
              value={code}
              onChange={e => setCode(e.target.value.replace(/[^A-Za-z0-9]/g, '').slice(0, 6))}
              placeholder="A3X9K2"
              className="birdflop-input w-full px-3 py-2 text-sm font-mono tracking-widest text-center text-2xl uppercase"
              maxLength={6}
              autoComplete="off"
              spellCheck="false"
            />
          </div>

          {error && (
            <p className="text-xs text-red-400 text-center">{error}</p>
          )}

          <button
            type="submit"
            disabled={code.trim().length !== 6 || loading}
            className="birdflop-btn-blue w-full py-3 text-sm font-bold disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {loading ? 'Linking…' : 'Link Account'}
          </button>
        </form>

        <div className="border-t border-[var(--border-color)] pt-4 text-center">
          <p className="text-xs text-[var(--text-secondary)]">
            Codes expire after <span className="text-white">10 minutes</span>
          </p>
        </div>
      </div>
    </div>
  );
}

// ─── AccountPicker ────────────────────────────────────────────────────────────

function AccountPicker({ accounts, onPick }) {
  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="birdflop-panel p-8 max-w-md w-full space-y-6">
        <div className="text-center space-y-2">
          <h1 className="text-2xl font-bold text-white">✦ Choose Account</h1>
          <p className="text-[var(--text-secondary)] text-sm">
            Multiple Minecraft accounts are linked to your Discord. Pick one to edit.
          </p>
        </div>
        <div className="space-y-3">
          {accounts.map(acc => (
            <button
              key={acc.uuid}
              onClick={() => onPick(acc)}
              className="birdflop-btn-blue w-full py-3 text-sm font-bold flex items-center justify-between px-4"
            >
              <div className="flex items-center gap-3">
                <img
                  src={`https://crafthead.net/helm/${acc.uuid}/32`}
                  alt={acc.name}
                  className="w-8 h-8 rounded pixelated"
                  onError={e => e.target.style.display = 'none'}
                />
                <span>{acc.name}</span>
              </div>
              <span className="opacity-60 text-xs font-mono">{acc.uuid.slice(0, 8)}…</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── CodeEntryForm ────────────────────────────────────────────────────────────

function CodeEntryForm({ onSubmit, apiBase }) {
  const [name, setName] = useState('');
  const [code, setCode] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    if (name.trim() && code.trim()) onSubmit(name.trim(), code.trim());
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="birdflop-panel p-8 max-w-md w-full space-y-6">

        <div className="text-center space-y-2">
          <h1 className="text-2xl font-bold text-white">✦ Nickname Designer</h1>
          <p className="text-[var(--text-secondary)] text-sm">
            Sign in with Discord or use a session code from in-game.
          </p>
        </div>

        {apiBase && (
          <>
            <DiscordButton apiBase={apiBase} />
            <div className="flex items-center gap-3">
              <div className="flex-1 h-px bg-[var(--border-color)]"></div>
              <span className="text-xs text-[var(--text-secondary)]">or use a session code</span>
              <div className="flex-1 h-px bg-[var(--border-color)]"></div>
            </div>
          </>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1">
            <label className="text-xs font-medium text-[var(--text-secondary)] uppercase tracking-wide">
              Minecraft Username
            </label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="Steve"
              className="birdflop-input w-full px-3 py-2 text-sm"
              autoComplete="off"
              spellCheck="false"
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-[var(--text-secondary)] uppercase tracking-wide">
              Session Code
            </label>
            <input
              type="text"
              value={code}
              onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 5))}
              placeholder="12345"
              className="birdflop-input w-full px-3 py-2 text-sm font-mono tracking-widest text-center text-lg"
              maxLength={5}
              inputMode="numeric"
            />
            <p className="text-xs text-[var(--text-secondary)]">
              Run <span className="font-mono text-[var(--accent-blue)]">/nicknameeditor</span> in-game to get a code
            </p>
          </div>

          <button
            type="submit"
            disabled={name.trim().length < 1 || code.trim().length !== 5}
            className="birdflop-btn-blue w-full py-3 text-sm font-bold disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Open Designer
          </button>
        </form>

        <div className="border-t border-[var(--border-color)] pt-4 text-center">
          <p className="text-xs text-[var(--text-secondary)]">
            Session codes expire after <span className="text-white">10 minutes</span>
          </p>
        </div>
      </div>
    </div>
  );
}

// ─── App ──────────────────────────────────────────────────────────────────────

function App() {
  const [playerInfo, setPlayerInfo]   = useState(null);
  const [activeTab, setActiveTab]     = useState('nickname');
  const [personaData, setPersonaData] = useState(null);
  const [isLoading, setIsLoading]     = useState(false);
  const [toast, setToast]             = useState(null);
  const [apiBase, setApiBase]         = useState('');

  // ── Discord link-code flow state ─────────────────────────────────────────
  const [discordLinkState, setDiscordLinkState] = useState(null); // { dsession, apiBase }

  const fetchPersonaData = async (info) => {
    if (!info?.apiBase) return;
    setIsLoading(true);
    try {
      const path = info.dsession
        ? `/data?dsession=${info.dsession}&uuid=${info.uuid}`
        : `/data?token=${info.token}`;
      const res = await fetch(buildApiUrl(info.apiBase, path));
      if (res.ok) setPersonaData(await res.json());
    } catch (e) {
      console.error('Failed to fetch persona data:', e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    const initAuth = async () => {
      const params   = new URLSearchParams(window.location.search);
      const hashParams = new URLSearchParams(window.location.hash.substring(1));
      const accessToken = hashParams.get('access_token');
      
      const player   = params.get('player')   || '';
      const token    = params.get('token')    || '';
      const apiParam = params.get('api')      || '';
      let dsession = params.get('dsession') || getStoredDiscordSession();
      const errorFlag = params.get('error')  || '';

      const resolved = resolveApi(apiParam);
      if (apiParam) setStoredApiBase(apiParam);
      setApiBase(resolved);

      if (window.history.replaceState) {
        window.history.replaceState({}, '', window.location.pathname);
      }

      if (accessToken) {
        try {
          setIsLoading(true);
          const userRes = await fetch('https://discord.com/api/v10/users/@me', {
            headers: { Authorization: `Bearer ${accessToken}` }
          });
          if (userRes.ok) {
            const user = await userRes.json();
            dsession = user.id;
            setStoredDiscordSession(dsession);
          }
        } catch (e) {
          console.error('Failed to fetch Discord user profile:', e);
        } finally {
          setIsLoading(false);
        }
      }

      if (errorFlag) {
        const msgs = {
          oauth_failed: 'Discord login failed. Please try again.',
          link_expired: 'Link request expired. Please run /linkpersona again.',
        };
        setToast({ message: msgs[errorFlag] || 'An error occurred.', type: 'error' });
        return;
      }

      if (dsession) {
        if (params.get('dsession')) setStoredDiscordSession(dsession);
        handleDiscordSession(dsession, resolved);
        return;
      }

      if (player && token) {
        const info = { name: player, token, apiBase: resolved };
        setPlayerInfo(info);
        fetchPersonaData(info);
      }
    };

    initAuth();
  }, []);

  const handleDiscordSession = async (dsession, base) => {
    if (!base) {
      setToast({ message: 'Server URL unknown. Please visit via an in-game link first.', type: 'error' });
      return;
    }
    setIsLoading(true);
    try {
      const res = await fetch(buildApiUrl(base, `/accounts?discordId=${dsession}&dsession=${dsession}`));
      if (!res.ok) { 
        setStoredDiscordSession(null);
        setToast({ message: 'Discord session invalid or expired.', type: 'error' }); 
        return; 
      }
      const accounts = await res.json(); // plain array

      if (accounts.length === 0) {
        // Not yet linked — show code entry
        setDiscordLinkState({ dsession, apiBase: base });
      } else if (accounts.length === 1) {
        openDiscordEditor({ dsession, apiBase: base }, accounts[0]);
      } else {
        setPlayerInfo({ _picker: true, dsession, accounts, apiBase: base });
      }
    } catch {
      setToast({ message: 'Failed to reach server.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const openDiscordEditor = (session, acc) => {
    const info = { name: acc.name, uuid: acc.uuid, dsession: session.dsession, discordId: session.dsession, apiBase: session.apiBase };
    setPlayerInfo(info);
    fetchPersonaData(info);
  };

  const handleCodeEntry = (name, code) => {
    const info = { name, token: code, apiBase };
    setPlayerInfo(info);
    fetchPersonaData(info);
  };

  const handleSaveSelection = async (type, id) => {
    if (!playerInfo) return;
    const body = playerInfo.dsession
      ? { dsession: playerInfo.dsession, uuid: playerInfo.uuid, [type]: id }
      : { token: playerInfo.token, [type]: id };
    try {
      const res = await fetch(buildApiUrl(playerInfo.apiBase, '/save'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      if (res.ok) fetchPersonaData(playerInfo);
    } catch (e) {
      console.error('Failed to save selection:', e);
    }
  };

  // ── Link-code entry screen ────────────────────────────────────────────────
  if (discordLinkState) {
    return (
      <>
        {toast && <Toast {...toast} onDone={() => setToast(null)} />}
        <LinkCodeEntry
          dsession={discordLinkState.dsession}
          apiBase={discordLinkState.apiBase}
          onLinked={(acc) => {
            setDiscordLinkState(null);
            setToast({ message: `Linked to ${acc.name}!`, type: 'success' });
            openDiscordEditor(discordLinkState, acc);
          }}
        />
      </>
    );
  }

  // ── Account picker ────────────────────────────────────────────────────────
  if (playerInfo?._picker) {
    return (
      <>
        {toast && <Toast {...toast} onDone={() => setToast(null)} />}
        <AccountPicker
          accounts={playerInfo.accounts}
          onPick={(acc) => openDiscordEditor(playerInfo, acc)}
        />
      </>
    );
  }

  // ── Login screen ──────────────────────────────────────────────────────────
  if (!playerInfo) {
    if (isLoading) {
      return (
        <div className="min-h-screen flex items-center justify-center">
          <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-[var(--accent-blue)]"></div>
        </div>
      );
    }
    return (
      <>
        {toast && <Toast {...toast} onDone={() => setToast(null)} />}
        <CodeEntryForm onSubmit={handleCodeEntry} apiBase={apiBase} />
      </>
    );
  }

  // ── Main editor ───────────────────────────────────────────────────────────
  const tabs = [
    { id: 'nickname',     label: 'Nickname',      icon: '✦' },
    { id: 'pins',         label: 'Pins',           icon: '⚐' },
    { id: 'tags',         label: 'Tags',           icon: '🏷' },
    { id: 'joinMessages', label: 'Join Messages',  icon: '✉' }
  ];

  return (
    <div className="min-h-screen p-4 md:p-8 flex justify-center">
      {toast && <Toast {...toast} onDone={() => setToast(null)} />}
      <div className="w-full max-w-6xl space-y-6">

        <div className="birdflop-panel p-4 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-gradient-to-br from-[#FF0080] to-[#8000FF] flex items-center justify-center text-xl shadow-lg">
              {playerInfo.name[0].toUpperCase()}
            </div>
            <div>
              <h2 className="text-xl font-bold text-white">{playerInfo.name}</h2>
              <p className="text-xs text-[var(--text-secondary)] uppercase tracking-tighter">Persona Identity Designer</p>
            </div>
          </div>

          <div className="flex bg-black/40 rounded-xl p-1">
            {tabs.map(tab => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`px-4 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2 ${
                  activeTab === tab.id
                    ? 'bg-[var(--accent-blue)] text-white shadow-lg'
                    : 'text-[var(--text-secondary)] hover:text-white'
                }`}
              >
                <span className="text-lg leading-none">{tab.icon}</span>
                <span className="hidden sm:inline">{tab.label}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="min-h-[600px]">
          {isLoading ? (
            <div className="flex items-center justify-center h-64">
              <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-[var(--accent-blue)]"></div>
            </div>
          ) : (
            <>
              {activeTab === 'nickname' && (
                <GradientGenerator
                  playerInfo={playerInfo}
                  currentNickname={personaData?.nickname}
                  initialText={personaData?.nickname ? stripTags(personaData.nickname) : playerInfo.name}
                />
              )}
              {activeTab === 'pins' && (
                <div className="birdflop-panel">
                  <div className="p-6 border-b border-[var(--border-color)]">
                    <h3 className="text-xl font-bold text-white">Select Your Pin</h3>
                    <p className="text-sm text-[var(--text-secondary)]">Pins appear next to your name in chat.</p>
                  </div>
                  <PersonaSelector
                    type="Pin"
                    items={personaData?.pins || []}
                    onSelect={(id) => handleSaveSelection('selectedPin', id)}
                    apiBase={playerInfo.apiBase}
                  />
                </div>
              )}
              {activeTab === 'tags' && (
                <div className="birdflop-panel">
                  <div className="p-6 border-b border-[var(--border-color)]">
                    <h3 className="text-xl font-bold text-white">Select Your Tag</h3>
                    <p className="text-sm text-[var(--text-secondary)]">Tags appear as a prefix or suffix in chat.</p>
                  </div>
                  <PersonaSelector
                    type="Tag"
                    items={personaData?.tags || []}
                    onSelect={(id) => handleSaveSelection('selectedTag', id)}
                  />
                </div>
              )}
              {activeTab === 'joinMessages' && (
                <div className="birdflop-panel">
                  <div className="p-6 border-b border-[var(--border-color)]">
                    <h3 className="text-xl font-bold text-white">Select Join Message</h3>
                    <p className="text-sm text-[var(--text-secondary)]">Choose the message shown when you join the server.</p>
                  </div>
                  <PersonaSelector
                    type="Join Message"
                    items={personaData?.joinMessages || []}
                    onSelect={(id) => handleSaveSelection('selectedJoinMessage', id)}
                  />
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default App;
