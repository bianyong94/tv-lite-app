# Global Vision TV UI Audit

Date: 2026-06-26
Surface: `tv-lite-app`
Mode: Combined audit

## Screenshots

1. `01-home-real.png` - Home screen with real content
2. `03-search-real.png` - Search screen default state
3. `07-detail-tap.png` - Detail screen after opening the first card

## Step List

1. Open app on home screen - Health: mixed
2. Enter search screen - Health: weak
3. Open first title detail screen - Health: mixed

## Audit Scope

- Android TV home, search, and detail flow
- Focus, hierarchy, information density, and large-screen readability
- Screenshot-based accessibility and interaction review only

## User Goal And Accessibility Target

- User goal: quickly browse, judge content value, search, and start playback with a remote
- Accessibility target: clear focus, readable hierarchy from sofa distance, low-friction D-pad navigation, obvious primary action

## Strengths

- Home uses real poster imagery, which gives stronger browse value than text-first tiles.
- Search and detail both keep a single obvious top task instead of splitting attention across many competing controls.
- Detail screen exposes the most important playback facts in one zone: title, description, source count, episode count, and primary play action.

## UX Risks

- The visual language is inconsistent between screens. Home feels like a poster wall, search becomes a dashboard row, and detail turns into a text-heavy info sheet. The product does not feel like one coherent TV app.
- Home over-relies on the left rail. The selected category is clear, but the main content area has no strong headline, section title, or explanatory frame, so the right side reads like a generic asset dump.
- Card overlays are crowded and unstable. Labels, ratings, year, type, and title all sit on top of posters, which hurts legibility and weakens the image itself.
- Search wastes a large amount of above-the-fold space. The giant input box dominates the screen while the results row sits low and leaves most of the canvas empty.
- Search ranking cards look like data panels rather than media browsing objects. They communicate score, but not why a user should enter the title.
- Detail screen makes the synopsis visually heavier than the play action. The eye lands on the paragraph block first, not on the action path.
- The source and episode selector state is too compressed. `源 1 / 13 · 选集 1 / 33` tells the user there is complexity, but not how to operate it next.

## Accessibility Risks

- Focus indication is inconsistent in meaning. On home the selected left-nav pill is obvious, but on search the focused input outline dominates so strongly that it visually overpowers all downstream actions.
- Several poster overlays sit on busy imagery with semi-transparent dark chips. This creates likely contrast failures, especially for small metadata and white text over bright poster regions.
- Sofa-distance readability is uneven. Large titles are readable, but secondary metadata on cards and long synopsis text blocks are dense for a TV context.
- Search uses horizontal card rows with clipped continuation on the right edge. Without a stronger affordance than a small arrow, some users may not infer that more items continue off-screen.
- Remote-only discoverability is weak for source switching on detail. The label exists, but the interactive entry path is not obvious from the screenshot alone.

## Opportunity Areas

- Rebuild one shared TV design system across all three screens: same card anatomy, same chip style, same spacing rhythm, same focus treatment.
- Convert home from “grid of posters” to “curated shelf” thinking: stronger section headers, fewer visible metadata fields, more emphasis on one primary hero or featured rail.
- Shrink the search bar and move more useful content upward. The first screen should show input plus immediately actionable search suggestions or result shelves.
- Simplify poster overlays. Keep at most one badge plus title on-card, and move the rest of the metadata into focused or secondary states.
- Promote the play path on detail. The CTA, current source, and current episode should read as one action cluster, with synopsis demoted in weight.
- Turn source and episode selection into explicit remote-friendly controls instead of passive text status.

## Evidence Limits And Verification Gaps

- This audit is based on screenshots captured in the current run only.
- Playback screen, source switch dialog, and episode picker were not fully audited in this pass.
- Full accessibility compliance, motion behavior, and long-session navigation ergonomics still require live D-pad walkthrough and playback-state checks.

## Recommendations

1. Start redesign from navigation and card anatomy, not from colors. The bigger issue is structure and focus priority, not palette alone.
2. Make home, search, and detail share one component grammar so the app stops feeling like three separate templates.
3. Reduce on-poster metadata by at least half and reserve dense metadata for focused or detail states.
4. Rework search into a browse-first TV search flow: smaller field, stronger suggestion modules, clearer result hierarchy.
5. Rebuild detail around a single playback control strip: play, source, episode, progress/history, then synopsis below.
