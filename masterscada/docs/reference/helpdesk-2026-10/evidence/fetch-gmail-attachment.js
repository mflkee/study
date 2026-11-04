#!/usr/bin/env node
// fetch-gmail-attachment.js — выкачать ВСЕ вложения письма Gmail по uid.
// Запуск: node fetch-gmail-attachment.js <uid> "[Gmail]/Вся почта" <каталог>
// Логин/пароль берутся из настроек MCP-сервера Gmail (~/Documents/finance_manager)
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const MCP = path.join(os.homedir(), 'Documents/finance_manager/mcp');
const { ImapFlow } = require(path.join(MCP, 'node_modules/imapflow'));
const { simpleParser } = require(path.join(MCP, 'node_modules/mailparser'));
const YAML = require(path.join(MCP, 'node_modules/yaml'));

function loadEnvFile(p) {
  const out = {};
  if (!fs.existsSync(p)) return out;
  for (const line of fs.readFileSync(p, 'utf8').split('\n')) {
    const m = line.match(/^\s*(?:export\s+)?([A-Za-z0-9_]+)\s*=\s*(.*?)\s*$/);
    if (m) out[m[1]] = m[2].trim().replace(/^["']|["']$/g, '');
  }
  return out;
}
const ROOT = path.join(os.homedir(), 'Documents/finance_manager');
const CFG = YAML.parse(fs.readFileSync(path.join(ROOT, 'config.yaml'), 'utf8')) || {};
const ENV = Object.fromEntries(Object.entries({
  ...process.env,
  ...loadEnvFile(path.join(os.homedir(), '.config/zsh/secrets.zsh')),
  ...loadEnvFile(path.join(MCP, '.env')),
}).filter(([, v]) => v !== '' && v != null));

const uid = Number(process.argv[2]);
const mailbox = process.argv[3] || 'INBOX';
const outDir = process.argv[4] || '/tmp/opencode/ms4d-reply';

(async () => {
  const c = new ImapFlow({
    host: 'imap.gmail.com', port: 993, secure: true, logger: false,
    auth: { user: CFG.gmail.user || ENV.GMAIL_USER, pass: ENV.GMAIL_APP_PASSWORD || ENV.GMAIL_PASSWORD },
    tls: { servername: 'imap.gmail.com' }, socketTimeout: 120000,
  });
  c.on('error', () => {});
  await c.connect();
  const lock = await c.getMailboxLock(mailbox, { readOnly: true });
  try {
    const msg = await c.fetchOne(String(uid), { source: true }, { uid: true });
    const parsed = await simpleParser(msg.source);
    fs.mkdirSync(outDir, { recursive: true });
    for (const att of parsed.attachments) {
      const p = path.join(outDir, att.filename || `att-${att.checksum}`);
      fs.writeFileSync(p, att.content);
      console.log('saved', p, att.content.length);
    }
  } finally { lock.release(); await c.logout(); }
})().catch((e) => { console.error('ERR', e.message); process.exit(1); });
