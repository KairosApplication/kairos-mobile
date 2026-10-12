import { readFileSync } from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, writeBatch, setDoc, updateDoc, runTransaction } from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kairos',
    firestore: { rules: readFileSync('../../firestore.rules', 'utf8') }
  });
});
beforeEach(async () => { await env.clearFirestore(); });
after(async () => { await env?.cleanup(); });
const db = (uid) => env.authenticatedContext(uid, { email: uid + '@example.com' }).firestore();
const profile = (uid, cpf = '12345678901') => ({
  uid, name: 'Ana', lastName: 'Silva', birthDate: '2000-01-01',
  cpf, email: uid + '@example.com', zipCode: '12345678', plan: 'basico'
});
function create(database, uid, data = profile(uid)) {
  const batch = writeBatch(database);
  batch.set(doc(database, 'users', uid), data);
  batch.set(doc(database, 'cpfClaims', data.cpf), { uid });
  return batch.commit();
}
test('owner creates profile atomically and reads it', async () => {
  await assertSucceeds(create(db('ana'), 'ana'));
  await assertSucceeds(getDoc(doc(db('ana'), 'users', 'ana')));
});
test('owner edits names while identity and personal identifiers stay immutable', async () => {
  await assertSucceeds(create(db('ana'), 'ana'));
  const ref = doc(db('ana'), 'users', 'ana');
  await assertSucceeds(updateDoc(ref, { name: 'Maria Alice', lastName: 'de Souza' }));
  for (const changes of [{ uid: 'bob' }, { cpf: '99999999999' }, { birthDate: '1999-01-01' },
    { zipCode: '99999999' }, { plan: 'admin' }, { password: 'secret' }, { name: '' }, { lastName: '' }]) {
    await assertFails(updateDoc(ref, changes));
  }
  await assertFails(updateDoc(doc(db('bob'), 'users', 'ana'), { name: 'Bob' }));
});
test('email updates require the verified Authentication email of the same owner', async () => {
  await assertSucceeds(create(db('ana'), 'ana'));
  await assertFails(updateDoc(doc(db('ana'), 'users', 'ana'), { email: 'new@example.com' }));
  const unverified = env.authenticatedContext('ana', { email: 'new@example.com', email_verified: false }).firestore();
  await assertFails(updateDoc(doc(unverified, 'users', 'ana'), { email: 'new@example.com' }));
  const verified = env.authenticatedContext('ana', { email: 'new@example.com', email_verified: true }).firestore();
  await assertSucceeds(updateDoc(doc(verified, 'users', 'ana'), { email: 'new@example.com' }));
  const otherOwner = env.authenticatedContext('bob', { email: 'new@example.com', email_verified: true }).firestore();
  await assertFails(updateDoc(doc(otherOwner, 'users', 'ana'), { email: 'new@example.com' }));
});
test('unauthenticated access and listing denied', async () => {
  await assertFails(create(env.unauthenticatedContext().firestore(), 'ana'));
  await assertFails(getDocs(collection(db('ana'), 'users')));
});
test('other users cannot read or overwrite profile', async () => {
  await assertSucceeds(create(db('ana'), 'ana'));
  await assertFails(getDoc(doc(db('bob'), 'users', 'ana')));
  await assertFails(setDoc(doc(db('bob'), 'users', 'ana'), profile('ana')));
});
test('password, missing fields and forged email denied', async () => {
  await assertFails(create(db('ana'), 'ana', { ...profile('ana'), password: 'secret' }));
  const missing = profile('ana'); delete missing.plan;
  await assertFails(create(db('ana'), 'ana', missing));
  await assertFails(create(db('ana'), 'ana', { ...profile('ana'), email: 'other@example.com' }));
});
test('claim required and duplicate CPF denied', async () => {
  await assertFails(setDoc(doc(db('ana'), 'users', 'ana'), profile('ana')));
  await assertSucceeds(create(db('ana'), 'ana'));
  await assertFails(create(db('bob'), 'bob'));
});
test('orphan claims, profile updates and arbitrary collections denied', async () => {
  await assertFails(setDoc(doc(db('ana'), 'cpfClaims', '12345678901'), { uid: 'ana' }));
  await assertSucceeds(create(db('ana'), 'ana'));
  await assertFails(updateDoc(doc(db('ana'), 'users', 'ana'), { plan: 'admin' }));
  await assertFails(setDoc(doc(db('ana'), 'secrets', 'x'), { value: 'x' }));
});
test('transaction can read missing claim but cannot inspect another owners claim', async () => {
  await assertSucceeds(getDoc(doc(db('ana'), 'cpfClaims', '12345678901')));
  await assertSucceeds(create(db('ana'), 'ana'));
  await assertFails(getDoc(doc(db('bob'), 'cpfClaims', '12345678901')));
});
test('Android transaction sequence succeeds and can be retried without overwriting', async () => {
  const database = db('ana');
  const userRef = doc(database, 'users', 'ana');
  const claimRef = doc(database, 'cpfClaims', '12345678901');
  const save = () => runTransaction(database, async transaction => {
    if ((await transaction.get(userRef)).exists()) return;
    await transaction.get(claimRef);
    transaction.set(userRef, profile('ana'));
    transaction.set(claimRef, { uid: 'ana' });
  });
  await assertSucceeds(save());
  await assertSucceeds(save());
});
