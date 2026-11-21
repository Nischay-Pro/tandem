# Trust and security

Extension sync is designed so that losing its database discloses no financial
details:

- Records arrive already sealed by the device. The service can't decrypt them.
- Each budget's planning key is shared only in wrapped form; unwrapping it needs
  the budget's Actual encryption key, which never leaves members' devices.
- Every request must carry an Actual session token. The service asks your Actual
  server which budgets that session can open, so a budget id alone grants nothing.
- Writes must name the revision they replace, so concurrent edits surface as
  conflicts instead of overwriting each other.

The service can still see budget ids, record ids and types, sizes and timing.
Keep it on the same host or network as Actual and behind HTTPS.
