// The web for guests (docs/designs/2026-10-16-web-guests.md): /g/<code> opens the group in the
// browser. An anonymous Firebase user joins it like the app does, picks who they are, and then sees the
// expenses and balances, adds expenses and records payments. Writes are the app's documents with their
// history entry (payloads.js); balances are the app's arithmetic (money.js).
import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js';
import { getAuth, onAuthStateChanged, signInAnonymously } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js';
import {
  arrayUnion, collection, doc, getDoc, getFirestore, increment, onSnapshot, serverTimestamp, updateDoc, writeBatch,
} from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-firestore.js';
import { CURRENCIES, decimalsOf, groupBalances, myShare } from './money.js';
import {
  changelogEntry, claimMember, convertMinor, defaultSplit, expenseDiff, expenseUpdate, fullyEditable, joinGroup,
  markDeleted, newExpense, newMember, newTransfer, parseAmount, parseRate, rateFromFrankfurter,
} from './payloads.js';

const f = { serverTimestamp, arrayUnion, increment };
const CATEGORIES = ['FOOD', 'GROCERIES', 'TRANSPORT', 'ACCOMMODATION', 'LEISURE', 'SHOPPING', 'HOME', 'UTILITIES', 'HEALTH', 'GIFTS', 'OTHER'];
const ALPHABET = /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$/;
const LANG_KEY = 'owlsplit.lang';
// The app's exchange rates (AppConfig.FX_API_BASE_URL): ECB, no key, CORS open.
const FX_URL = 'https://api.frankfurter.dev/v1';

