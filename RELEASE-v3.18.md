# BFF v3.18 — ignore automatic answers

All automatic SMS replies use BFF-Answer:. Incoming messages with that prefix are ignored before command processing, case-insensitively and allowing leading whitespace. They cannot start an alarm, request location, or generate another reply. English and Portuguese location, waiting and error replies use the same prefix.

Every independently sent fallback segment also carries the prefix. Both participating BFF phones must be updated; older versions may interpret BFF-Answer as a command.

Regression coverage includes actual SMS receiver parsing, mixed-case/whitespace prefixes, location/alarm decisions, standalone Unicode segments and intact map links. Existing bff where silent commands retain their behavior.
