(ns isaac.session.store-spec
  (:require
    [isaac.fs :as fs]
    [isaac.session.store :as store]
    [isaac.session.store.memory :as memory]
    [isaac.system :as system]
    [speclj.core :refer :all]))

(describe "isaac.session.store"

  (it "defines a SessionStore protocol"
    (should-not-be-nil store/SessionStore))

  (describe "resolve-store"

    (it "returns the explicit session store when provided"
      (let [s (memory/create-store)]
        (should= s (store/resolve-store {:session-store s} "test caller"))))

    (it "creates a file-backed store from state-dir when needed"
      (system/with-system {:fs (fs/mem-fs)}
        (let [s (store/resolve-store {:state-dir "target/test-state/store-spec"} "test caller")]
          (should (satisfies? store/SessionStore s)))))

    (it "throws when neither session-store nor state-dir is available"
      (let [error (try
                    (store/resolve-store {} "test caller")
                    (catch clojure.lang.ExceptionInfo e e))]
        (should-contain "test caller requires :state-dir or :session-store" (.getMessage error))
        (should= [] (:ctx-keys (ex-data error))))))

  (describe "runtime-ctx"

    (it "returns only the runtime session store context"
      (let [session-store (memory/create-store)]
        (system/with-system {:state-dir "target/test-state/runtime-ctx"
                             :session-store session-store
                             :extra "ignored"}
          (should= {:state-dir "target/test-state/runtime-ctx"
                    :session-store session-store}
                   (store/runtime-ctx))))))

  (describe "create-store"

    (it "creates an atom containing an empty map"
      (let [s (memory/create-store)]
        (should (satisfies? store/SessionStore s)))))

  (describe "open-session!"

    (it "adds a session keyed by the given key string"
      (let [s (memory/create-store)]
        (store/open-session! s "k1" {})
        (should= "k1" (:key (store/get-session s "k1")))))

    (it "can create multiple sessions"
      (let [s (memory/create-store)]
        (store/open-session! s "k1" {})
        (store/open-session! s "k2" {})
        (should= 2 (count (store/list-sessions s))))))

  (describe "get-session"

    (it "returns the session for a given key"
      (let [s (memory/create-store)]
        (store/open-session! s "k1" {})
        (should= "k1" (:key (store/get-session s "k1")))))

    (it "returns nil for a missing key"
      (let [s (memory/create-store)]
        (should-be-nil (store/get-session s "missing")))))

  (describe "list-sessions"

    (it "returns an empty list when no sessions exist"
      (let [s (memory/create-store)]
        (should= [] (vec (store/list-sessions s)))))

    (it "returns all sessions"
      (let [s (memory/create-store)]
        (store/open-session! s "k1" {})
        (store/open-session! s "k2" {})
        (should= 2 (count (store/list-sessions s))))))

  (describe "append-message!"

    (it "updates last-channel from message"
      (let [s (memory/create-store)]
        (store/open-session! s "k1" {})
        (store/append-message! s "k1" {:channel "slack"})
        (should= "slack" (:last-channel (store/get-session s "k1")))))

    (it "updates last-to from message"
      (let [s (memory/create-store)]
        (store/open-session! s "k1" {})
        (store/append-message! s "k1" {:to "user1"})
        (should= "user1" (:last-to (store/get-session s "k1")))))

    (it "updates both last-channel and last-to when both present"
      (let [s (memory/create-store)]
        (store/open-session! s "k1" {})
        (store/append-message! s "k1" {:channel "irc" :to "user2"})
        (let [session (store/get-session s "k1")]
          (should= "irc" (:last-channel session))
          (should= "user2" (:last-to session)))))

    (it "handles string keys in message map"
      (let [s (memory/create-store)]
        (store/open-session! s "k1" {})
        (store/append-message! s "k1" {"channel" "discord" "to" "user3"})
        (let [session (store/get-session s "k1")]
          (should= "discord" (:last-channel session))
          (should= "user3" (:last-to session)))))

    (it "does not alter session when message has no channel or to"
      (let [s (memory/create-store)]
        (store/open-session! s "k1" {})
        (store/append-message! s "k1" {:text "hello"})
        (let [session (store/get-session s "k1")]
          (should-be-nil (:last-channel session))
          (should-be-nil (:last-to session)))))))
