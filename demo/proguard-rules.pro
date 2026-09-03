# R8 configuration for the Grant demo app.
#
# This file exists to VALIDATE the library, not to work around it. grant-core ships no
# consumer rules, on the theory that it needs none: GrantRequestActivity and
# GrantInitializer are declared in the library manifest, and R8 keeps manifest-referenced
# components automatically.
#
# Building this app with minification proves or disproves that theory. If a rule ever has
# to be added below to make the permission flow work, that rule belongs in a
# consumer-rules.pro inside grant-core instead — because every consuming app would need it
# too, and they cannot be expected to discover it themselves.
#
# Deliberately empty of Grant-specific keeps.

# Full mode plus access modification: the most aggressive configuration a consuming app is
# likely to use, so the library is tested against the worst realistic case rather than the
# default.
-allowaccessmodification

# Keep the demo's own entry point readable in stack traces during manual verification.
-keepattributes SourceFile,LineNumberTable
