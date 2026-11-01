/**
 * auth.js -- client-side demo authentication.
 *
 * IMPORTANT: this is a static, no-backend prototype. Users and passwords are
 * stored in the browser's localStorage in plain text. This is fine for an
 * academic demo but is NOT secure and must never be used to guard real data.
 * A real deployment would authenticate against a server with hashed
 * passwords, not the browser's local storage.
 */
(function (global) {
  "use strict";

  const USERS_KEY = "sudar_users_v1";
  const SESSION_KEY = "sudar_session_v1";

  function loadUsers() {
    const raw = localStorage.getItem(USERS_KEY);
    if (raw) {
      try { return JSON.parse(raw); } catch (e) { /* fall through to reseed */ }
    }
    const seeded = [
      { username: "admin", password: "Admin@123", role: "ADMINISTRATOR", fullName: "District Control Room Administrator" }
    ];
    localStorage.setItem(USERS_KEY, JSON.stringify(seeded));
    return seeded;
  }

  function saveUsers(users) {
    localStorage.setItem(USERS_KEY, JSON.stringify(users));
  }

  function findUser(username) {
    return loadUsers().find(u => u.username.toLowerCase() === String(username).toLowerCase());
  }

  function createUser(username, password, fullName) {
    username = String(username).trim();
    if (!username || !password) return { ok: false, message: "Username and password are required." };
    if (findUser(username)) return { ok: false, message: "That username is already registered." };

    const users = loadUsers();
    users.push({ username, password, role: "OPERATOR", fullName: fullName || username });
    saveUsers(users);
    return { ok: true };
  }

  function login(username, password) {
    const user = findUser(username);
    if (!user || user.password !== password) {
      return { ok: false, message: "Incorrect username or password." };
    }
    const session = { username: user.username, role: user.role, fullName: user.fullName, loginAt: new Date().toISOString() };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    return { ok: true, session };
  }

  function currentSession() {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    try { return JSON.parse(raw); } catch (e) { return null; }
  }

  function logout() {
    sessionStorage.removeItem(SESSION_KEY);
  }

  /** Call at the top of any protected page; redirects to login if not authenticated. */
  function requireSession() {
    const session = currentSession();
    if (!session) {
      window.location.href = "login.html";
      return null;
    }
    return session;
  }

  global.SudarAuth = { login, createUser, currentSession, logout, requireSession, findUser };
})(window);
