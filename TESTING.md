# TapPass – test list before every release

Tick everything on a real phone before a version goes to Google Play.
If something fails: screenshot + "Settings → Developer → Show geo log", send to Claude.

## A. Install & first launch
- [ ] 1. Fresh install: 3 welcome screens show, swiping works, "Continue with Free" opens the wallet
- [ ] 2. App icon and dark splash screen look right
- [ ] 3. Rotating the phone keeps the app in portrait

## B. Cards
- [ ] 4. Add a card by **camera** (barcode card) – number and format correct
- [ ] 5. Add a card by **camera** (QR card, if you have one)
- [ ] 6. Add a card from a **gallery photo** (straight and sideways photo)
- [ ] 7. Add a card by **typing** the number + picking the store from suggestions
- [ ] 8. Free version: 4th card opens the paywall
- [ ] 9. Open a card: brand header, brightness goes to max, code readable
- [ ] 10. **Scan at a real till** – the shop accepts it
- [ ] 11. Delete a card (asks first)

## C. Geo-alerts
- [ ] 12. Turning Geo-alerts on asks for location "all the time" + notifications
- [ ] 13. "Make alerts reliable" shows all green after fixing settings
- [ ] 14. Geo log shows "built-in list: N stores" and "registered 18 stores"
- [ ] 15. **Phone locked, app closed:** walking to a store gives a notification with sound
- [ ] 16. Tapping the notification opens the right card
- [ ] 17. Second visit within 4 h: no repeat alert

## D. Other screens
- [ ] 18. Shopping list: add, tick, delete, survives closing the app
- [ ] 19. Activity shows cards shown + alerts
- [ ] 20. Light / dark mode both look right
- [ ] 21. Privacy policy opens and is the final text

## E. Payments (once connected)
- [ ] 22. Start trial with a test account – Pro unlocks
- [ ] 23. Cancel in Google Play – Pro turns off after the period
- [ ] 24. "Restore Purchases" on a second phone with the same Google account

## F. Release build only
- [ ] 25. Developer section is hidden
- [ ] 26. Version number increased
