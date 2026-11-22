import { generateVAPIDKeys } from 'web-push'
import fs from 'fs'
import path from 'path'

const keys = generateVAPIDKeys()

console.log('=== VAPID Keys Generated ===')
console.log('PUBLIC_KEY:', keys.publicKey)
console.log('PRIVATE_KEY:', keys.privateKey)
console.log('')

// Write to .env.local for development
const envPath = path.resolve('.env.local')
const envContent = `VAPID_PUBLIC_KEY=${keys.publicKey}
VAPID_PRIVATE_KEY=${keys.privateKey}
`

fs.writeFileSync(envPath, envContent)
console.log('Written to .env.local')

// Also output for Cloudflare Pages secrets
console.log('')
console.log('=== For Cloudflare Pages Dashboard ===')
console.log('Go to Pages > dbus-donostia > Settings > Environment variables')
console.log('Add:')
console.log('  VAPID_PUBLIC_KEY (Variable):', keys.publicKey)
console.log('  VAPID_PRIVATE_KEY (Secret):', keys.privateKey)
console.log('')
console.log('For key rotation, also add:')
console.log('  VAPID_ROTATE_TOKEN (Secret): <generate-secure-random-string>')
