# Todd's Card Vault

An Android app for scanning, valuing and organizing a football card collection. Everything stays on the phone. It uses only free data sources.

## What it does
- **Scan:** You photograph the front and back. The phone reads the text offline with Google ML Kit and fills in the player, year, brand, card number, team and position. You confirm or correct the details, then save.
- **PSA slabs:** The app reads the cert number from the slab label. PSA's free cert API then fills in the exact card and grade.
- **Value:** Comes from eBay's free Browse API. It's the median asking price of matching Buy It Now listings, with outliers removed. Graded cards only match listings with the same grade, and raw cards exclude graded listings. You can enter your own value, such as a sold price you looked up, and it overrides the estimate.
- **Automatic updates:** Every card is re-priced once a day in the background. 500 cards is far under eBay's free limit of 5,000 calls a day.
- **Trend:** Each card's value is saved daily and charted (1M / 3M / 1Y / All), with 7-, 30- and 90-day and all-time changes.
- **Inventory:** Sorts by value, player, year or date added. You can search, and filter by brand, team, position, year, player and graded/raw.
- **Card view:** Tap the card to flip between front and back. Buttons link to eBay sold listings and the PSA cert page for real sold prices.
- **Backup:** Saves cards, photos and history to a single .zip file on a USB drive, SD card or cloud folder, and restores from it.

## Free keys to set up (Settings screen)
1. **eBay:** Create a free account at developer.ebay.com, then create a **Production** keyset. Paste the **App ID** and **Cert ID** into Settings.
2. **PSA (optional, for slabs):** Sign in at psacard.com/publicapi and copy the access token into Settings.

## Installing
Every push to `main` builds an APK through GitHub Actions. To install it:
1. Go to **Actions → latest "Build APK" run → Artifacts** and download the `CardVault-N` zip.
2. Unzip it and open the `.apk` on the phone. Allow "install unknown apps" for your browser or file manager if Android asks.

The repo includes a fixed signing key (`app/debug.keystore`). Because of it, each new build installs over the old one and keeps the data. Don't uninstall the app unless you have a backup, because uninstalling deletes its data.

## Limits
- eBay's free API only shows **asking prices**, not sold prices. Use the "Check sold prices" links on a card for actual sales.
- Text recognition works best in good light with the card filling the frame. Always check what it filled in.
