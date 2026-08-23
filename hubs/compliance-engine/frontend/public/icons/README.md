# Icons

The `icon.svg` is the source icon for IncoKalk.

To generate PNG icons for PWA compatibility, run:

```bash
npx sharp-cli -i icon.svg -o icon-192.png resize 192 192
npx sharp-cli -i icon.svg -o icon-512.png resize 512 512
```

Or use any SVG-to-PNG converter. The SVG is used directly as the manifest icon
since modern browsers support SVG icons.
