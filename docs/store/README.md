# Store preparation assets

These are drafts for a future owner-approved submission. Nothing in this directory has been uploaded to a store.

F-Droid's [upstream English and German metadata](../../fastlane/metadata/android/) reuses the listing copy and synthetic screenshots in the supported source layout. The unsigned APK build path is documented in [release signing](../releasing.md). F-Droid build-recipe validation and submission remain separate open issues [#64](https://github.com/utrost/Textr/issues/64) and [#66](https://github.com/utrost/Textr/issues/66).

- [Readiness and build/signing instructions](readiness.md)
- [English/German listing copy](listing.md)
- [Data safety draft](data-safety.md)
- [Actual emulator screenshots](../screenshots.md)
- [512×512 icon](icon.png), derived from the existing launcher vector; [SVG source](icon.svg)
- [1024×500 feature graphic](feature-graphic.png); [SVG source](feature-graphic.svg)

PNG exports use RGB with 8-bit channels. Recreate them with ImageMagick and DejaVu Sans installed:

```sh
convert -background none -depth 8 docs/store/icon.svg PNG24:docs/store/icon.png
convert -background none -depth 8 docs/store/feature-graphic.svg PNG24:docs/store/feature-graphic.png
```

Artwork is part of this GPL-3.0-only repository. These assets do not imply store acceptance or physical-device certification. Developer identity, support contact, account declarations, final screenshots/assets and signing choices remain subject to owner review.
