# F-Droid dependency and licensing audit

**Reviewed:** 2026-10-28  
**Source revision used for dependency resolution:** `62882102952dc8177d29251b5c67af1a1a7aed66`  
**Scope:** Gradle runtime and compile dependency graphs, bundled font notices, app metadata and build configuration.

## Automated checks

The GitHub Actions workflow `F-Droid source audit` passed on the reviewed revision. It resolved the `releaseRuntimeClasspath` and `releaseCompileClasspath`, checked the Fastlane metadata and literal fallback version values, and uploaded the dependency report. This workflow deliberately does **not** build an APK.

The resolved report includes AndroidX/Jetpack Compose, Kotlin/KotlinX, Kizitonwose Calendar, Markwon, CommonMark, Guava's small `listenablefuture` utility, and JSpecify. The report did not show Firebase, Google Play Services, Crashlytics, advertising SDKs, or analytics SDKs. The presence of `com.google.guava:listenablefuture:1.0` is not the same as a dependency on Google Play Services.

## License review

| Dependency or asset family | Version(s) in resolved graph | Known license | Review note |
| --- | --- | --- | --- |
| AndroidX and Jetpack Compose | Core 1.17.0, Activity Compose 1.10.1, Compose UI/Foundation 1.9.3, Material 3 1.4.0, Material Icons Extended 1.7.8, and transitive AndroidX modules | Apache-2.0 | Free software; keep upstream notices where required. |
| Kotlin standard library and KotlinX coroutines/serialization | Kotlin stdlib 2.4.21, coroutines 1.8.1, serialization 1.7.3 | Apache-2.0 | Free software. |
| Kizitonwose Calendar | 2.9.1 | MIT | Upstream repository includes its MIT license. |
| Markwon | 4.6.2 | Apache-2.0 | Markdown rendering library. |
| CommonMark Java | 0.13.0 (Markwon transitive dependency) | BSD-2-Clause | Preserve the upstream copyright/license notice as required. |
| Guava listenablefuture | 1.0 | Apache-2.0 | Small transitive utility; not Google Play Services. |
| JSpecify and JetBrains annotations | JSpecify 1.0.0; annotations 23.0.0 | Apache-2.0 | Annotation-only support dependencies. |
| Bundled fonts | Inter, Syne, Bricolage Grotesque, Doto, Geist Mono | SIL OFL-1.1 | `app/src/main/assets/licenses/FONT_LICENSES.txt` contains credits and the OFL text. |
| App source | — | MIT | Root `LICENSE`. |

License references:
- [Kizitonwose Calendar](https://github.com/kizitonwose/Calendar) (MIT)
- [Markwon 4.6.2 metadata](https://central.sonatype.com/artifact/io.noties.markwon/core/4.6.2) (Apache-2.0)
- [CommonMark Java](https://github.com/commonmark/commonmark-java) (BSD-2-Clause)
- [Kotlin](https://github.com/JetBrains/kotlin) (Apache-2.0)
- [F-Droid Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/)

## Items still requiring human/source-level verification

1. **Artwork rights:** the maintainer confirms that the phone screenshots were captured on their own phone. They are not third-party promotional screenshots. This provenance is documented here; the screenshot files are already wired into Fastlane metadata. The launcher icon is a separate asset: its authorship/source and redistribution rights still need to be confirmed and documented before submission.
2. **Full license automation:** the successful Gradle report enumerates resolved coordinates but is not itself a machine-readable license report. The licenses above are a preliminary review of direct dependencies and the main transitive families, not a signed-off scan of every transitive artifact's POM/license file. F-Droid maintainers will perform their own review.
3. **F-Droid build:** no `fdroid build` or official F-Droid CI run has been performed. The recipe at `docs/fdroiddata/metadata/com.insomdroid.dislauncher.yml` is a draft for the separate `fdroiddata` repository, not a submitted merge request.
4. **Update checking:** the draft currently uses `UpdateCheckMode: Static` because the existing `v1.3.3` tag predates the literal Gradle version values. Revisit automatic tag-based updates after a future stable release tag contains those values.
5. **Inclusion timing:** F-Droid's current inclusion policy says an app should have at least three months of active development unless an exception applies (for example, demonstrated users). The F-Droid maintainers decide whether the project's history/users meet that condition.

## Current conclusion

No obvious non-free advertising, analytics, or Google Play Services dependency was found in the resolved Gradle graphs, and the primary libraries/assets reviewed have permissive or redistributable licenses. This is a **preliminary pass**, not yet a complete F-Droid compliance sign-off. The launcher-icon provenance, remaining transitive-license verification, F-Droid build test, and official `fdroiddata` submission remain outstanding.
