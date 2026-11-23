# Design decisions

The decisions that shape Tandem, and where each kind of data lives.

## Where data lives

| Data                                                                        | Owner      | Where it's kept                                                            | How it syncs                                        |
| --------------------------------------------------------------------------- | ---------- | -------------------------------------------------------------------------- | --------------------------------------------------- |
| Accounts, transactions, categories, budgets, payees, rules, schedules, tags | Actual     | The budget file on Actual's server and a copy on each device               | Actual's own sync, from the device                  |
| Planning records: goals, debt terms, retirement plans, residence, pensions  | Tandem     | The device, each record sealed with the budget's planning key              | Optional extension sync, as sealed records only     |
| The planning key                                                            | Tandem     | Android Keystore or iOS Keychain; wrapped copy on extension sync           | Wrapped by the recovery key or the budget's own key |
| Sign-in, server address, device settings                                    | The device | Android Keystore or iOS Keychain and the app's private database            | Never                                               |
| Encrypted budget password                                                   | The person | In memory, or the device's protected storage if background sync is allowed | Never                                               |

Tandem never writes planning data into the budget file, and never keeps a second
ledger.

## Decisions

### One app, many capabilities

One Tandem app holds every goal and country feature; people turn on what they
need. A person with accounts in several countries uses one app. The cost is a
larger test matrix, kept in check by feature modules that load only when used.

### Actual is the ledger

Every amount, balance and budget comes from Actual, and Tandem's edits are
ordinary Actual changes, so Actual's own apps always agree. Things Actual has no
place for, such as an employer match formula, are planning records that refer to
Actual accounts instead of copying them.

### Planning records are sealed on the device

Planning records are encrypted on the device before they're stored or synced.
The extension sync service holds only sealed records and can't read them. The
cost is a separate key to look after: adding a device takes the recovery key,
and losing one means replacing it.

### Budget data syncs directly with Actual

The app downloads the budget file and speaks Actual's sync protocol itself, so
budgeting needs nothing but an Actual server. The old budget gateway is gone;
the Tandem server only offers optional planning sync. Compatibility tests pin
the supported Actual release.

### Calculations are dated, local and explained

Country rules carry their years, sources and limits, and run on the device. When
a year isn't published yet, Tandem says so and shows the last known figure, and
you can enter a limit yourself when rules change first. Cases Tandem can't
decide stay visible instead of being guessed.

### Modes and goals change the view, not the data

Simple and advanced modes and the goals chosen at setup decide what Home shows
and which screens are suggested. They never change how money is counted, so
switching modes changes nothing in the budget.