const TEXT = {
  es: {
    loading: 'Cargando…', invalid: 'Este enlace no es válido', invalidLead: 'Pide a quien te invitó un enlace nuevo.',
    offline: 'No se pudo conectar. Revisa tu conexión y recarga la página.',
    invited: 'Te han invitado a', people: (n) => `${n} ${n === 1 ? 'persona' : 'personas'}`,
    joinLead: 'Entra desde el navegador, sin instalar nada. Si prefieres la app, ábrela con el mismo enlace.',
    join: 'Entrar en el grupo', joining: 'Entrando…',
    who: '¿Quién eres en el grupo?', whoLead: 'Si ya te habían añadido, elige tu nombre: sus gastos y su saldo pasan a ser tuyos.',
    imNew: 'Soy nuevo', yourName: 'Tu nombre', addMe: 'Añadirme',
    yourBalance: 'Tu saldo', owed: 'Te deben', owe: 'Debes', settled: 'Estás al día',
    tabExpenses: 'Gastos', tabBalances: 'Saldos', tabSettle: 'Saldar',
    add: 'Añadir gasto', noExpenses: 'Aún no hay gastos.', paidBy: (n) => `Pagó ${n}`, yourShare: (a) => `Tu parte ${a}`,
    allSettled: 'Todo saldado: nadie debe nada.', pays: (a, b) => `${a} paga a ${b}`, you: 'Tú', youPay: (b) => `Pagas a ${b}`,
    record: 'Registrar el pago', recorded: 'Pago registrado.', confirmRecord: (a, b) => `¿Ya has pagado ${a} a ${b}? Regístralo solo cuando el dinero esté enviado.`,
    confirmOther: (a, from, to) => `¿${from} ya ha pagado ${a} a ${to}? Regístralo solo cuando el dinero esté enviado.`,
    bizum: 'Bizum', copyPhone: (p) => `Copiar teléfono ${p}`, iban: 'Copiar IBAN', copied: 'Copiado',
    newExpense: 'Nuevo gasto', amount: 'Importe', title: 'Concepto', titlePh: 'Cena, taxi, súper…', category: 'Categoría',
    date: 'Fecha', payer: 'Pagó', who2: 'Para quién', save: 'Guardar', cancel: 'Cancelar', saving: 'Guardando…',
    needAmount: 'Escribe un importe.', needTitle: 'Escribe un concepto.', needPeople: 'Elige al menos una persona.',
    saveFailed: 'No se pudo guardar. Revisa tu conexión e inténtalo otra vez.',
    getApp: 'Con la app tienes además avisos, estadísticas y modo sin conexión.', download: 'Descargar Owlsplit',
    editExpense: 'Editar gasto', delete: 'Borrar', confirmDelete: '¿Borrar este gasto? Queda en el historial y se puede recuperar desde la app.',
    deleted: 'Gasto borrado.', saved: 'Gasto guardado.', inApp: 'Este gasto está en otra divisa o tiene un reparto que solo se edita en la app: aquí puedes cambiar el concepto, la categoría y la fecha, o borrarlo.',
    conflict: 'Alguien lo ha cambiado mientras tanto. Vuelve a abrirlo e inténtalo otra vez.',
    payments: 'Pagos registrados', undo: 'Deshacer', confirmUndo: '¿Deshacer este pago? El saldo vuelve a como estaba.', undone: 'Pago deshecho.',
    paymentLine: (a, b) => `${a} → ${b}`,
    otherCurrency: 'Los gastos en otras divisas se cuentan a su cambio guardado.',
    currency: 'Divisa', rate: (from, to) => `Cambio: ${to} por 1 ${from}`, rateDay: (d) => `BCE, ${d}`,
    needRate: 'Escribe el tipo de cambio.',
    categories: { FOOD: 'Comida', GROCERIES: 'Súper', TRANSPORT: 'Transporte', ACCOMMODATION: 'Alojamiento', LEISURE: 'Ocio', SHOPPING: 'Compras', HOME: 'Casa', UTILITIES: 'Suministros', HEALTH: 'Salud', GIFTS: 'Regalos', OTHER: 'Otros' },
  },
  en: {
    loading: 'Loading…', invalid: 'This link is not valid', invalidLead: 'Ask whoever invited you for a new link.',
    offline: 'Could not connect. Check your connection and reload the page.',
    invited: 'You have been invited to', people: (n) => `${n} ${n === 1 ? 'person' : 'people'}`,
    joinLead: 'Join from the browser, without installing anything. If you prefer the app, open it with the same link.',
    join: 'Join the group', joining: 'Joining…',
    who: 'Who are you in the group?', whoLead: 'If they had already added you, pick your name: its expenses and balance become yours.',
    imNew: "I'm new", yourName: 'Your name', addMe: 'Add me',
    yourBalance: 'Your balance', owed: 'You are owed', owe: 'You owe', settled: 'You are all settled up',
    tabExpenses: 'Expenses', tabBalances: 'Balances', tabSettle: 'Settle up',
    add: 'Add expense', noExpenses: 'No expenses yet.', paidBy: (n) => `Paid by ${n}`, yourShare: (a) => `Your share ${a}`,
    allSettled: 'All settled: nobody owes anything.', pays: (a, b) => `${a} pays ${b}`, you: 'You', youPay: (b) => `You pay ${b}`,
    record: 'Record the payment', recorded: 'Payment recorded.', confirmRecord: (a, b) => `Have you paid ${b} ${a}? Record it only once the money has been sent.`,
    confirmOther: (a, from, to) => `Has ${from} paid ${to} ${a}? Record it only once the money has been sent.`,
    bizum: 'Bizum', copyPhone: (p) => `Copy phone ${p}`, iban: 'Copy IBAN', copied: 'Copied',
    newExpense: 'New expense', amount: 'Amount', title: 'Description', titlePh: 'Dinner, taxi, groceries…', category: 'Category',
    date: 'Date', payer: 'Paid by', who2: 'For whom', save: 'Save', cancel: 'Cancel', saving: 'Saving…',
    needAmount: 'Enter an amount.', needTitle: 'Enter a description.', needPeople: 'Pick at least one person.',
    saveFailed: 'Could not save. Check your connection and try again.',
    getApp: 'The app also gives you notifications, statistics and offline use.', download: 'Download Owlsplit',
    editExpense: 'Edit expense', delete: 'Delete', confirmDelete: 'Delete this expense? It stays in the history and can be restored from the app.',
    deleted: 'Expense deleted.', saved: 'Expense saved.', inApp: 'This expense is in another currency or has a split that is only edited in the app: here you can change its description, category and date, or delete it.',
    conflict: 'Someone changed it meanwhile. Open it again and retry.',
    payments: 'Recorded payments', undo: 'Undo', confirmUndo: 'Undo this payment? The balances go back to how they were.', undone: 'Payment undone.',
    paymentLine: (a, b) => `${a} → ${b}`,
    otherCurrency: 'Expenses in other currencies count at their saved rate.',
    currency: 'Currency', rate: (from, to) => `Rate: ${to} per 1 ${from}`, rateDay: (d) => `ECB, ${d}`,
    needRate: 'Enter the exchange rate.',
    categories: { FOOD: 'Food', GROCERIES: 'Groceries', TRANSPORT: 'Transport', ACCOMMODATION: 'Accommodation', LEISURE: 'Leisure', SHOPPING: 'Shopping', HOME: 'Home', UTILITIES: 'Utilities', HEALTH: 'Health', GIFTS: 'Gifts', OTHER: 'Other' },
  },
};

