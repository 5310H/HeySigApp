# HeySig UX Improvement Plan

## Executive summary

The app has solid technical depth, but the current experience is not friendly for a normal user. The main UX issue is that the app exposes advanced research and engineering concepts directly to the user without translating them into clear tasks, defaults, or safe onboarding.

The clearest examples are the Noise and Sleep features:

- Noise is described as a six-level configuration system with calibration, thresholds, margin, confirmation, cooldown, and protected programs. This reads like a lab tool, not a finishable user feature.
- Sleep is described as a schedule/routine state machine with alarms, override logic, reminder eligibility, and App/Android lifecycle handling. This is a complex operational feature that should be presented as a guided routine, not as a raw settings panel.
- The documentation consistently states that many features are experimental, pending validation, or source-locked behind acceptance gates. That makes the product feel uncertain rather than trustworthy.

The improvement goal should be: make core tasks understandable in under one minute, remove hidden requirements, and provide safe defaults for the 80% of users who just want the app to work without reading the architecture docs.

## Review findings

### 1. The app assumes the user understands the model

Current docs require the user to understand terms like:

- calibration thresholds
- hysteresis margin
- stale samples
- confirmation counts
- cooldown
- protected sleep program
- draft vs saved state
- review and apply
- automatic preview
- manual override
- source lock / physical acceptance

A user should not need to know these concepts to use Noise or Sleep. The app should convert them into plain language and preserve the expert options behind an advanced panel.

### 2. Critical setup requirements are buried in technical notes

Several docs describe required conditions that are not obvious in the UI:

- app must stay open and unlocked
- microphone permission disclosure must be accepted
- built-in microphone is used
- background/locked-screen behavior is not implemented
- sleep reminders require explicit enablement and valid saved choices
- noise calibration must be performed on the same device
- current volume and sound conditions affect results
- manual settings and sleep override priority can block automation

These are not setup blockers in themselves, but they are currently hidden and can cause the app to feel broken or random.

### 3. Features are organized around engineering flow, not user goals

The project currently organizes around technical concepts (Noise setup, Calibrate, Measure, Details & preview) instead of user goals ("reduce background noise," "sleep through the night," "keep my daily routine").

That is a major usability gap.

### 4. The product reads like a prototype, not a polished system

The wording "experimental," "pending acceptance," "not yet available," and "source-locked" appears repeatedly. This is accurate for engineering status, but it undermines trust if exposed to a user without a controlled onboarding path.

The UX should make this distinction obvious:

- a feature is a guided trial mode
- a feature is ready for general use
- a feature is limited and requires setup or conditions

## UX principles to apply

1. Start with user outcomes, not system internals.
2. Explain every permission, condition, and limitation in plain language.
3. Default to safe, reversible actions.
4. Hide expert settings behind Advanced mode.
5. Use progressive disclosure: basic, guided, advanced.
6. Show a single, clear status per feature.
7. Make the next step obvious.
8. Make the app tell users what it is doing and why.
9. If a feature is unavailable, state exactly what is missing and how to fix it.
10. Separate setup from ordinary use.

## Recommendations by feature

### Noise

Problem: Noise is a technical configuration tool presented as a standard feature. A user should not need to understand thresholds, ranges, margins, or confirmation counts before using it.

Recommended UX changes:

- Replace the current setup model with a guided wizard:
  - Step 1: choose a goal (quiet environment, conversation, louder settings, bedtime mode)
  - Step 2: map programs to noise levels
  - Step 3: run a short sample in a real environment
  - Step 4: review suggested thresholds
  - Step 5: enable automatic noise switching
- Default to a simple preset: quiet / normal / loud. Only advanced users should see custom thresholds.
- Show plain-English labels such as:
  - "Quiet mode recommended"
  - "This feature needs a microphone and app permission"
  - "Automatic switching is off until calibration is complete"
- Add a non-destructive preview mode that simulates the decision without changing hearing-aid settings.
- Add a clear status ribbon:
  - "Ready"
  - "Needs calibration"
  - "Sampling"
  - "Paused"
  - "Blocked: mic permission required"
- Explain when the feature is unavailable due to device state, permission, or limited acceptance.

### Sleep

Problem: Sleep is expressed as a rule engine and routine mode rather than a simple bedtime routine. A user wants: "At night, switch to Sleep program. In the morning, return to my normal program." They do not want to manage alarms, override logic, and Android scheduling semantics.

Recommended UX changes:

- Replace the raw configuration with two guided flows:
  - "Set a bedtime routine"
  - "Set a wake-up routine"
- Use simple settings:
  - bedtime time
  - sleep program
  - wake time
  - return program
  - reminder option
- Show the actual effect in plain English before save:
  - "At 10:30 PM, switch to Sleep program. At 7:00 AM, restore Normal program."
- Put advanced settings behind an advanced section, such as:
  - reminder timing
  - override behavior
  - noise blocking
  - device-specific scheduling complexity
- Add a quick test mode for one-time confirmation without saving a permanent schedule.
- Provide a clear status card:
  - "Routine enabled"
  - "Next switch: 10:30 PM"
  - "Wake routine active"
  - "Paused pending review"

## Detailed plan

### Phase 1: UX diagnosis and user task mapping (week 1)

Goals:

- identify the actual user tasks
- remove engineering language from the user-facing flow
- create design principles

Tasks:

- map the top user actions into a simple journey:
  - set up device
  - choose a program
  - configure sleep
  - configure noise
  - review status
  - save and restore settings
- run a short walkthrough with 2-3 non-technical users and document confusion points
- identify which screens can be simplified or hidden behind advanced controls
- consolidate language across README, quick-start docs, and UI labels

Deliverables:

- user-task map
- problem list by feature
- revised wording guide
- screen-by-screen UX audit

