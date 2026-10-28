# F-Droid dependency and licensing audit

**Reviewed:** 2026-10-28  
**Source revision used for dependency resolution:** `12123a7f50035327babe7fa529968b41f89a9048`  
**Scope:** Gradle runtime and compile dependency graphs, bundled font notices, app metadata and build configuration.

## Automated checks

The GitHub Actions workflow `F-Droid source audit` previously passed on the reviewed revision. It resolves `releaseRuntimeClasspath` and `releaseCompileClasspath`, checks the Fastlane metadata and literal fallback version values, and uploads the dependency graph. The workflow now also runs the Gradle License Report plugin (`com.github.jk1.dependency-license-report` 3.1.4) against the app project and uploads its generated license reports alongside the dependency graph. This workflow deliberately does **not** build an APK. The newly added report step has not yet been confirmed by a completed Actions run; treat its first output as a review artifact, not as an automatic compliance verdict.

The resolved Gradle report was downloaded and reviewed. The graph is dominated by AndroidX/Jetpack Compose and Kotlin/KotlinX; the distinct non-platform library families are Kizitonwose Calendar (`compose`, `core`, `data`), Markwon, CommonMark Java, Guava's small `listenablefuture` utility, JetBrains annotations, and JSpecify. No Firebase, Google Play Services, Crashlytics, advertising SDK, or analytics SDK appeared in the resolved release runtime/compile graphs. The presence of `com.google.guava:listenablefuture:1.0` is not a dependency on Google Play Services.

## License review

| Dependency or asset family | Version(s) in resolved graph | Known license | Review note |
| --- | --- | --- | --- |
| AndroidX and Jetpack Compose | Core 1.17.0, Activity Compose 1.10.1, Compose UI/Foundation 1.9.3, Material 3 1.4.0, Material Icons Extended 1.7.8, and transitive AndroidX modules | Apache-2.0 | Free software; keep upstream notices where required. |
| Kotlin standard library and KotlinX coroutines/serialization | Kotlin stdlib 2.4.21, coroutines 1.8.1, serialization 1.7.3 | Apache-2.0 | Free software. |
| Kizitonwose Calendar (`compose`, `core`, `data`) | 2.9.1 | MIT | `compose` pulls in `core` and `data`; include the family in the review, not just the direct module. |
| Markwon | 4.6.2 | Apache-2.0 | Markdown rendering library. |
| CommonMark Java | 0.13.0 (Markwon transitive dependency) | BSD-2-Clause | Preserve the upstream copyright/license notice as required. |
| Guava listenablefuture | 1.0 | Apache-2.0 | Small transitive utility; not Google Play Services. |
| JSpecify and JetBrains annotations | JSpecify 1.0.0; JetBrains annotations 23.0.0 (older annotations versions appear as constraints) | Apache-2.0 | Annotation-only support dependencies. |
| Bundled fonts | Inter, Syne, Bricolage Grotesque, Doto, Geist Mono | SIL OFL-1.1 | `app/src/main/assets/licenses/FONT_LICENSES.txt` contains credits and the OFL text. |
| App source | — | MIT | Root `LICENSE`. |

License references:
- [Kizitonwose Calendar](https://github.com/kizitonwose/Calendar) (MIT)
- [Markwon 4.6.2 metadata](https://central.sonatype.com/artifact/io.noties.markwon/core/4.6.2) (Apache-2.0)
- [CommonMark Java](https://github.com/commonmark/commonmark-java) (BSD-2-Clause)
- [Kotlin](https://github.com/JetBrains/kotlin) (Apache-2.0)
- [F-Droid Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/)

## Items still requiring human/source-level verification

1. **Artwork rights:** the maintainer confirms that both the launcher icon and phone screenshots were created by them; screenshots were captured on their own phone. This is maintainer-provided provenance, not an independent forensic verification. The assets are wired into Fastlane metadata. Keep this statement accurate if any artwork is replaced or derived from third-party work.
2. **Remaining transitive-license verification:** the known license families are Apache-2.0 for AndroidX/Jetpack, Kotlin/KotlinX, Guava `listenablefuture`, JSpecify and JetBrains annotations; MIT for Kizitonwose Calendar; Apache-2.0 for Markwon; and BSD-2-Clause for CommonMark Java. The workflow now generates an automated dependency-license report from Gradle metadata, but it still requires human review for missing, ambiguous, or unexpected license declarations. It is not by itself an SPDX/SBOM attestation or a byte-for-byte inspection of every downloaded JAR/AAR and POM. Review bundled third-party notices before official submission.
3. **F-Droid build:** no `fdroid build` or official F-Droid CI run has been performed. The recipe at `docs/fdroiddata/metadata/com.insomdroid.dislauncher.yml` is a draft for the separate `fdroiddata` repository, not a submitted merge request.
4. **Update checking:** the draft currently uses `UpdateCheckMode: Static` because the existing `v1.3.3` tag predates the literal Gradle version values. Revisit automatic tag-based updates after a future stable release tag contains those values.
5. **Inclusion timing:** F-Droid's current inclusion policy says an app should have at least three months of active development unless an exception applies (for example, demonstrated users). The F-Droid maintainers decide whether the project's history/users meet that condition.

## Current conclusion

No obvious non-free advertising, analytics, or Google Play Services dependency was found in the resolved Gradle graphs. The maintainer has stated that the icon and screenshots are their own work. The reviewed dependency families appear to use Apache-2.0, MIT, or BSD-2-Clause licenses. This remains a **preliminary pass**, not an F-Droid compliance sign-off: review of the generated per-dependency license report, review of bundled third-party notices, F-Droid build test, and official `fdroiddata` submission remain outstanding.