// ── Language (the same switch and key as the rest of the site) ─────────────────────────────────────
let lang = (() => {
  try {
    const saved = localStorage.getItem(LANG_KEY);
    if (saved === 'es' || saved === 'en') return saved;
  } catch { /* Private mode: fall back to the browser. */ }
  return /^es/i.test(navigator.language || 'es') ? 'es' : 'en';
})();
const t = () => TEXT[lang];

// ── State ──────────────────────────────────────────────────────────────────────────────────────────
const code = decodeURIComponent(location.pathname.split('/g/')[1] || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
const state = {
  screen: 'loading', uid: null, invite: null, groupId: null, group: null,
  members: [], expenses: [], transfers: [], tab: 'expenses', adding: false, confirming: null, confirmingDelete: false, undoing: null,
  busy: false, error: null, notice: null,
};
let db;
let unsubscribers = [];

// ── Small DOM helpers: text only, never HTML from the data ─────────────────────────────────────────
function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className = v;
    else if (k === 'value') el.value = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) if (c !== null && c !== undefined && c !== false) el.append(c instanceof Node ? c : String(c));
  return el;
}

function money(minor, currency) {
  const decimals = decimalsOf(currency);
  return new Intl.NumberFormat(lang === 'es' ? 'es-ES' : 'en-GB', { style: 'currency', currency, minimumFractionDigits: decimals, maximumFractionDigits: decimals })
    .format(minor / 10 ** decimals);
}

const today = () => new Date().toISOString().slice(0, 10);
const nameOf = (id) => state.members.find((m) => m.id === id)?.name ?? '?';
const me = () => state.members.find((m) => m.userUid === state.uid && !m.deleted);
const visibleMembers = () => state.members.filter((m) => !m.deleted).sort((a, b) => a.sortKey - b.sortKey || (a.id < b.id ? -1 : 1));

function copy(text, button) {
  navigator.clipboard?.writeText(text).then(() => {
    const before = button.textContent;
    button.textContent = t().copied;
    setTimeout(() => { button.textContent = before; }, 2000);
  });
}

// ── Screens ────────────────────────────────────────────────────────────────────────────────────────
function render() {
  document.documentElement.lang = lang;
  for (const b of document.querySelectorAll('#lang button')) b.setAttribute('aria-pressed', String(b.dataset.lang === lang));
  const app = document.getElementById('app');
  app.replaceChildren(...screen().flat().filter((c) => c !== null && c !== undefined && c !== false));
}

function screen() {
  const T = t();
  switch (state.screen) {
    case 'loading':
      return [h('p', { class: 'muted' }, T.loading)];
    case 'invalid':
      return [h('h1', {}, T.invalid), h('p', { class: 'muted' }, T.invalidLead)];
    case 'offline':
      return [h('h1', {}, 'Owlsplit'), h('p', { class: 'muted' }, T.offline)];
    case 'join':
      return [
        h('p', { class: 'muted' }, T.invited),
        h('h1', {}, state.invite.groupName),
        h('p', { class: 'muted' }, T.people(state.invite.memberCount)),
        h('p', { class: 'muted' }, T.joinLead),
        h('button', { class: 'button', type: 'button', disabled: state.busy, onclick: join }, state.busy ? T.joining : T.join),
        state.error && h('p', { class: 'error' }, state.error),
      ];
    case 'who':
      return whoScreen();
    case 'group':
      return state.adding ? [expenseForm()] : groupScreen();
    default:
      return [];
  }
}

function whoScreen() {
  const T = t();
  const unclaimed = visibleMembers().filter((m) => !m.userUid);
  const name = h('input', { type: 'text', maxlength: '40', autocomplete: 'given-name', placeholder: T.yourName, 'aria-label': T.yourName });
  return [
    h('h1', {}, T.who),
    h('p', { class: 'muted' }, T.whoLead),
    unclaimed.length > 0 && h('div', { class: 'list' }, unclaimed.map((m) =>
      h('button', { class: 'row', type: 'button', disabled: state.busy, onclick: () => claim(m) }, h('span', { class: 'main title' }, m.name)))),
    h('div', { class: 'form' },
      h('label', {}, T.imNew, name),
      h('button', { class: 'button', type: 'button', disabled: state.busy, onclick: () => addMe(name.value) }, T.addMe)),
    state.error && h('p', { class: 'error' }, state.error),
  ];
}

