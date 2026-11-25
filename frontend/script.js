"use strict";

const $ = (id) => document.getElementById(id);
const state = {
  users: [], activeId: "", contactId: "", history: [], pending: [], connected: false,
  busy: false, conversationToken: 0, toastTimer: null
};

function getUser(id) { return state.users.find((u) => u.id === id); }
function nameFor(id) { return getUser(id)?.name || id; }
function initial(name) { return Array.from((name || "?").trim())[0]?.toUpperCase() || "?"; }
function setText(id, text) { $(id).textContent = text; }
function setBusy(value) {
  state.busy = value;
  $("registerBtn").disabled = value;
  $("deliverBtn").disabled = value || state.pending.length === 0;
  $("undoBtn").disabled = value || state.pending.length === 0;
  $("sendBtn").disabled = value || !canChat() || !$('messageInput').value.trim();
}
function canChat() { return !!state.activeId && !!state.contactId && state.activeId !== state.contactId; }
function toast(message, error = false) {
  const el = $("toast"); el.textContent = message; el.classList.toggle("error", error);
  el.classList.add("show"); clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => el.classList.remove("show"), 3300);
}
async function api(url, {method = "GET", form} = {}) {
  const options = {method, headers: {"Accept": "application/json"}};
  if (form !== undefined) {
    options.headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
    options.body = new URLSearchParams(form);
  }
  let response;
  try { response = await fetch(url, options); }
  catch { throw new Error("Cannot connect to the Java server. Start ChatWebServer in VS Code."); }
  const body = await response.json().catch(() => { throw new Error("Unexpected response from Java server."); });
  if (!response.ok) throw new Error(body?.error || `Request failed (${response.status})`);
  return body;
}
function connection(connected) {
  state.connected = connected;
  $("connectionDot").classList.toggle("online", connected);
  $("connectionDot").classList.toggle("offline", !connected);
  setText("connectionText", connected ? "Server connected" : "Server disconnected");
}
function createAvatar(user, size = "normal") {
  const ring = document.createElement("span"); ring.className = "avatar-ring";
  const avatar = document.createElement("span"); avatar.className = "avatar";
  avatar.textContent = initial(user?.name || user?.id);
  if (size === "large") { ring.style.width = "78px"; ring.style.height = "78px"; avatar.style.width = "72px"; avatar.style.height = "72px"; avatar.style.fontSize = "26px"; }
  ring.append(avatar); return ring;
}
function populateActiveSelect() {
  const select = $("activeUser"); select.replaceChildren();
  if (!state.users.length) {
    const opt = new Option("Create a user to begin", ""); select.add(opt); state.activeId = "";
  } else {
    if (!getUser(state.activeId)) state.activeId = state.users[0].id;
    for (const u of state.users) select.add(new Option(`${u.name}  ·  ${u.id}`, u.id));
    select.value = state.activeId;
  }
  const user = getUser(state.activeId);
  setText("activeAvatar", initial(user?.name));
  setText("railAvatar", initial(user?.name));
}
function renderContacts() {
  const list = $("contacts"); list.replaceChildren();
  const all = state.users.filter((u) => u.id !== state.activeId);
  const filter = $("contactSearch").value.toLowerCase().trim();
  const visible = all.filter((u) => `${u.name} ${u.id}`.toLowerCase().includes(filter));
  setText("contactCount", String(all.length));
  if (!visible.length) {
    const box = document.createElement("div"); box.className = "sidebar-empty";
    box.textContent = !state.users.length ? "No users yet. Click the + button to create your first user." :
      !all.length ? "Create one more user to start a conversation." : "No matching people found.";
    list.append(box); return;
  }
  for (const u of visible) {
    const button = document.createElement("button"); button.type = "button";
    button.className = `contact${state.contactId === u.id ? " active" : ""}`;
    button.setAttribute("aria-label", `Open conversation with ${u.name}`);
    button.append(createAvatar(u));
    const details = document.createElement("div"); details.className = "contact-info";
    const name = document.createElement("span"); name.className = "contact-name"; name.textContent = u.name;
    const id = document.createElement("span"); id.className = "contact-subtitle"; id.textContent = `@${u.id}`;
    details.append(name, id); button.append(details);
    const chev = document.createElement("span"); chev.className = "contact-chevron"; chev.textContent = "›"; button.append(chev);
    button.addEventListener("click", () => selectContact(u.id)); list.append(button);
  }
}
function renderQueue() {
  const list = $("queueList"); list.replaceChildren();
  setText("queueCount", `${state.pending.length} pending`);
  setText("pendingBadge", String(state.pending.length));
  if (!state.pending.length) {
    const empty = document.createElement("div"); empty.className = "queue-empty";
    empty.textContent = "No pending messages."; list.append(empty);
  }
  state.pending.forEach((m, i) => {
    const item = document.createElement("div"); item.className = "queue-item";
    const head = document.createElement("div"); head.className = "queue-item-head";
    const ident = document.createElement("b"); ident.textContent = m.messageId;
    const pos = document.createElement("span"); pos.className = "queue-position";
    pos.textContent = i === 0 ? "NEXT" : i === state.pending.length - 1 ? "MOST RECENT" : `ITEM ${i + 1}`;
    head.append(ident, pos);
    const text = document.createElement("p"); text.textContent = `${nameFor(m.senderId)} → ${nameFor(m.receiverId)}: ${m.text}`;
    item.append(head, text); list.append(item);
  });
  setBusy(state.busy);
}
function refreshHeader() {
  const user = getUser(state.contactId);
  if (canChat() && user) {
    setText("chatName", user.name); setText("chatSubtitle", `@${user.id}  ·  Chat simulator`);
    setText("chatAvatar", initial(user.name));
  } else {
    setText("chatName", "Your messages");
    setText("chatSubtitle", "Choose a conversation to get started");
    setText("chatAvatar", "✦");
  }
  $("messageInput").disabled = !canChat();
  $("messageInput").placeholder = canChat() ? `Message ${user.name}...` : "Choose someone to chat with...";
  $("sendBtn").disabled = !canChat() || state.busy || !$("messageInput").value.trim();
  $("welcome").hidden = canChat();
  $("messagesList").hidden = !canChat();
}
function renderHistory() {
  refreshHeader();
  const list = $("messagesList"); list.replaceChildren();
  if (!canChat()) return;
  if (state.history.length === 0) {
    const intro = document.createElement("div"); intro.className = "conversation-intro";
    intro.append(createAvatar(getUser(state.contactId), "large"));
    const heading = document.createElement("b"); heading.textContent = nameFor(state.contactId);
    const hint = document.createElement("span"); hint.textContent = "Say hello to start your conversation ✨";
    intro.append(heading, hint); list.append(intro); return;
  }
  const queuedIds = new Set(state.pending.map((m) => m.messageId));
  for (const m of state.history) {
    const mine = m.senderId === state.activeId;
    const row = document.createElement("div"); row.className = `message-row${mine ? " own" : ""}`;
    const bubble = document.createElement("div"); bubble.className = "bubble"; bubble.textContent = m.text;
    const meta = document.createElement("div"); meta.className = "bubble-meta";
    const msgId = document.createElement("span"); msgId.textContent = `${mine ? "You" : nameFor(m.senderId)} · ${m.messageId}`;
    meta.append(msgId);
    if (queuedIds.has(m.messageId)) {
      const queued = document.createElement("span"); queued.className = "queued-label"; queued.textContent = "Queued"; meta.append(queued);
    }
    row.append(bubble, meta); list.append(row);
  }
  requestAnimationFrame(() => { $("messagesArea").scrollTop = $("messagesArea").scrollHeight; });
}
async function loadConversation() {
  const token = ++state.conversationToken;
  if (!canChat()) { state.history = []; renderHistory(); return; }
  const a = state.activeId, b = state.contactId;
  try {
    const messages = await api(`/api/conversations?user1Id=${encodeURIComponent(a)}&user2Id=${encodeURIComponent(b)}`);
    if (token !== state.conversationToken || a !== state.activeId || b !== state.contactId) return;
    state.history = messages; renderHistory();
  } catch (err) { if (token === state.conversationToken) toast(err.message, true); }
}
async function reload() {
  const [users, pending] = await Promise.all([api("/api/users"), api("/api/messages/pending")]);
  state.users = users; state.pending = pending;
  populateActiveSelect();
  if (state.contactId === state.activeId || !getUser(state.contactId)) state.contactId = "";
  if (!state.contactId) state.contactId = state.users.find((u) => u.id !== state.activeId)?.id || "";
  renderContacts(); renderQueue(); await loadConversation();
}
function selectContact(id) {
  if (id === state.activeId) { toast("Select a different user to chat with.", true); return; }
  state.contactId = id; state.history = []; renderContacts(); renderHistory();
  $("app").classList.add("chat-open"); loadConversation();
}
function openModal() { $("modalBackdrop").hidden = false; $("newUserId").focus(); }
function closeModal() { $("modalBackdrop").hidden = true; }
function openTools() {
  $("drawerShade").hidden = false;
  $("toolsDrawer").classList.add("open"); $("toolsDrawer").setAttribute("aria-hidden", "false");
  $("closeTools").focus();
}
function closeTools() {
  $("toolsDrawer").classList.remove("open"); $("toolsDrawer").setAttribute("aria-hidden", "true");
  $("drawerShade").hidden = true;
}
async function runAction(action) {
  if (state.busy) return;
  setBusy(true);
  try { await action(); await reload(); connection(true); }
  catch (err) { toast(err.message, true); }
  finally { setBusy(false); }
}
function attachEvents() {
  ["newUserBtn", "welcomeNewUser", "navPeople"].forEach((id) => $(id).addEventListener("click", openModal));
  ["closeModal"].forEach((id) => $(id).addEventListener("click", closeModal));
  $("modalBackdrop").addEventListener("click", (e) => { if (e.target === $("modalBackdrop")) closeModal(); });
  ["toolsBtn", "navTools"].forEach((id) => $(id).addEventListener("click", openTools));
  $("drawerShade").addEventListener("click", closeTools); $("closeTools").addEventListener("click", closeTools);
  $("navChats").addEventListener("click", () => { closeTools(); $("app").classList.remove("chat-open"); });
  $("backToChats").addEventListener("click", () => $("app").classList.remove("chat-open"));
  $("activeUser").addEventListener("change", () => {
    state.activeId = $("activeUser").value;
    if (state.contactId === state.activeId) state.contactId = state.users.find((u) => u.id !== state.activeId)?.id || "";
    populateActiveSelect(); renderContacts(); loadConversation();
  });
  $("contactSearch").addEventListener("input", renderContacts);
  $("exactSearch").addEventListener("click", async () => {
    const id = $("contactSearch").value.trim();
    if (!id) { toast("Enter a user ID to search.", true); return; }
    try {
      const found = await api(`/api/users/search?id=${encodeURIComponent(id)}`);
      if (found.id === state.activeId) toast("That is your active user. Choose someone else.", true);
      else { selectContact(found.id); toast(`Found ${found.name}`); }
    } catch (err) { toast(err.message, true); }
  });
  $("registrationForm").addEventListener("submit", (e) => {
    e.preventDefault();
    const id = $("newUserId").value.trim(), name = $("newUserName").value.trim();
    if (!id || !name) return;
    runAction(async () => {
      const created = await api("/api/users", {method: "POST", form: {id, name}});
      closeModal(); $("registrationForm").reset(); toast(`${created.name} was added!`);
    });
  });
  $("messageInput").addEventListener("input", () => {
    $("sendBtn").disabled = state.busy || !canChat() || !$("messageInput").value.trim();
    $("messageInput").style.height = "auto";
    $("messageInput").style.height = `${Math.min($("messageInput").scrollHeight, 130)}px`;
  });
  $("messageInput").addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
      e.preventDefault(); $("composeForm").requestSubmit();
    }
  });
  $("composeForm").addEventListener("submit", (e) => {
    e.preventDefault(); if (!canChat()) return;
    const value = $("messageInput").value;
    if (!value.trim()) return;
    const senderId = state.activeId, receiverId = state.contactId;
    runAction(async () => {
      const sent = await api("/api/messages", {method: "POST", form: {senderId, receiverId, text: value}});
      $("messageInput").value = ""; $("messageInput").style.height = "auto";
      toast(`${sent.messageId} added to the outgoing queue`);
    });
  });
  $("deliverBtn").addEventListener("click", () => runAction(async () => {
    const result = await api("/api/messages/deliver-next", {method: "POST"});
    toast(result.messageId ? `Delivered ${result.messageId}` : result.message);
  }));
  $("undoBtn").addEventListener("click", () => runAction(async () => {
    const result = await api("/api/messages/undo", {method: "POST"});
    toast(result.messageId ? `Undid ${result.messageId}` : result.message);
  }));
  $("searchMessagesForm").addEventListener("submit", async (e) => {
    e.preventDefault(); const keyword = $("messageKeyword").value.trim(); if (!keyword) return;
    try {
      const result = await api(`/api/messages/search?keyword=${encodeURIComponent(keyword)}`);
      const box = $("searchResults"); box.replaceChildren();
      if (!result.length) { const p = document.createElement("p"); p.className = "quiet"; p.textContent = "No matching messages found."; box.append(p); return; }
      result.forEach((m) => {
        const hit = document.createElement("div"); hit.className = "search-hit";
        const id = document.createElement("b"); id.textContent = `${m.messageId} · ${nameFor(m.senderId)} → ${nameFor(m.receiverId)}`;
        const text = document.createElement("span"); text.textContent = m.text;
        hit.append(id, text); box.append(hit);
      });
    } catch (err) { toast(err.message, true); }
  });
  $("refreshChat").addEventListener("click", () => runAction(async () => { await reload(); toast("Conversation refreshed"); }));
  $("refreshUsers").addEventListener("click", () => runAction(async () => { await reload(); toast("Chats refreshed"); }));
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") { closeModal(); closeTools(); }
  });
}
async function initialize() {
  attachEvents(); renderHistory();
  try { await api("/api/health"); await reload(); connection(true); }
  catch (err) { connection(false); toast(err.message, true); }
}
document.addEventListener("DOMContentLoaded", initialize);