Success criteria:

- a first-time user can tell what Noise and Sleep do in plain language without reading docs
- major setup steps are understandable without engineering terminology

### Phase 2: redesign the first-run experience (week 2-3)

Goals:

- create a friendly setup flow
- reduce early confusion
- make the app safe by default

Tasks:

- add a first-run welcome flow with a simple setup checklist
- present a three-tier model: Basic, Guided, Advanced
- create a setup summary screen with clear status and next actions
- require confirmation for sensitive or irreversible actions
- make permissions, microphone use, and background restrictions explicit before activation
- add a "What this does" explanation under each major feature

Deliverables:

- onboarding flow
- status cards
- plain-language tooltips and helper copy
- one-page feature explanations

Success criteria:

- a user can complete a minimal setup without reading the manual
- every feature states what it does, what it needs, and when it will run

### Phase 3: redesign the Noise experience (week 3-5)

Goals:

- remove technical complexity from the primary flow
- keep advanced controls available but hidden by default

Tasks:

- convert Noise to a guided workflow with 3 modes: Basic, Calibrate, Advanced
- add a default preset and one-click calibration start
- create a "preview before apply" flow
- replace technical labels with plain-language explanations
- add status helper text and trapped validation messages
- add a recovery path when calibration fails or measurements are invalid

Deliverables:

- simplified Noise screen
- calibration wizard
- preview result panel
- plain-language validation copy

Success criteria:

- a new user can complete a simple noise setup in under 5 minutes
- failure states explain why setup failed and what to do next

### Phase 4: redesign the Sleep experience (week 4-6)

Goals:

- make bedtime and wake routines understandable and reliable
- surface only the necessary decisions by default

Tasks:

- replace raw settings with bedtime and wake routines
- create a confirmation summary before saving schedule
- add a test run option for a one-time schedule trial
- provide a visible next event summary and current state
- separate routine behavior from advanced override logic
- add clear state transitions: off, active, pending, blocked

Deliverables:

- sleep routine dashboard
- bedtime/wake configuration flow
- test mode
- status overview

Success criteria:

- a user can understand their configured bedtime routine in a single glance
- a user can test a routine without committing to a full schedule

### Phase 5: polish and validation (week 6-8)

Goals:

- ensure the flow works in real-world user conditions
- measure clarity and task completion

Tasks:

- run usability review with 5-8 users
- test common tasks in the real app flow
- improve copy, navigation, and defaults based on observed errors
- verify large-font, accessibility, and round-watch layouts
- review privacy and permissions wording for clarity
- remove any setup language that implies guaranteed functionality before acceptance

Deliverables:

- usability report
- revised copy deck
- final UX spec
- release checklist for accessibility and clarity

Success criteria:

- at least 80% of testers can complete the main tasks without docs
- new users know exactly what Noise and Sleep do before enabling them

## Suggested interface patterns

### Simple status card pattern

Each feature should have a card with:

- feature name
- state: Ready / Needs setup / Paused / Off / Blocked
- short description
- primary action
- secondary action
- last updated time

### Preview before action

Before changing a hearing-aid-related setting, show:

- what will happen
- when it will happen
- what conditions are required
- how to cancel

### Plain-language explanation pattern

Every advanced setting should have a helper panel like:

- What this does
- When it runs
- What you need to allow
- Limits and warnings

## Immediate next steps

The most important next actions are:

1. Rewrite the first-run experience in plain language.
2. Add a guided Noise wizard instead of exposing the advanced configuration model directly.
3. Add a simple Sleep routine dashboard instead of raw settings and internal logic.
4. Hide advanced technical settings behind a clearly labeled Advanced section.
5. Add public status messaging for every blocked or incomplete feature.

## Recommended product message

Instead of exposing the current engineering language, the product should say something like:

> Noise automatically adjusts programs based on the environment. It uses your microphone and a short calibration step to learn your typical quiet and loud settings. Sleep helps you switch to a bedtime program and return to your normal program at wake time.

This makes the app understandable, trustworthy, and much easier to use.

## Implementation update — October 21, 2026

The primary phone flows now apply the immediate recommendations:

- First use explains the setup sequence and links to a Getting started checklist.
  The checklist remains available in Settings and separates everyday controls
  from optional voice, Sleep and Noise setup. It requests no permissions.
- Sleep has a status dashboard, numbered setup/reminder/try-now steps, a
  confirmation summary before saving, visible prerequisites, notification
  recovery, and expandable troubleshooting. The saved routine editor is collapsed
  by default so reminder controls and one-time actions are easier to reach. Try-now uses the existing saved
  targets and confirmation/executor; it does not arm a schedule. Unsaved screen
  choices are not used for a program switch.
- Noise has a guided program → calibration → preview path, a readable status
  without truncation, permission/lifecycle guidance and recovery routes. Numeric
  measurement controls and detailed status remain in Advanced screens.
- Existing UI smoke routes are updated for the new navigation and include the
  checklist. No storage migration, fitting change, new permission or automatic
  hearing-aid action is introduced.

Some recommendations in this plan need correction to match implemented and
validated behavior. Noise automatic switching remains disabled even after
calibration. Quiet/normal/loud presets cannot imply measured thresholds or
verified program choices; the existing six-level model and saved mappings are
preserved. Sleep notifications are reminders requiring a confirmed switch,
not scheduled automatic playback. Phone hands-free can run in an explicitly
started bounded foreground-service session while locked; noise capture remains
foreground-only. Screen-off watch wake-phrase listening is not implemented.
The proposed product message above is aspirational and must not be used as a
claim for this build.

External usability sessions, real phone/watch acceptance, overnight reminder
reliability and store release review remain required. The week-based phases
are a roadmap, not evidence that those activities were completed.
