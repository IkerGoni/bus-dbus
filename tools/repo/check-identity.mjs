#!/usr/bin/env node
// Guard de identidad pública: falla si un fichero versionado (lockfiles incluidos)
// o la identidad de HEAD contiene un correo fuera de la lista aprobada o una ruta
// de usuario local. No imprime el valor encontrado: solo fichero, línea y huella.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

const APPROVED = new Set([
  '8208093+ikergoni@users.noreply.github.com',
  'dev.mail.igoni@gmail.com',
]);
// Dominio en minúsculas: evita falsos positivos de Kotlin como `this@composed.sharedElement`.
const EMAIL = /[A-Za-z0-9._%+-]+@[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,24}(?![A-Za-z0-9])/g;
// `/Users/...` y `/Users/<user>` son marcadores de documentación, no rutas reales.
const LOCAL_PATH = /\/Users\/(?!\.\.\.|<)[A-Za-z0-9._-]+|\/home\/(?!runner\b)[a-z][a-z0-9._-]*\//g;

const git = (...args) => execFileSync('git', args, { encoding: 'utf8', maxBuffer: 1 << 28 });
const fingerprint = (value) => createHash('sha256').update(value).digest('hex').slice(0, 12);

const findings = [];
for (const file of git('ls-files', '-z').split('\0').filter(Boolean)) {
  let text;
  try {
    text = readFileSync(file, 'utf8');
  } catch {
    continue; // submódulos o enlaces rotos
  }
  if (text.includes('\0')) continue; // binario
  text.split('\n').forEach((line, i) => {
    for (const [kind, rx] of [['correo', EMAIL], ['ruta local', LOCAL_PATH]]) {
      for (const m of line.matchAll(rx)) {
        if (kind === 'correo' && APPROVED.has(m[0].toLowerCase())) continue;
        findings.push(`${file}:${i + 1} ${kind} no aprobado (sha256:${fingerprint(m[0])})`);
      }
    }
  });
}

for (const value of git('log', '-1', '--no-mailmap', '--format=%ae%n%ce').split('\n').filter(Boolean)) {
  if (!APPROVED.has(value.toLowerCase())) findings.push(`HEAD: identidad Git no aprobada (sha256:${fingerprint(value)})`);
}

if (findings.length) {
  console.error(`Identidad pública: ${findings.length} hallazgo(s)\n${findings.join('\n')}`);
  process.exit(1);
}
console.log('Identidad pública: 0 correos no aprobados y 0 rutas locales en el árbol versionado.');