function groupScreen() {
  const T = t();
  const base = state.group.baseCurrency;
  const result = groupBalances(base, state.members, state.expenses, state.transfers);
  const mine = me();
  const balance = mine ? result.balances[mine.id] ?? 0 : 0;
  const tab = (id, label) => h('button', { type: 'button', 'aria-pressed': String(state.tab === id), onclick: () => { state.tab = id; render(); } }, label);
  return [
    h('h1', {}, state.group.name),
    h('div', { class: 'hero' },
      h('div', { class: 'muted' }, balance > 0 ? T.owed : balance < 0 ? T.owe : T.yourBalance),
      h('div', { class: `amount ${balance > 0 ? 'positive' : ''}` }, money(Math.abs(balance), base)),
      balance === 0 && h('div', { class: 'muted' }, T.settled)),
    state.notice && h('p', { class: 'notice', role: 'status' }, state.notice),
    h('div', { class: 'tabs', role: 'group' }, tab('expenses', T.tabExpenses), tab('balances', T.tabBalances), tab('settle', T.tabSettle)),
    ...(state.tab === 'expenses' ? expensesTab(base) : state.tab === 'balances' ? balancesTab(result, base) : settleTab(result, base)),
    h('p', { class: 'muted' }, T.getApp, ' ', h('a', { href: '/' }, T.download)),
  ];
}

function expensesTab(base) {
  const T = t();
  const mine = me();
  const list = state.expenses.filter((e) => !e.deleted)
    .sort((a, b) => (b.date > a.date ? 1 : b.date < a.date ? -1 : b.createdAt - a.createdAt));
  const foreign = list.some((e) => e.currency !== base);
  return [
    h('button', { class: 'button', type: 'button', onclick: () => openForm('new') }, `+ ${T.add}`),
    list.length === 0
      ? h('p', { class: 'muted' }, T.noExpenses)
      : h('div', { class: 'list' }, list.map((e) => {
        const share = mine ? myShare(e, state.members, mine.id) : 0;
        const payers = Object.keys(e.payers).map(nameOf).join(', ');
        return h('button', { class: 'row', type: 'button', onclick: () => openForm(e.id), 'aria-label': `${T.editExpense}: ${e.title}` },
          h('div', { class: 'main' },
            h('div', { class: 'title' }, e.title),
            h('div', { class: 'sub' }, `${e.date} · ${T.paidBy(payers)}`)),
          h('div', { class: 'end' }, money(e.amountMinor, e.currency),
            share > 0 && h('div', { class: 'sub' }, T.yourShare(money(share, e.currency)))));
      })),
    foreign && h('p', { class: 'muted' }, T.otherCurrency),
  ];
}

function balancesTab(result, base) {
  const mine = me();
  return [h('div', { class: 'list' }, visibleMembers().map((m) => {
    const v = result.balances[m.id] ?? 0;
    return h('div', { class: 'row' },
      h('div', { class: 'main title' }, m.id === mine?.id ? `${m.name} (${t().you})` : m.name),
      h('div', { class: `end ${v > 0 ? 'positive' : ''}` }, `${v > 0 ? '+' : v < 0 ? '−' : ''}${money(Math.abs(v), base)}`));
  }))];
}

function settleTab(result, base) {
  return [...suggestionsList(result, base), ...paymentsList()];
}

/** The latest payments, so a mistaken one can be undone (it is kept, marked as deleted). */
function paymentsList() {
  const T = t();
  const list = state.transfers.filter((x) => !x.deleted)
    .sort((a, b) => (b.date > a.date ? 1 : b.date < a.date ? -1 : b.createdAt - a.createdAt)).slice(0, 10);
  if (list.length === 0) return [];
  return [h('h2', {}, T.payments), h('div', { class: 'list' }, list.map((x) => {
    const confirming = state.undoing === x.id;
    return h('div', {},
      h('div', { class: 'row' },
        h('div', { class: 'main' }, h('div', { class: 'title' }, T.paymentLine(nameOf(x.fromMemberId), nameOf(x.toMemberId))), h('div', { class: 'sub' }, x.date)),
        h('div', { class: 'end' }, money(x.amountMinor, x.currency))),
      confirming && h('p', { class: 'muted', style: 'padding: 0 16px 8px' }, T.confirmUndo),
      h('div', { class: 'pay' },
        h('button', { type: 'button', disabled: state.busy, onclick: () => (confirming ? undoPayment(x) : ((state.undoing = x.id), render())) }, T.undo),
        confirming && h('button', { type: 'button', onclick: () => { state.undoing = null; render(); } }, T.cancel)));
  }))];
}

