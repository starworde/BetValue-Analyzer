import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import admin from "firebase-admin";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function readServiceAccount() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON GitHub Secret");
  }
  const decoded = raw.trim().startsWith("{")
    ? raw
    : Buffer.from(raw.trim(), "base64").toString("utf8");
  const parsed = JSON.parse(decoded);
  if (!parsed.client_email || !parsed.private_key || !parsed.project_id) {
    throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON does not look like a Firebase service account JSON");
  }
  return parsed;
}

async function main() {
  const serviceAccount = readServiceAccount();
  const projectId = process.env.FIREBASE_PROJECT_ID || serviceAccount.project_id;
  const rulesPath = path.resolve(__dirname, "..", "firestore.rules");
  const source = fs.readFileSync(rulesPath, "utf8");

  if (!admin.apps.length) {
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId,
    });
  }

  await admin.securityRules().releaseFirestoreRulesetFromSource(source);
  console.log(`[rules] Firestore rules deployed for ${projectId} from ${path.relative(process.cwd(), rulesPath)}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
