import makeWASocket, {
  DisconnectReason,
  useMultiFileAuthState,
  fetchLatestBaileysVersion,
} from '@whiskeysockets/baileys';
import Pino from 'pino';
import qrcode from 'qrcode-terminal';
import { createClient } from '@supabase/supabase-js';
import fs from 'fs';
import path from 'path';
import readline from 'readline';

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://dtkldxmfkhipdgiltzjl.supabase.co';
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR0a2xkeG1ma2hpcGRnaWx0empsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMyNzY3NjIsImV4cCI6MjA5ODg1Mjc2Mn0.jXEUggNeO_tvt2lm7BRRbzm4p1eI-WZRvojLVdKn2fg';
const AUTH_DIR = './auth_info_baileys';
const BOT_STATE_FILE = './bot_state.json';
const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: { persistSession: false },
});

let sock = null;
let botConfig = { phoneNumber: process.env.BOT_PHONE || '967', isActive: false };
let restarting = false;

function question(text) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise(resolve => rl.question(text, answer => { rl.close(); resolve(answer); }));
}

function readBotState() {
  try { return JSON.parse(fs.readFileSync(BOT_STATE_FILE, 'utf8')); } catch { return {}; }
}

function writeBotState(state) {
  fs.writeFileSync(BOT_STATE_FILE, JSON.stringify(state, null, 2));
}

function removeAuthSession() {
  if (fs.existsSync(AUTH_DIR)) fs.rmSync(AUTH_DIR, { recursive: true, force: true });
}

