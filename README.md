# clj-lpass

A Clojure library for interacting with [LastPass](https://lastpass.com).

A hacky wrapper around [lastpass-cli](https://github.com/lastpass/lastpass-cli).

No official affiliation or relationship with LastPass or parent company LogMeIn.

## Dependencies

Must have [lastpass-cli](https://github.com/lastpass/lastpass-cli)'s `lpass`
executable on the path.

## Examples

```
(require '[clj-lpass.core :as lpass])

(lpass/ls)

(lpass/show 123456789)

(->> (lpass/ls true)
     (filter :aws-credentials/access-key-id)
     first
     )

(->> (lpass/ls)
     (filter lpass/credit-card?)
     (map lpass/show*)
     )

(->> (lpass/ls)
     (filter lpass/bank-account?)
     )
```

## Limitations

Read-only.

Although [lastpass-cli](https://github.com/lastpass/lastpass-cli) does support
modification, those features are not yet wrapped by this library.

### Bugs

Bad behavior if not logged in (i.e. when you haven't done `lpass login`).

Very little error handling.