function suggestionsList(result, base) {
  const T = t();
  const mine = me();
  if (result.suggestions.length === 0) return [h('p', { class: 'muted' }, T.allSettled)];
  return [h('div', { class: 'list' }, result.suggestions.map((p) => {
    const amount = money(p.amountMinor, base);
    const mineToPay = p.fromMemberId === mine?.id;
    const label = mineToPay ? T.youPay(nameOf(p.toMemberId)) : T.pays(nameOf(p.fromMemberId), nameOf(p.toMemberId));
    // Suggestions are recomputed on every change: the one being confirmed is known by who pays whom.
    const confirming = state.confirming === payKey(p);
    return h('div', {},
      h('div', { class: 'row' }, h('div', { class: 'main title' }, label), h('div', { class: 'end' }, amount)),
      mineToPay && payOptions(p, base),
      confirming && h('p', { class: 'muted', style: 'padding: 0 16px 8px' },
        mineToPay ? T.confirmRecord(amount, nameOf(p.toMemberId)) : T.confirmOther(amount, nameOf(p.fromMemberId), nameOf(p.toMemberId))),
      h('div', { class: 'pay' },
        h('button', { type: 'button', disabled: state.busy, onclick: () => (confirming ? record(p) : askRecord(p)) }, T.record),
        confirming && h('button', { type: 'button', onclick: () => { state.confirming = null; render(); } }, T.cancel)));
  }))];
}

/** How the person I owe wants to be paid: what they published in their profile. */
function payOptions(payment, base) {
  const T = t();
  const pay = state.members.find((m) => m.id === payment.toMemberId)?.payMethods;
  if (!pay) return null;
  const plain = (payment.amountMinor / 10 ** decimalsOf(base)).toFixed(decimalsOf(base));
  const options = [];
  if (pay.bizum) {
    const b = h('button', { type: 'button' }, T.copyPhone(pay.bizum));
    b.addEventListener('click', () => copy(pay.bizum, b));
    options.push(b);
  }
  if (pay.paypal) options.push(h('a', { href: `https://paypal.me/${encodeURIComponent(pay.paypal)}/${plain}${base}`, target: '_blank', rel: 'noopener' }, 'PayPal'));
  if (pay.revolut) options.push(h('a', { href: `https://revolut.me/${encodeURIComponent(pay.revolut)}`, target: '_blank', rel: 'noopener' }, 'Revolut'));
  if (pay.iban) {
    const b = h('button', { type: 'button' }, `${T.iban} · ${pay.iban.replace(/(.{4})/g, '$1 ').trim()}`);
    b.addEventListener('click', () => copy(pay.iban, b));
    options.push(b);
  }
  return options.length > 0 ? h('div', { class: 'pay' }, options) : null;
}

function openForm(which) {
  const base = state.group.baseCurrency;
  const editing = which === 'new' ? null : state.expenses.find((e) => e.id === which);
  const mine = me();
  const active = visibleMembers().filter((m) => m.active);
  // What is typed lives here: the group updating meanwhile redraws the form without losing it.
  state.draft = editing
    ? {
      amount: amountText(editing.amountMinor, decimalsOf(editing.currency)),
      title: editing.title,
      category: editing.category,
      date: editing.date,
      payerId: Object.keys(editing.payers)[0],
      ids: Object.keys(editing.splitValues).filter((id) => editing.splitValues[id] > 0),
      currency: editing.currency,
      rate: null,
    }
    : {
      amount: '', title: '', category: 'OTHER', date: today(), payerId: mine?.id ?? active[0]?.id,
      ids: active.map((m) => m.id), currency: base, rate: null,
    };
  state.adding = which;
  state.error = null;
  state.notice = null;
  state.confirmingDelete = false;
  render();
}

/** "12,50" for the field, in the page language's separator. */
function amountText(minor, decimals) {
  const text = (minor / 10 ** decimals).toFixed(decimals);
  return lang === 'es' ? text.replace('.', ',') : text;
}

