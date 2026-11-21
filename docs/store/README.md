# Store preparation assets

These are drafts for a future owner-approved submission. Nothing in this directory has been uploaded to a store.

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

Artwork is part of this GPL-3.0-only repository. These assets do not imply store acceptance or physical-device certification. The maintainer confirmed developer **utrost** and the [public Textr issue tracker](https://github.com/utrost/Textr/issues) as privacy/support contact. Standard F-Droid signing is selected; see [F-Droid preparation](../fdroid/README.md). Play account declarations, Play signing choices, final assets and publication remain owner-dependent.
