---
description: Work through the next unchecked task in TASKS.md for the LockIn Android project. Reads the task list, finds the next [ ] item, executes it fully, marks it [x], and reports what was done and what comes next.
---

When the user types `/nexttask`, do the following:

1. Read `TASKS.md` from the project root
2. Find the first unchecked task `[ ]` in order
3. Load any relevant skills from `.agents/skills/` based on the task type:
   - Android/Kotlin code → load `android-kotlin.md`
   - Payment/Razorpay → load `razorpay-android.md`
   - VPN/Service work → load `vpn-service.md`
4. Execute the task completely — generate all required files, full and compilable
5. Mark the task as `[x]` in `TASKS.md`
6. Report:
   - What was built
   - Files created or modified
   - What the next task is
7. Ask: "Ready for the next task? Type /nexttask to continue."

### Rules
- Never skip a task — work in strict order
- Never generate partial files — always complete and compilable
- If a task requires a user decision, pause and ask ONE specific question before proceeding
- If a task involves a secret (API key, password), add a placeholder and note it clearly
- After every code file, add a brief comment block at the top explaining what the file does
