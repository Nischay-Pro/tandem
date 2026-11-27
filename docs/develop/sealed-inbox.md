# Sealed automation inbox

For developers changing how automation adds to an encrypted budget while it's
locked. Adding never needs the budget password; reading what was added does.

## Keys

- On unlock, Tandem makes a P-256 key pair for the budget. The private key is
  kept only in the budget's encrypted vault; the public key is kept on the
  device in the clear (`internal:inbox-public-key-v1:<budget>`).
- The key id is the first 16 bytes of SHA-256 over the uncompressed public key.
- A new pair is made when the budget password changes. The vault keeps the last
  four pairs, so entries sealed just before the change still open.
- Removing the budget, downloading it again or signing out deletes the inbox
  file and forgets the public key.

## Entry format (version 1)

| Bytes | Field                                    |
| ----- | ---------------------------------------- |
| 1     | Version, `0x01`                          |
| 16    | Key id                                   |
| 16    | Entry id, random                         |
| 65    | Ephemeral P-256 public key, uncompressed |
| 12    | AES-GCM nonce, random                    |
| rest  | AES-256-GCM ciphertext and 16-byte tag   |

1. A fresh ephemeral key pair per entry; ECDH with the budget's public key.
2. HKDF-SHA256: salt = ephemeral public key ‖ budget public key, info =
   `tandem-inbox/v1|<budget id>|<key id hex>`, 32 bytes.
3. AES-256-GCM with the additional data = the 110-byte header ‖ budget id.

Every key is used once, so a nonce never repeats under a key. Changing any
header byte, or opening the entry for another budget, fails the tag check.

The plaintext is JSON with the caller's fields as given, the source app and
the time. Only the amount's sign, the account, date and cleared flag are
checked before sealing; the rest is checked when the entry is added.

## Inbox file

One file per budget in no-backup app storage. Each line is appended whole and
synced:

- `e <base64 entry>` for an entry.
- `x <entry id hex>` when Undo takes an entry back before it's added.

A line cut short by a crash is closed before the next append, so it costs only
that entry.

## Adding entries on unlock

When the vault is open and saved, before each sync:

1. Read the inbox in order and skip withdrawn and already added entry ids.
2. Open each entry with the vault's keys. Entries that fail are dropped and
   counted.
3. Add it through the outbox like any automation write: payee matching, the
   external-id repeat check and Actual's rules on sync. The transaction id is
   `tandem:<entry id>`, so an entry added before a crash is found and not
   added again.
4. Record the entry id in the vault, then move on. The file is emptied only
   after every entry is handled.
5. Publish the newest public key.

Callers are told "Saved. It will be added when you next open Tandem." and the
notification shows no amount, payee or account.
