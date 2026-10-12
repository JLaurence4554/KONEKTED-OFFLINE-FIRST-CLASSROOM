#!/usr/bin/env node
// apply-schema.mjs
//
// Applies schema_supabase.sql (same folder) to the Supabase project whose
// connection details are in .env.local, then verifies the 12 KONEKTED tables.
// This replaces the manual "paste into SQL Editor" step.
//
//   node apply-schema.mjs           apply the schema, then verify
//                                   (safe to re-run: everything is IF NOT EXISTS)
//   node apply-schema.mjs --check   only verify; changes nothing
//
// The database password is read from .env.local and is never printed.

import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { Client } from 'pg';

const here = path.dirname(fileURLToPath(import.meta.url));
const checkOnly = process.argv.includes('--check');

const EXPECTED_TABLES = [
  'users', 'classes', 'class_members', 'lessons', 'lesson_attachments',
  'quizzes', 'questions', 'quiz_results', 'attendance', 'join_requests',
  'auth_tokens', 'login_attempts',
];

function loadEnv() {
  const file = path.join(here, '.env.local');
  if (!existsSync(file)) {
    console.error('MISSING: backend/supabase/.env.local');
    console.error('Create it (see the example in SUPABASE_SETUP.md, section B) and paste in');
    console.error('your Session pooler connection details from the Supabase dashboard -> Connect.');
    process.exit(1);
  }
  const env = {};
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    if (line.trim().startsWith('#')) continue;
    const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (!m) continue;
    let v = m[2];
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
    env[m[1]] = v;
  }
  return env;
}

const env = loadEnv();
const needed = ['KONEKTED_DB_HOST', 'KONEKTED_DB_PORT', 'KONEKTED_DB_NAME', 'KONEKTED_DB_USER', 'KONEKTED_DB_PASS'];
const unfilled = needed.filter((k) => !env[k] || env[k].includes('YOUR-'));
if (unfilled.length) {
  console.error('Fill in these lines in backend/supabase/.env.local first: ' + unfilled.join(', '));
  process.exit(1);
}

const client = new Client({
  host: env.KONEKTED_DB_HOST,
  port: Number(env.KONEKTED_DB_PORT),
  database: env.KONEKTED_DB_NAME,
  user: env.KONEKTED_DB_USER,
  password: env.KONEKTED_DB_PASS,
  ssl: { rejectUnauthorized: true },
  connectionTimeoutMillis: 20000,
});

console.log(`Connecting: host=${env.KONEKTED_DB_HOST} port=${env.KONEKTED_DB_PORT} `
  + `db=${env.KONEKTED_DB_NAME} user=${env.KONEKTED_DB_USER} (password hidden)`);

try {
  await client.connect();
} catch (e) {
  console.error('\nCOULD NOT CONNECT: ' + (e.message || e));
  console.error('Check the host, user and password in .env.local, and that you are using the');
  console.error('Session pooler host (port 5432), not the Direct connection one.');
  process.exit(1);
}

try {
  let encrypted = false;
  try {
    encrypted = (await client.query('SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()')).rows[0]?.ssl === true;
  } catch { /* some poolers hide this; not important */ }
  console.log(encrypted
    ? 'Connected. Connection is encrypted.'
    : 'Connected, but the server did not report encryption (Supabase should say encrypted).');

  if (!checkOnly) {
    const sql = readFileSync(path.join(here, 'schema_supabase.sql'), 'utf8');
    await client.query(sql); // one multi-statement run; every statement is IF NOT EXISTS
    console.log('Schema applied (schema_supabase.sql).');
  }

  const have = (await client.query(
    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
  )).rows.map((r) => r.table_name);
  const missing = EXPECTED_TABLES.filter((t) => !have.includes(t));
  if (missing.length) {
    console.error('\nMISSING TABLES: ' + missing.join(', '));
    process.exit(2);
  }

  const rls = (await client.query(
    "SELECT count(*)::int AS n FROM pg_class c JOIN pg_namespace nsp ON nsp.oid = c.relnamespace "
    + "WHERE nsp.nspname = 'public' AND c.relkind = 'r' AND c.relrowsecurity"
  )).rows[0].n;

  const users = (await client.query('SELECT COUNT(*)::int AS n FROM users')).rows[0].n;
  console.log(`All ${EXPECTED_TABLES.length} KONEKTED tables are there (${rls} locked for the Supabase API).`);
  console.log(`Accounts in this database: ${users}`);
  console.log('\nOK - this database is ready for the KONEKTED server.');
} catch (e) {
  console.error('\nFAILED: ' + (e.message || e));
  process.exit(1);
} finally {
  await client.end().catch(() => {});
}
