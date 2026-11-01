# Material Symbols Outlined

Dislauncher uses selected Android VectorDrawable assets from Google's official Material Symbols Outlined collection.

- Source: https://github.com/google/material-design-icons
- Style: Material Symbols Outlined
- License: Apache License 2.0 (see LICENSE in this directory)
- Icon assets are stored in `app/src/main/res/drawable/ic_symbol_*.xml`.

The original vector tint attribute is removed from the copied drawable files so Jetpack Compose can apply each screen's semantic theme color through `Icon(tint = ...)`. The icon geometry and Outlined design are retained.

Material Symbols are used for interface actions and navigation. Third-party app icons, the custom weather illustrations, and the Home clock visualizations remain separate by design.
