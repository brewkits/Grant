// Raise Mocha's per-test timeout for the browser (Karma) test runs.
//
// Mocha defaults to 2000 ms, and that limit wraps the whole test — it fires before
// kotlinx-coroutines-test's own `runTest(timeout = ...)` ever gets a chance to. WebGrantDelegateTest
// deliberately declares 3–8 s budgets because it awaits *real* browser Promises
// (getUserMedia, Notification.requestPermission, getCurrentPosition) that are outside the
// coroutine test scheduler and cannot be virtually advanced.
//
// Without this the suite is load-dependent: it passed when `jsBrowserTest` was run on its own,
// and failed under a full `./gradlew build allTests` with
//   "Timeout of 2000ms exceeded. For async tests and hooks, ensure done() is called"
// — a flaky red CI that has nothing to do with the code under test.
//
// 20 s is comfortably above the longest declared budget (8 s) so the coroutine-level timeout
// stays the one that actually reports a failure, which is the one with the useful message.
// Merge rather than assign: `config.set({client: {...}})` REPLACES the whole `client`
// object, and the Kotlin/JS plugin has already put `client.args` there — that is where a
// `--tests "..."` filter is passed to the runner. Overwriting it would silently disable test
// filtering for this module.
config.set({
    client: Object.assign({}, config.client, {
        mocha: Object.assign({}, (config.client || {}).mocha, { timeout: 20000 })
    })
});
