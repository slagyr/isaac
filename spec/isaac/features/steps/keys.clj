(ns isaac.features.steps.keys
  (:require
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.features.matchers :as match]
    [isaac.session.key :as key]
    [isaac.session.store :as store]))

(defn- unquote-string [s]
  (if (and (str/starts-with? s "\"") (str/ends-with? s "\""))
    (subs s 1 (dec (count s)))
    s))

(defgiven empty-state "an empty Isaac state directory {string}"
  [path]
  (g/assoc! :state-dir (unquote-string path))
  (g/assoc! :store (store/create-store)))

(defgiven sessions-exist "the following sessions exist:"
  [table]
  (let [s (g/get :store)]
    (doseq [row (:rows table)]
      (let [row-map (zipmap (:headers table) row)
            key-str (get row-map "key")]
        (store/create-session! s key-str)))))

(defwhen sessions-created "the following sessions are created:"
  [table]
  (let [s (g/get :store)]
    (doseq [row (:rows table)]
      (let [row-map (zipmap (:headers table) row)]
        (if (get row-map "key")
          (store/create-session! s (get row-map "key"))
          (let [kw-map  (into {} (map (fn [[k v]] [(keyword k) v]) row-map))
                key-str (key/build-key kw-map)]
            (store/create-session! s key-str)))))))

(defwhen thread-sessions-created "the following thread sessions are created:"
  [table]
  (let [s (g/get :store)]
    (doseq [row (:rows table)]
      (let [row-map   (zipmap (:headers table) row)
            key-str   (key/build-thread-key (get row-map "parentKey") (get row-map "thread"))]
        (store/create-session! s key-str)))))

(defwhen key-parsed "the key {string} is parsed"
  [key-str]
  (g/assoc! :parsed (key/parse-key (unquote-string key-str))))

(defwhen messages-appended "the following messages are appended:"
  [table]
  (let [s       (g/get :store)
        listing (store/list-sessions s)
        key-str (:key (first listing))]
    (doseq [row (:rows table)]
      (let [row-map (zipmap (:headers table) row)]
        (store/append-message! s key-str row-map)))))

(defthen listing-count "the session listing has {int} entry/entries"
  [n]
  (let [listing (store/list-sessions (g/get :store))]
    (g/should= n (count listing))))

(defthen listing-matches "the session listing has entries matching:"
  [table]
  (let [listing (store/list-sessions (g/get :store))
        result  (match/match-entries table listing)]
    (g/should (:pass? result))))

(defthen parsed-key-matches "the parsed key matches:"
  [table]
  (let [parsed (g/get :parsed)
        result (match/match-object table parsed)]
    (g/should (:pass? result))))