/** Another currency: the ECB rate into the group's (Frankfurter, as the app), read as text. */
async function fetchRate(currency) {
  const base = state.group.baseCurrency;
  const draft = state.draft;
  draft.rate = { text: '', day: '', loading: true };
  render();
  let found = null;
  try {
    const body = await fetch(`${FX_URL}/latest?base=${currency}&symbols=${base}`).then((r) => (r.ok ? r.text() : ''));
    found = rateFromFrankfurter(body, base);
  } catch { /* Offline: typed by hand. */ }
  // The user may have picked another currency meanwhile.
  if (state.draft !== draft || draft.currency !== currency) return;
  const text = found ? (found.scaled / 1e9).toString() : '';
  draft.rate = { text: lang === 'es' ? text.replace('.', ',') : text, day: found?.day ?? '', loading: false };
  render();
}

function expenseForm() {
  const T = t();
  const base = state.group.baseCurrency;
  const draft = state.draft;
  const editing = state.adding === 'new' ? null : state.expenses.find((e) => e.id === state.adding);
  const full = !editing || fullyEditable(editing, base);
  const decimals = decimalsOf(draft.currency);
  // Who can take part: the active members, plus anyone already in the expense.
  const active = visibleMembers().filter((m) => m.active || (editing && (m.id in editing.splitValues || m.id in editing.payers)));
  const field = (key) => (e) => { draft[key] = e.target.value; };
  const amount = h('input', {
    class: 'amount-input', type: 'text', inputmode: 'decimal', placeholder: money(0, draft.currency), 'aria-label': T.amount,
    disabled: !full, value: draft.amount, oninput: (e) => { draft.amount = e.target.value; showEquivalent(); },
  });
  const title = h('input', { type: 'text', maxlength: '80', placeholder: T.titlePh, value: draft.title, oninput: field('title') });
  const category = h('select', { onchange: field('category') }, CATEGORIES.map((c) => h('option', { value: c }, T.categories[c])));
  category.value = draft.category;
  const currency = h('select', {
    disabled: !!editing,
    onchange: (e) => {
      draft.currency = e.target.value;
      draft.rate = null;
      if (draft.currency !== base) fetchRate(draft.currency); else render();
    },
  }, CURRENCIES.map((c) => h('option', { value: c }, c)));
  currency.value = draft.currency;
  const date = h('input', { type: 'date', value: draft.date, onchange: field('date') });
  const payer = h('select', { disabled: !full, onchange: field('payerId') }, active.map((m) => h('option', { value: m.id }, m.name)));
  payer.value = draft.payerId;
  const checks = active.map((m) => h('input', {
    type: 'checkbox', value: m.id, 'aria-label': m.name, disabled: !full, checked: draft.ids.includes(m.id),
    onchange: (e) => { draft.ids = e.target.checked ? [...draft.ids, m.id] : draft.ids.filter((id) => id !== m.id); },
  }));

  // Another currency: its rate (fetched, editable) and what it is in the group's.
  const foreign = draft.currency !== base && !editing;
  const equivalent = h('div', { class: 'sub' });
  const rateInput = foreign && h('input', {
    type: 'text', inputmode: 'decimal', value: draft.rate?.text ?? '', 'aria-label': T.rate(draft.currency, base),
    oninput: (e) => { draft.rate = { ...(draft.rate ?? {}), text: e.target.value, day: '' }; showEquivalent(); },
  });
  function showEquivalent() {
    if (!foreign) return;
    const minor = parseAmount(draft.amount, decimals);
    const scaled = parseRate(draft.rate?.text);
    equivalent.textContent = minor && scaled ? `≈ ${money(convertMinor(minor, decimals, decimalsOf(base), scaled), base)}` : '';
  }
  showEquivalent();

  const save = () => {
    const amountMinor = full ? parseAmount(draft.amount, decimals) : editing.amountMinor;
    const rate = foreign ? parseRate(draft.rate?.text) : 1_000_000_000;
    state.error = !amountMinor ? T.needAmount : !draft.title.trim() ? T.needTitle
      : draft.ids.length === 0 ? T.needPeople : !rate ? T.needRate : null;
    if (state.error) { render(); return; }
    const values = { ...draft, title: draft.title, date: draft.date || today(), amountMinor, fxRateScaled: rate };
    if (editing) saveEdit(editing, values, full); else addExpense(values);
  };
  return h('div', { class: 'form' },
    h('h2', {}, editing ? T.editExpense : T.newExpense),
    editing && !full && h('p', { class: 'notice' }, T.inApp),
    h('label', {}, T.amount, amount),
    h('label', {}, T.currency, currency),
    foreign && h('label', {}, T.rate(draft.currency, base),
      draft.rate?.loading ? h('span', { class: 'muted' }, T.loading) : rateInput,
      draft.rate?.day && h('span', { class: 'sub' }, T.rateDay(draft.rate.day)),
      equivalent),
    h('label', {}, T.title, title),
    h('label', {}, T.category, category),
    h('label', {}, T.date, date),
    h('label', {}, T.payer, payer),
    h('div', {}, h('div', { class: 'label' }, T.who2),
      h('div', { class: 'checks' }, active.map((m, i) => h('label', {}, checks[i], m.name)))),
    state.error && h('p', { class: 'error', role: 'alert' }, state.error),
    editing && state.confirmingDelete && h('p', { class: 'notice', role: 'alert' }, T.confirmDelete),
    h('div', { class: 'actions' },
      h('button', { class: 'button', type: 'button', disabled: state.busy, onclick: save }, state.busy ? T.saving : T.save),
      editing && h('button', {
        class: 'button link-button', type: 'button', disabled: state.busy,
        onclick: () => (state.confirmingDelete ? deleteExpense(editing) : (state.confirmingDelete = true, render())),
      }, T.delete),
      h('button', { class: 'button link-button', type: 'button', onclick: () => { state.adding = false; state.error = null; render(); } }, T.cancel)));
}

