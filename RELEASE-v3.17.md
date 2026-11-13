# BFF v3.17

If Android denies SIM metadata access while dividing a Unicode SMS reply, BFF falls back to standalone messages of at most 70 UTF-16 units. This avoids the failing multipart/carrier lookup without requesting another permission. The fallback preserves the text, surrogate pairs and word boundaries (including the current map URL). Normal platform division remains unchanged when available.

Fallback replies may arrive as separate SMS messages and incur per-message charges. Sent callbacks still aggregate the parts; success means modem acceptance, not delivery confirmation.

Regression tests cover the captured SecurityException, Unicode preservation, intact map link and normal division. Physical reply delivery must be confirmed separately.
