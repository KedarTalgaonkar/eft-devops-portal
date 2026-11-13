# Brand assets

1. Get the official Euronet logo (white / light version, for the navy sidebar) from the Euronet brand or marketing team.
   Do not take it from the web.
2. Save it here, for example as `euronet-logo.svg`.
3. In `brand.json` set `"logo": "brand/euronet-logo.svg"`.
4. Optional: replace `public/favicon.ico` with the Euronet favicon.

Until a logo is configured, the sidebar shows a text wordmark. Region and product name are also set in `brand.json`.
Brand colours are tokens at the top of `src/styles.scss` (`--eu-navy-*`, `--eu-teal`, `--eu-saffron`). Align them with
the brand guide if they differ.