// ── Actions ────────────────────────────────────────────────────────────────────────────────────────
const groupRef = () => doc(db, 'groups', state.groupId);
const sub = (name, id) => doc(db, 'groups', state.groupId, name, id);
const uuid = () => crypto.randomUUID();

async function busy(work) {
  state.busy = true;
  state.error = null;
  render();
  try {
    await work();
  } catch (e) {
    console.error(e);
    state.error = e?.code === 'permission-denied' ? t().conflict : t().saveFailed;
  } finally {
    state.busy = false;
    render();
  }
}

function join() {
  return busy(async () => {
    await updateDoc(groupRef(), joinGroup(f, state.uid, code));
    await openGroup();
  });
}

function claim(member) {
  return busy(async () => {
    const batch = writeBatch(db);
    batch.update(sub('members', member.id), claimMember(f, state.uid, member));
    const diff = [{ field: 'userUid', before: null, after: state.uid }];
    if (!member.active) diff.push({ field: 'active', before: 'false', after: 'true' });
    batch.set(sub('changelog', uuid()), changelogEntry(f, state.uid, { entityType: 'MEMBER', entityId: member.id, action: 'JOINED', diff, now: Date.now() }));
    await batch.commit();
  });
}

function addMe(name) {
  if (!name.trim()) return undefined;
  return busy(async () => {
    const id = uuid();
    const sortKey = state.members.reduce((max, m) => Math.max(max, m.sortKey), -1) + 1;
    const batch = writeBatch(db);
    batch.set(sub('members', id), newMember(f, state.uid, name, sortKey));
    batch.set(sub('changelog', uuid()), changelogEntry(f, state.uid, { entityType: 'MEMBER', entityId: id, action: 'JOINED', now: Date.now() }));
    await batch.commit();
  });
}

function addExpense({ title, category, date, amountMinor, payerId, ids, currency, fxRateScaled }) {
  return busy(async () => {
    const id = uuid();
    const now = Date.now();
    const split = defaultSplit(state.members, ids);
    const batch = writeBatch(db);
    batch.set(sub('expenses', id), newExpense(f, state.uid, {
      title, category, date, currency, amountMinor, payerId, ...split, now, fxRateScaled,
    }));
    batch.set(sub('changelog', uuid()), changelogEntry(f, state.uid, { entityType: 'EXPENSE', entityId: id, action: 'CREATED', now }));
    await batch.commit();
    state.adding = false;
    state.tab = 'expenses';
    state.notice = t().saved;
  });
}

const payKey = (p) => `${p.fromMemberId}>${p.toMemberId}`;

