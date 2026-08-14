#!/usr/bin/env node
// 官方 src/endpoints/google.js generateJWTToken / getProjectIdFromServiceAccount /
// getVertexAIAuth / getGoogleApiConfig → JSON fixture。
// 函数体逐字提取；打桩：readSecret、getAccessToken=固定串（full 模式 token 换取需网络）、
// GEMINI_SAFETY/VERTEX_SAFETY=[]、trimTrailingSlash（官方 util 同语义）。
// Date.now 冻结后生成 JWT，与 Kotlin 注入时间戳的 jwt(sa, now) 逐字对拍。
// 输出 engine/src/test/resources/diff/vertex-auth.json。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'vertex-auth.json');

const src = readFileSync(join(officialRef, 'src', 'endpoints', 'google.js'), 'utf8');

function extractFn(source, marker) {
    const start = source.indexOf(marker);
    if (start < 0) throw new Error(marker + ' not found');
    const parenStart = source.indexOf('(', start);
    let depth = 0, bodyStart = -1, inString = null, inRegex = false, inLineComment = false, inBlockComment = false;
    for (let i = parenStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (inRegex) { if (ch === '\\') { i++; continue; } if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; } if (ch === '/') inRegex = false; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; continue; }
        if (ch === '/') { let j = i - 1; while (j >= 0 && /\s/.test(source[j])) j--; const prev = source[j]; if (['=', '>', '(', ',', ':', '['].includes(prev)) { inRegex = true; continue; } }
        if (ch === '(') depth++;
        else if (ch === ')') { depth--; if (depth === 0) { let j = i + 1; while (j < source.length && /\s/.test(source[j])) j++; if (source[j] === '{') bodyStart = j; break; } }
    }
    if (bodyStart < 0) throw new Error('no body for ' + marker);
    let d = 0; inString = null; inRegex = false; inLineComment = false; inBlockComment = false;
    for (let i = bodyStart; i < source.length; i++) {
        const ch = source[i];
        if (inLineComment) { if (ch === '\n') inLineComment = false; continue; }
        if (inBlockComment) { if (ch === '*' && source[i + 1] === '/') { inBlockComment = false; i++; } continue; }
        if (inString) { if (ch === '\\') { i++; continue; } if (ch === inString) inString = null; continue; }
        if (inRegex) { if (ch === '\\') { i++; continue; } if (ch === '[') { while (i < source.length && source[i] !== ']') i++; continue; } if (ch === '/') inRegex = false; continue; }
        if (ch === '"' || ch === "'" || ch === '`') { inString = ch; continue; }
        if (ch === '/' && source[i + 1] === '/') { inLineComment = true; continue; }
        if (ch === '/' && source[i + 1] === '*') { inBlockComment = true; continue; }
        if (ch === '/') { let j = i - 1; while (j >= 0 && /\s/.test(source[j])) j--; const prev = source[j]; if (['=', '>', '(', ',', ':', '['].includes(prev)) { inRegex = true; continue; } }
        if (ch === '{') d++;
        else if (ch === '}') { d--; if (d === 0) return source.slice(start, i + 1); }
    }
    throw new Error('unbalanced ' + marker);
}

globalThis.nodeCrypto = crypto;
const secretStore = { makersuite: 'ms-key', vertexai: 'vertex-key', vertexai_service_account: null };
globalThis.API_MAKERSUITE = 'https://generativelanguage.googleapis.com';
globalThis.API_VERTEX_AI = 'https://us-central1-aiplatform.googleapis.com';
globalThis.SECRET_KEYS = { MAKERSUITE: 'makersuite', VERTEXAI: 'vertexai', VERTEXAI_SERVICE_ACCOUNT: 'vertexai_service_account' };
globalThis.readSecret = (directories, key, secretId = null) => secretStore[key] ?? '';
globalThis.GEMINI_SAFETY = [];
globalThis.VERTEX_SAFETY = [];
globalThis.trimTrailingSlash = (s) => String(s ?? '').replace(/\/+$/, '');
globalThis.getConfigValue = (key, def) => def;
globalThis.getAccessToken = async () => 'FAKE_ACCESS_TOKEN';

const jwtSrc = extractFn(src, 'export async function generateJWTToken(');
const projectSrc = extractFn(src, 'export function getProjectIdFromServiceAccount(');
const authSrc = extractFn(src, 'export async function getVertexAIAuth(');
const configSrc = extractFn(src, 'export async function getGoogleApiConfig(');

const jwtBody = jwtSrc.replace(/^export /, '').replace(/\bcrypto\./g, 'nodeCrypto.');
globalThis.generateJWTToken = new Function('return (' + jwtBody + ')')();
globalThis.getProjectIdFromServiceAccount = new Function('return (' + projectSrc.replace(/^export /, '') + ')')();
globalThis.getVertexAIAuth = new Function('return (' + authSrc.replace(/^export /, '') + ')')();
globalThis.getGoogleApiConfig = new Function('return (' + configSrc.replace(/^export /, '') + ')')();

const { privateKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: 2048,
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
});
const serviceAccount = {
    type: 'service_account',
    project_id: 'demo-project',
    private_key: privateKey,
    client_email: 'demo@demo.iam.gserviceaccount.com',
    client_id: '12345',
};

const FROZEN_MS = 1750000000000;
const realNow = Date.now;
Date.now = () => FROZEN_MS;
let jwt;
try {
    jwt = await globalThis.generateJWTToken(serviceAccount);
} finally {
    Date.now = realNow;
}
const projectId = globalThis.getProjectIdFromServiceAccount(serviceAccount);

async function configCase(id, body, apiKeyForCase) {
    secretStore.vertexai = apiKeyForCase ?? 'vertex-key';
    secretStore.vertexai_service_account = body.vertexai_auth_mode === 'full' ? JSON.stringify(serviceAccount) : null;
    const request = { user: { directories: {} }, body };
    const cfg = await globalThis.getGoogleApiConfig(request, body.model || 'gemini-2.5-pro', body.endpoint || 'generateContent');
    return { id, input: { ...body, apiKey: apiKeyForCase ?? 'vertex-key', accessToken: 'FAKE_ACCESS_TOKEN' }, url: cfg.url, headers: cfg.headers };
}

const cases = [
    { id: 'service_account', serviceAccount },
    { id: 'jwt', jwt, nowEpochSec: Math.floor(FROZEN_MS / 1000) },
    { id: 'project_id', projectId },
    await configCase('express_region_project', { api: 'vertexai', vertexai_auth_mode: 'express', vertexai_region: 'us-central1', vertexai_express_project_id: 'p', model: 'gemini-2.5-pro', endpoint: 'generateContent' }),
    await configCase('express_global_noproject', { api: 'vertexai', vertexai_auth_mode: 'express', vertexai_region: 'global', vertexai_express_project_id: '', model: 'gemini-2.5-pro', endpoint: 'generateContent' }),
    await configCase('full_service_account', { api: 'vertexai', vertexai_auth_mode: 'full', vertexai_region: 'us-central1', model: 'gemini-2.5-pro', endpoint: 'streamGenerateContent' }),
    await configCase('proxy', { api: 'vertexai', vertexai_auth_mode: 'express', reverse_proxy: 'https://proxy.example/', proxy_password: 'pp', model: 'gemini-2.5-pro', endpoint: 'generateContent' }),
];

writeFileSync(outFile, JSON.stringify({ generated: new Date().toISOString(), source: 'sillytavern-ref release', cases }, null, 2) + '\n');
console.log(`wrote ${outFile} (${cases.length} cases)`);
