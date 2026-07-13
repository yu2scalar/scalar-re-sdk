# ScalarRE SDK

Java SDK for **ScalarRE** (Scalar Reliable Event) — an exactly-once DB-to-DB
message transfer engine. The SDK builds, parses, and processes the event body
JSON exchanged with a ScalarRE server, so producer and consumer applications do
not hand-write that format.

- **Package:** `com.scalar.re.sdk`
- **Coordinates:** `com.scalar:scalar-re-sdk:0.9.0`
- **Requires:** JDK 17+
- **License:** Apache-2.0

## Install (local Maven)

The SDK is not published to a remote Maven repository yet. Clone this
repository and install it into your local Maven repository (`~/.m2`):

```bash
git clone https://github.com/yu2scalar/scalar-re-sdk.git
cd scalar-re-sdk
./gradlew publishToMavenLocal
```

## Use

Add `mavenLocal()` and the dependency to your Gradle build:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'com.scalar:scalar-re-sdk:0.9.0'
}
```

The DynamoDB and Cosmos storage helpers are optional and declared
`compileOnly`; if you use them, add the matching storage SDK to your own
runtime dependencies.

## Build & test

```bash
./gradlew build      # compile + unit tests
./gradlew javadoc    # API docs (doclint gate)
```

## Versioning

This repository is a source mirror of the SDK module maintained in the ScalarRE
monorepo. Releases are tagged `vX.Y.Z` and track the ScalarRE server version.