/** The new values of an edited expense: only what changed is written, with its history diff. */
function saveEdit(old, values, full) {
  return busy(async () => {
    const next = { ...old, title: values.title.trim().slice(0, 80), category: values.category, date: values.date };
    if (full) {
      next.amountMinor = values.amountMinor;
      next.payers = { [values.payerId]: values.amountMinor };
      // Weights keep the ones the expense had; equal splits mark everyone with 1.
      const split = defaultSplit(state.members, values.ids);
      next.splitType = old.splitType === 'WEIGHTS' ? 'WEIGHTS' : split.splitType;
      next.splitValues = next.splitType === 'WEIGHTS'
        ? Object.fromEntries(values.ids.map((id) => [id, old.splitValues[id] ?? state.members.find((m) => m.id === id)?.defaultWeight ?? 1]))
        : split.splitValues;
    }
    const diff = expenseDiff(old, next);
    if (diff.length === 0) { state.adding = false; return; }
    const changes = Object.fromEntries(diff.map((c) => [c.field, next[c.field]]));
    const batch = writeBatch(db);
    batch.update(sub('expenses', old.id), expenseUpdate(f, state.uid, old, changes));
    batch.set(sub('changelog', uuid()), changelogEntry(f, state.uid, { entityType: 'EXPENSE', entityId: old.id, action: 'UPDATED', diff, now: Date.now() }));
    await batch.commit();
    state.adding = false;
    state.notice = t().saved;
  });
}

function deleteExpense(old) {
  return busy(async () => {
    const batch = writeBatch(db);
    batch.update(sub('expenses', old.id), markDeleted(f, state.uid, old));
    batch.set(sub('changelog', uuid()), changelogEntry(f, state.uid, { entityType: 'EXPENSE', entityId: old.id, action: 'DELETED', now: Date.now() }));
    await batch.commit();
    state.adding = false;
    state.confirmingDelete = false;
    state.notice = t().deleted;
  });
}

function undoPayment(old) {
  return busy(async () => {
    const batch = writeBatch(db);
    batch.update(sub('transfers', old.id), markDeleted(f, state.uid, old));
    batch.set(sub('changelog', uuid()), changelogEntry(f, state.uid, { entityType: 'TRANSFER', entityId: old.id, action: 'DELETED', now: Date.now() }));
    await batch.commit();
    state.undoing = null;
    state.notice = t().undone;
  });
}

function askRecord(payment) {
  state.confirming = payKey(payment);
  render();
}

function record(payment) {
  return busy(async () => {
    const id = uuid();
    const now = Date.now();
    const batch = writeBatch(db);
    batch.set(sub('transfers', id), newTransfer(f, state.uid, { ...payment, currency: state.group.baseCurrency, date: today(), now }));
    batch.set(sub('changelog', uuid()), changelogEntry(f, state.uid, { entityType: 'TRANSFER', entityId: id, action: 'CREATED', now }));
    await batch.commit();
    state.confirming = null;
    state.notice = t().recorded;
  });
}

// ── Loading the group ──────────────────────────────────────────────────────────────────────────────
function listen(name, into) {
  return onSnapshot(collection(db, 'groups', state.groupId, name), (snap) => {
    state[into] = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    decide();
  });
}

/** Members first: until I have one, the guest says who they are. */
function decide() {
  if (!state.group) return;
  state.screen = me() ? 'group' : 'who';
  document.title = `${state.group.name} · Owlsplit`;
  render();
}

async function openGroup() {
  unsubscribers.forEach((u) => u());
  unsubscribers = [
    onSnapshot(groupRef(), (snap) => { state.group = snap.data(); decide(); }),
    listen('members', 'members'),
    listen('expenses', 'expenses'),
    listen('transfers', 'transfers'),
  ];
}

async function start() {
  if (!ALPHABET.test(code)) { state.screen = 'invalid'; render(); return; }
  const config = await fetch('/__/firebase/init.json').then((r) => r.json());
  const app = initializeApp(config);
  db = getFirestore(app);
  const auth = getAuth(app);
  // The browser keeps the anonymous account: coming back later is the same guest.
  const user = await new Promise((resolve) => { const off = onAuthStateChanged(auth, (u) => { off(); resolve(u); }); });
  state.uid = (user ?? (await signInAnonymously(auth)).user).uid;
  const invite = await getDoc(doc(db, 'invites', code));
  if (!invite.exists()) { state.screen = 'invalid'; render(); return; }
  state.invite = invite.data();
  state.groupId = state.invite.groupId;
  // Already in it (an earlier visit): straight to the group. Otherwise the group cannot be read yet.
  try {
    const group = await getDoc(groupRef());
    if (group.exists() && (group.data().memberUids ?? []).includes(state.uid)) { await openGroup(); return; }
  } catch { /* Not a member yet. */ }
  state.screen = 'join';
  render();
}

for (const b of document.querySelectorAll('#lang button')) {
  b.addEventListener('click', () => {
    lang = b.dataset.lang;
    try { localStorage.setItem(LANG_KEY, lang); } catch { /* Not remembered; still switches. */ }
    render();
  });
}
render();
start().catch((e) => { console.error(e); state.screen = 'offline'; render(); });
