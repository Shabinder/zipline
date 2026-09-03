rootProject.name = "zipline-root"

include(":zipline")
// zipline-api-validator + zipline-cli dropped for the Kotlin 2.4 rebuild: api-validator uses FIR
// compiler internals (FirResult/outputs/session) that changed in 2.4, and neither is consumed by
// the SoundBound app (validator/cli are dev tools). Keeps the 2.4 rebuild contained.
include(":zipline-cli")
include(":zipline-bytecode")
include(":zipline-cryptography")
include(":zipline-gradle-plugin")
include(":zipline-kotlin-plugin")
include(":zipline-kotlin-plugin-tests")
include(":zipline-loader")
include(":zipline-loader-testing")
include(":zipline-profiler")
include(":zipline-testing")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
