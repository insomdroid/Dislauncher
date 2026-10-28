# F-Droid dependency and licensing audit

**Reviewed:** 2026-10-28  
**Source revision used for dependency resolution:** `12123a7f50035327babe7fa529968b41f89a9048`  
**Scope:** Gradle runtime and compile dependency graphs, bundled font notices, app metadata and build configuration.

## Automated checks

The GitHub Actions run [#9](https://github.com/insomdroid/Dislauncher/actions/runs/40684698352) passed on 2026-10-28 at commit `c1adc4175d01b5422aaa33150527d9b07ad720b5`. It resolved `releaseRuntimeClasspath` and `releaseCompileClasspath`, generated the Gradle License Report (`com.github.jk1.dependency-license-report` 3.1.4), validated Fastlane metadata and fallback version values, and uploaded `fdroid-dependency-report`. A follow-up check now compares CommonMark's Maven POM metadata with the license-report output. The separate [F-Droid recipe smoke test workflow](../../.github/workflows/fdroid-recipe-test.yml) builds the exact commit pinned by the draft recipe and checks the resulting package/version; it does not upload the generated APK. This is a source-build smoke test, not a full official F-Droid build-server run. The generated report is committed under [`docs/reports/dependency-license/`](../reports/dependency-license/).

The resolved Gradle graph is dominated by AndroidX/Jetpack Compose and Kotlin/KotlinX; distinct non-platform library families include Kizitonwose Calendar (`compose`, `core`, `data`), Markwon, CommonMark Java, Guava's small `listenablefuture` utility, JetBrains annotations, and JSpecify. No Firebase, Google Play Services, Crashlytics, advertising SDK, or analytics SDK appeared in the resolved release runtime/compile graphs. The presence of `com.google.guava:listenablefuture:1.0` is not a dependency on Google Play Services. The checked-in HTML license report contains 107 dependency entries: 104 report Apache-2.0 variants and three Kizitonwose Calendar modules report MIT. The report incorrectly labels `com.atlassian.commonmark:commonmark:0.13.0` as Apache-2.0. Maven Central's artifact metadata identifies it as BSD 2-Clause, consistent with the CommonMark Java project's license. A new CI step explicitly checks the artifact and parent POM to guard against this mismatch.

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
2. **Bundled third-party notices:** `app/src/main/assets/licenses/` currently contains the font OFL notice, but no separate notice file for the third-party Java/Kotlin libraries. Before submission, add the relevant upstream copyright/license notices for the MIT Calendar modules, BSD-2-Clause CommonMark, and Apache-2.0 components where required, and ensure the notices are included in the app distribution. The automated report reads Gradle/POM metadata; it is not an SPDX/SBOM attestation or a byte-for-byte inspection of every downloaded JAR/AAR and POM.
3. **F-Droid build:** the new recipe smoke-test workflow has been added and triggered; its result must be checked. It builds the recipe's pinned source commit with Gradle on a GitHub-hosted runner, but does not reproduce the full official F-Droid build-server environment. The recipe at `docs/fdroiddata/metadata/com.insomdroid.dislauncher.yml` remains a draft for the separate `fdroiddata` repository, not a submitted merge request.
4. **Update checking:** the draft currently uses `UpdateCheckMode: Static` because the existing `v1.3.3` tag predates the literal Gradle version values. Revisit automatic tag-based updates after a future stable release tag contains those values.
5. **Inclusion timing:** F-Droid's current inclusion policy says an app should have at least three months of active development unless an exception applies (for example, demonstrated users). The F-Droid maintainers decide whether the project's history/users meet that condition.

## Current conclusion

No obvious non-free advertising, analytics, or Google Play Services dependency was found in the resolved Gradle graphs. CommonMark's BSD 2-Clause license is now identified from Maven Central metadata and guarded by CI. The maintainer has stated that the icon and screenshots are their own work. This remains a **preliminary pass**, not an F-Droid compliance sign-off: check the new smoke-test result, add and package third-party library notices, run a full F-Droid build-server test, and submit the recipe to the official `fdroiddata` repository.