function normalizeArabic(input = '') {
  return input
    .toString()
    .trim()
    .toLowerCase()
    .replace(/[\u064B-\u065F\u0670]/g, '')
    .replace(/\u0640/g, '')
    .replace(/[إأآٱ]/g, 'ا')
    .replace(/ؤ/g, 'و')
    .replace(/ئ/g, 'ي')
    .replace(/ى/g, 'ي')
    .replace(/ة/g, 'ه')
    .replace(/["'`´‘’“”()[\]{}،,.:;؛!؟?\-_\/\\|]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function levenshtein(a, b) {
  if (a === b) return 0;
  if (!a.length) return b.length;
  if (!b.length) return a.length;
  const prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  const cur = new Array(b.length + 1);
  for (let i = 1; i <= a.length; i++) {
    cur[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      cur[j] = Math.min(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost);
    }
    for (let j = 0; j <= b.length; j++) prev[j] = cur[j];
  }
  return prev[b.length];
}

function scoreCompany(search, name) {
  const s = normalizeArabic(search);
  const n = normalizeArabic(name);
  if (!s || !n) return 0;
  if (s === n) return 1000;
  if (n.includes(s)) return 900 + Math.min(s.length, 80);
  if (s.includes(n)) return 850 + Math.min(n.length, 80);
  const tokens = s.split(' ').filter(Boolean);
  const nameTokens = new Set(n.split(' ').filter(Boolean));
  const tokenHits = tokens.filter(t => n.includes(t) || nameTokens.has(t)).length;
  let score = tokenHits * 140;
  const dist = levenshtein(s, n);
  const maxLen = Math.max(s.length, n.length, 1);
  score += Math.round((1 - Math.min(dist / maxLen, 1)) * 300);
  return score;
}

async function fetchBotConfig() {
  try {
    const { data, error } = await supabase
      .from('bot_config')
      .select('*')
      .eq('id', 'primary_bot')
      .maybeSingle();
    if (error) throw error;
    if (data) return { phoneNumber: String(data.phone_number || '967'), isActive: Boolean(data.is_active) };
  } catch (e) {
    console.error('⚠️ تعذر قراءة bot_config:', e.message);
  }
  return { phoneNumber: process.env.BOT_PHONE || '967', isActive: true };
}

async function fetchCompanies() {
  const { data, error } = await supabase
    .from('companies')
    .select('id,name,deleted_at')
    .is('deleted_at', null)
    .limit(5000);
  if (error) throw error;
  return data || [];
}

async function findCompany(queryText) {
  const companies = await fetchCompanies();
  const ranked = companies
    .map(c => ({ ...c, score: scoreCompany(queryText, c.name || '') }))
    .sort((a, b) => b.score - a.score);
  const best = ranked[0];
  if (best && best.score >= 180) return { company: best, suggestions: ranked.slice(0, 5) };
  return { company: null, suggestions: ranked.filter(x => x.score > 0).slice(0, 5) };
}

function epochDayToDate(epochDay) {
  return new Date(Number(epochDay) * 86400000);
}

function arabicDayName(date) {
  return ['الأحد', 'الإثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'][date.getUTCDay()];
}

function isoDate(date) {
  return date.toISOString().slice(0, 10);
}

async function fetchItinerary(companyId) {
  const { data, error } = await supabase
    .from('visits')
    .select('week_of_cycle,date_epoch_day,shift,deleted_at,cycle_start_epoch_day')
    .eq('company_id', companyId)
    .is('deleted_at', null)
    .order('cycle_start_epoch_day', { ascending: false })
    .order('week_of_cycle', { ascending: true });
  if (error) throw error;
  const visits = data || [];
  if (!visits.length) return [];
  const latestCycle = visits[0].cycle_start_epoch_day;
  return visits
    .filter(v => v.cycle_start_epoch_day === latestCycle)
    .sort((a, b) => Number(a.week_of_cycle) - Number(b.week_of_cycle));
}

function buildReply(company, visits) {
  const itinerary = visits.length
    ? visits.map(v => {
        const d = epochDayToDate(v.date_epoch_day);
        const shift = v.shift === 'MORNING' ? 'الفترة الصباحية' : 'الفترة المسائية';
        return `•   *الأسبوع ${v.week_of_cycle}: (${arabicDayName(d)}) - ${shift}*`;
      }).join('\n')
    : '•   *لا توجد زيارات مجدولة حاليًا.*';

  return `*صيدلية برج الأطباء - إدارة الصيدلية*\n\n*شركة: ${company.name}*\n\n*جدول الزيارات*\n\n${itinerary}\n\n*يرجى الالتزام بالموعد المحدد.*`;
}

function extractText(message) {
  const m = message.message || {};
  return (
    m.conversation ||
    m.extendedTextMessage?.text ||
    m.imageMessage?.caption ||
    m.videoMessage?.caption ||
    ''
  ).trim();
}

async function sendNoMatch(remoteJid, msg, query, suggestions) {
  const suggestionText = suggestions.length
    ? '\n\n*اقتراحات قريبة:*\n' + suggestions.map(s => `- ${s.name}`).join('\n')
    : '';
  const reply = `*صيدلية برج الأطباء - إدارة الصيدلية*\n\nلم يتم العثور على شركة مطابقة لعبارة:\n*${query}*${suggestionText}\n\nيرجى إرسال اسم الشركة بشكل أوضح.`;
  await sock.sendMessage(remoteJid, { text: reply }, { quoted: msg });
}

async function logBotQuery(sender, queryText, matchedCompany) {
  try {
    await supabase.from('bot_logs').insert({
      sender_phone: sender,
      query_text: queryText,
      matched_company: matchedCompany,
    });
  } catch (e) {
    console.error('⚠️ فشل تسجيل bot_logs:', e.message);
  }
}

async function ensureSessionForPhone(phoneNumber) {
  const state = readBotState();
  if (state.phoneNumber && state.phoneNumber !== phoneNumber) {
    console.log(`🔁 تم تغيير رقم البوت من ${state.phoneNumber} إلى ${phoneNumber}. حذف جلسة الربط القديمة...`);
    removeAuthSession();
  }
  writeBotState({ ...state, phoneNumber });
}

async function startBot() {
  botConfig = await fetchBotConfig();
  await ensureSessionForPhone(botConfig.phoneNumber);

  const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR);
  const { version } = await fetchLatestBaileysVersion();

  sock = makeWASocket({
    version,
    auth: state,
    logger: Pino({ level: 'silent' }),
    browser: ['Borg Pharmacy Bot', 'Chrome', '1.0'],
  });

  sock.ev.on('creds.update', saveCreds);

  sock.ev.on('connection.update', async update => {
    const { connection, lastDisconnect, qr } = update;
    if (qr) qrcode.generate(qr, { small: true });

    if (connection === 'open') {
      console.log(`✅ بوت واتساب متصل. الرقم النشط: ${botConfig.phoneNumber} | active=${botConfig.isActive}`);
    }

    if (connection === 'close') {
      const code = lastDisconnect?.error?.output?.statusCode;
      const shouldReconnect = code !== DisconnectReason.loggedOut;
      console.log('⚠️ انقطع الاتصال:', code, 'reconnect=', shouldReconnect);
      if (shouldReconnect) setTimeout(startBot, 3000);
    }
  });

  if (!state.creds.registered && botConfig.phoneNumber && botConfig.phoneNumber.length >= 8) {
    setTimeout(async () => {
      try {
        const code = await sock.requestPairingCode(botConfig.phoneNumber);
        console.log(`🔐 كود ربط واتساب للرقم ${botConfig.phoneNumber}: ${code}`);
      } catch (e) {
        console.error('❌ فشل إنشاء كود الربط:', e.message);
      }
    }, 2500);
  }

  sock.ev.on('messages.upsert', async ({ messages, type }) => {
    if (type !== 'notify') return;
    const msg = messages?.[0];
    if (!msg?.message || msg.key.fromMe) return;

    const remoteJid = msg.key.remoteJid;
    const sender = (msg.key.participant || remoteJid || '').replace(/@.*/, '');
    const rawText = extractText(msg);
    const cleanText = normalizeArabic(rawText);

    if (!cleanText || cleanText.length < 2 || cleanText.length > 80) return;

    botConfig = await fetchBotConfig();
    if (!botConfig.isActive) {
      console.log('⏸️ البوت غير مفعل من التطبيق. تجاهل الرسالة:', rawText);
      return;
    }

    console.log(`🔍 استعلام من ${sender}: ${rawText} => ${cleanText}`);

    try {
      const { company, suggestions } = await findCompany(cleanText);
      if (!company) {
        console.log('⚠️ لم يتم العثور على شركة مطابقة. تم إرسال رد للمرسل.');
        await sendNoMatch(remoteJid, msg, rawText, suggestions);
        await logBotQuery(sender, rawText, 'لا يوجد تطابق');
        return;
      }

      const visits = await fetchItinerary(company.id);
      const reply = buildReply(company, visits);
      await sock.sendMessage(remoteJid, { text: reply }, { quoted: msg });
      await logBotQuery(sender, rawText, company.name);
      console.log(`✅ تم إرسال جدول الزيارات: ${company.name}`);
    } catch (error) {
      console.error('❌ خطأ أثناء معالجة الرسالة:', error);
      await sock.sendMessage(remoteJid, { text: 'حدث خطأ مؤقت أثناء البحث. يرجى المحاولة مرة أخرى.' }, { quoted: msg });
    }
  });
}

async function watchConfigChanges() {
  setInterval(async () => {
    if (restarting) return;
    const latest = await fetchBotConfig();
    if (latest.phoneNumber !== botConfig.phoneNumber) {
      restarting = true;
      console.log(`🔁 تم رصد رقم جديد من التطبيق: ${latest.phoneNumber}`);
      try { sock?.end?.(); } catch {}
      removeAuthSession();
      botConfig = latest;
      setTimeout(async () => {
        restarting = false;
        await startBot();
      }, 2000);
    } else {
      botConfig = latest;
    }
  }, 30000);
}

console.log('🚀 تشغيل Borg Pharmacy WhatsApp Bot...');
await startBot();
await watchConfigChanges();
