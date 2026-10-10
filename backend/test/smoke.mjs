import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(new URL('.', import.meta.url).pathname, '..');
const server = fs.readFileSync(path.join(root, 'src/server.js'), 'utf8');
const routeros = fs.readFileSync(path.join(root, 'src/routeros.js'), 'utf8');
assert.match(server, /helmet/);
assert.match(server, /CORS_ORIGIN must be an explicit allowlist/);
assert.match(server, /BACKUP_EXPORT_FAILED/);
assert.match(server, /rr\.user_id=\$2/);
assert.match(routeros, /rejectUnauthorized:pin\?false:!this\.allowInsecureTls/);
assert.match(server, /REGISTRATION_CLOSED/);
assert.match(server, /generateBatch/);
assert.doesNotMatch(server, /randomBytes\(8\)\.toString\("base64url"\)/);
assert.match(server, /\/api\/cards\/scan/);
assert.match(server, /cards\/audit/);
assert.ok(fs.existsSync(path.join(root, 'package-lock.json')));
console.log('Backend smoke checks passed');
