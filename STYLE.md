# STYLE

## Java imports

Prefer importing Java classes in the namespace form and using the short class name in code.

Do this:

```clj
(ns isaac.acp.rpc
  (:require
    [cheshire.core :as json])
  (:import (clojure.lang ExceptionInfo)))
```

Then use the short name:

```clj
(catch ExceptionInfo e
  ...)
```

Not this:

```clj
(catch clojure.lang.ExceptionInfo e
  ...)
```

## Constants

Constants are best capitalized to make their role obvious at use sites.

Prefer:

```clj
(def PARSE_ERROR -32700)
```

Over:

```clj
(def parse-error -32700)
```

## Line length and wrapping

Prefer a maximum line length of 120 characters.

If a function call fits comfortably on one line within that limit, keep it on one line.
Do not introduce line breaks just to make the code look vertically symmetric.
Break forms only when needed for readability or to stay within the line length.

Prefer:

```clj
(jrpc/request 1 "ping")
```

Over:

```clj
(jrpc/request
  1
  "ping")
```
```

This is essentially a preference for minimal wrapping: keep short forms inline, wrap only when the line gets too long or the structure becomes clearer when split.
