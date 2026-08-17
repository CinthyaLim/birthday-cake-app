# The published artifact

`reading-the-cake.html` is the source for the condensed, shareable web version of the learning
module, published at:

<https://claude.ai/code/artifact/79a82d5a-1350-4094-9e5a-86f24a4d5702>

It is **derived**, not authoritative. The markdown in `docs/learning/` is the source of truth —
when the architecture changes, update the modules first, then fold the change into this file.

## Updating it

Edit this file, then republish it to the **same URL** (pass that URL explicitly, or the publish
creates a second artifact rather than updating this one). The title, `Reading the Cake`, and the
🎂 favicon stay the same across redeploys — readers find the tab by its icon.

## Why it's one large file

The page is self-contained by requirement: the artifact host blocks requests to any external
domain, so fonts, styles and scripts are all inlined. Most of the ~490 KB is two base64 `@font-face`
payloads. Nothing here is fetched at runtime.
