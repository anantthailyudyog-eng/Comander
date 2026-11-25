const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_stark_01";
const BOB_UID = "bob_banner_02";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied across all Jarvis OS collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("macros").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("documents").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      category: "Preference",
      content: "Prefers dark obsidian HUD",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Owner CRUD: MemoryNote create, partial update, delete, and shadow/immutable rejections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const memRef = aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1");

  await assertSucceeds(
    memRef.set({
      userId: ALICE_UID,
      category: "Preference",
      content: "Address user as Sir.",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    memRef.update({
      content: "Address user as Boss.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").get());

  // Reject ghost field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_2").set({
      id: "mem_2",
      userId: ALICE_UID,
      category: "Idea",
      content: "Should fail because @DocumentId id is forbidden",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    memRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_future").set({
      userId: ALICE_UID,
      category: "Idea",
      content: "Future timestamp tampering",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(memRef.delete());
});

test("Owner CRUD: PriorityTask, CustomMacro, and WriterDocument", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");
  await assertSucceeds(
    taskRef.set({
      userId: ALICE_UID,
      title: "Calibrate Mark VII suit telemetry",
      priority: "HIGH",
      completed: false,
      durationSeconds: 300,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    taskRef.update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    taskRef.update({
      priority: "INVALID_LEVEL",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const macroRef = aliceDb.collection("users").doc(ALICE_UID).collection("macros").doc("macro_1");
  await assertSucceeds(
    macroRef.set({
      userId: ALICE_UID,
      name: "Night Ops",
      voiceTrigger: "activate night ops",
      promptCommand: "Switch to Focus/Stealth Mode and summarize my tasks.",
      iconKey: "bolt",
      createdAt: created,
      updatedAt: created,
    })
  );

  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_1");
  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      title: "Arc Reactor Calibration Report",
      docType: "REPORT",
      tone: "Technical",
      content: "# Telemetry Nominal\nAll systems green.",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation: Bob cannot read or mutate Alice's subcollections", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const created = new Date(Date.now() - 5000);
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").set({
      userId: ALICE_UID,
      title: "Top Secret Stark Blueprint",
      priority: "CRITICAL",
      completed: false,
      durationSeconds: 0,
      createdAt: created,
      updatedAt: created,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("bob_inject").set({
      userId: ALICE_UID,
      title: "Spoofed",
      priority: "LOW",
      completed: false,
      durationSeconds: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
