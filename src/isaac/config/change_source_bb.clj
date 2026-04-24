(ns isaac.config.change-source-bb
  "Babashka file-watcher backed by org.babashka/fswatcher (Go fsnotify).
   Uses FSEvents on macOS and inotify on Linux — event-driven, not polling."
  (:require
    [clojure.string :as str]
    [isaac.config.change-source :as change-source]
    [isaac.config.paths :as paths])
  (:import
    (java.util.concurrent LinkedBlockingQueue TimeUnit)))

(defn- config-relative [home path]
  (let [prefix (str (paths/config-root home) "/")]
    (when (str/starts-with? path prefix)
      (subs path (count prefix)))))

(defn- enqueue-change! [queue home {:keys [path]}]
  (when-let [rel (config-relative home path)]
    (.offer queue rel)))

(deftype FswatcherChangeSource [home queue state]
  change-source/ConfigChangeSource
  (change-source/-start! [_]
    (when (nil? @state)
      (let [watch-fn    (requiring-resolve 'pod.babashka.fswatcher/watch)
            config-root (paths/config-root home)
            watcher     (watch-fn config-root
                                  (fn [event] (enqueue-change! queue home event))
                                  {:recursive true})]
        (reset! state {:watcher watcher})
        ;; FSEvents on macOS needs a moment to start tracking a new directory.
        (Thread/sleep 1000)))
    nil)
  (change-source/-stop! [_]
    (when-let [{:keys [watcher]} @state]
      ((requiring-resolve 'pod.babashka.fswatcher/unwatch) watcher)
      (reset! state nil))
    nil)
  (change-source/-poll! [_ timeout-ms]
    (if (pos? timeout-ms)
      (.poll queue timeout-ms TimeUnit/MILLISECONDS)
      (.poll queue)))
  (change-source/-notify-path! [_ path]
    (enqueue-change! queue home {:path path})
    nil))

(defn watch-service-source [home]
  (->FswatcherChangeSource home (LinkedBlockingQueue.) (atom nil)))
