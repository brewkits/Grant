package dev.brewkits.grant.compose

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the state-to-dialog decision that drives every dialog this module renders.
 *
 * **Why this exists.** Until now `grant-compose` had exactly one test —
 * `assertNotNull(AppGrant.CAMERA)` — which cannot fail and asserted nothing about the module.
 * The real logic sat inside `derivedStateOf { }` blocks in `@Composable` functions, where a
 * `commonTest` runner cannot reach it. That is a placement problem, not a platform limit: the
 * decision is a pure function of three booleans, so extracting it (see `resolveDialogKind`)
 * makes it testable with no Compose host at all.
 *
 * **What is actually at risk here is the ORDERING, not the mapping.** Each branch in isolation
 * is obvious; what matters is which one wins when a state sets more than one flag. Reversing
 * rationale and settings-guide compiles, renders, and looks reasonable — it just sends a user
 * who could still be persuaded straight to a Settings screen they did not need. That is the
 * shape of Issue #55 and Issue #41. These tests fix the precedence so a future reordering
 * fails here instead of on a user's phone.
 */
class GrantDialogKindTest {

    // ── GrantDialog / GrantGroupDialog ────────────────────────────────────────────────────

    @Test
    fun invisible_state_shows_nothing_even_when_other_flags_are_set() {
        // isVisible is the master switch: a stale rationale/settings flag left over from a
        // previous flow must not resurrect a dialog.
        assertEquals(
            DialogKind.None,
            resolveDialogKind(isVisible = false, showRationale = true, showSettingsGuide = true),
        )
    }

    @Test
    fun rationale_wins_over_settings_when_both_are_set() {
        // The precedence that matters. Rationale is the recoverable step; the settings guide is
        // terminal. Reversing these is a silent UX regression, not a compile error.
        assertEquals(
            DialogKind.Rationale,
            resolveDialogKind(isVisible = true, showRationale = true, showSettingsGuide = true),
        )
    }

    @Test
    fun settings_guide_shows_only_when_rationale_is_not_requested() {
        assertEquals(
            DialogKind.Settings,
            resolveDialogKind(isVisible = true, showRationale = false, showSettingsGuide = true),
        )
    }

    @Test
    fun visible_with_no_flag_shows_nothing() {
        // Reachable in practice: the handler sets isVisible while a request is in flight, before
        // any outcome is known. Rendering a dialog here would flash an empty prompt.
        assertEquals(
            DialogKind.None,
            resolveDialogKind(isVisible = true, showRationale = false, showSettingsGuide = false),
        )
    }

    // ── GrantAndServiceDialog ─────────────────────────────────────────────────────────────

    @Test
    fun service_invisible_state_shows_nothing_even_when_every_flag_is_set() {
        assertEquals(
            ServiceDialogKind.None,
            resolveServiceDialogKind(
                isVisible = false,
                showRationale = true,
                showPermissionSettings = true,
                showServiceSettings = true,
            ),
        )
    }

    @Test
    fun service_rationale_wins_over_both_settings_kinds() {
        assertEquals(
            ServiceDialogKind.Rationale,
            resolveServiceDialogKind(
                isVisible = true,
                showRationale = true,
                showPermissionSettings = true,
                showServiceSettings = true,
            ),
        )
    }

    @Test
    fun permission_settings_wins_over_service_settings() {
        // A missing permission blocks the feature outright; a disabled service is recoverable
        // without any grant. Prompting for the service first asks the user to fix the lesser
        // problem and leaves the feature still broken afterwards.
        assertEquals(
            ServiceDialogKind.PermissionSettings,
            resolveServiceDialogKind(
                isVisible = true,
                showRationale = false,
                showPermissionSettings = true,
                showServiceSettings = true,
            ),
        )
    }

    @Test
    fun service_settings_shows_when_it_is_the_only_flag() {
        assertEquals(
            ServiceDialogKind.ServiceSettings,
            resolveServiceDialogKind(
                isVisible = true,
                showRationale = false,
                showPermissionSettings = false,
                showServiceSettings = true,
            ),
        )
    }

    @Test
    fun service_visible_with_no_flag_shows_nothing() {
        assertEquals(
            ServiceDialogKind.None,
            resolveServiceDialogKind(
                isVisible = true,
                showRationale = false,
                showPermissionSettings = false,
                showServiceSettings = false,
            ),
        )
    }
}
