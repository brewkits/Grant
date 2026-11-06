# Independent Desktop artifact consumer

This is a separate Gradle build, not a root subproject. Grant dependencies resolve only
from the three staged Maven repositories, using the published BOM without explicit module
versions. No project dependencies, composite substitution, or `mavenLocal()` are used.

From the Grant repository root on an Apple Silicon Mac:

```shell
./gradlew :grant-core:publishKotlinMultiplatformPublicationToMavenCentralLocalRepository \
  :grant-core:publishJvmPublicationToMavenCentralLocalRepository \
  :grant-desktop:publishAllPublicationsToMavenCentralLocalRepository \
  :grant-bom:publishAllPublicationsToMavenCentralLocalRepository
./gradlew -p verification/desktop-consumer -PgrantVersion=2.7.0-alpha01 createDistributable
open -n verification/desktop-consumer/build/compose/binaries/main/app/GrantDesktopConsumer.app \
  --args --out=/tmp/grant-desktop-consumer.txt
cat /tmp/grant-desktop-consumer.txt
```

The report must contain `BRIDGE=LOADED` and `RESULT=PASS`. This checks artifact resolution,
BOM constraints, native resource packaging, bridge registration and native status reads.
It does not prove that the user has granted consent or that a notarized distribution works.

For consent verification, launch the app through `open -n` with either `--request-camera`
or `--request-mic` and a fresh output path. A human must answer the system prompt. Record
Allow and Don't Allow independently for each permission, plus relaunch and revocation in
System Settings. Never synthesize the consent click or infer it from the launching terminal.
Use the app's bundle ID `dev.brewkits.grant.desktopconsumer` when intentionally resetting
only this test app's consent for another run. Do not reset other applications' permissions.

The generated app is a local development package. Signing/notarization and sandboxed
distribution remain separate release gates in `grant-desktop/README.md`.
