# Test-Schlüssel

`test.jks` signiert Testversionen, solange im Repository keine Secrets
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`) hinterlegt sind. Passwort: `aze-test-key`.

Der Schlüssel ist absichtlich öffentlich und nur für Tests gedacht.
Sobald die Secrets angelegt sind, wird mit dem eigenen Schlüssel signiert –
dann muss die Testversion einmal deinstalliert werden.
