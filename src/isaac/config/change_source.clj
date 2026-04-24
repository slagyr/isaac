(ns isaac.config.change-source
  (:require
    [clojure.string :as str]
    [isaac.config.paths :as paths])
  (:import
    (java.util.concurrent LinkedBlockingQueue TimeUnit)))

(defprotocol ConfigChangeSource
  (-start! [this])
  (-stop! [this])
  (-poll! [this timeout-ms])
  (-notify-path! [this path]))

(defn- config-relative-path [home path]
  (let [config-root (str (paths/config-root home) "/")]
    (when (str/starts-with? path config-root)
      (subs path (count config-root)))))

(defn- enqueue-change! [queue home path]
  (when-let [relative (config-relative-path home path)]
    (.offer queue relative)))

(deftype MemoryChangeSource [home queue]
  ConfigChangeSource
  (-start! [_] nil)
  (-stop! [_] nil)
  (-poll! [_ timeout-ms]
    (if (pos? timeout-ms)
      (.poll queue timeout-ms TimeUnit/MILLISECONDS)
      (.poll queue)))
  (-notify-path! [_ path]
    (enqueue-change! queue home path)
    nil))

(deftype NoopWatchServiceChangeSource [_home]
  ConfigChangeSource
  (-start! [_] nil)
  (-stop! [_] nil)
  (-poll! [_ timeout-ms]
    (when (pos? timeout-ms)
      (Thread/sleep timeout-ms))
    nil)
  (-notify-path! [_ _] nil))

(defn- babashka? []
  (some? (System/getProperty "babashka.version")))

(defn watch-service-source [home]
  (if (babashka?)
    (->NoopWatchServiceChangeSource home)
    ((requiring-resolve 'isaac.config.change-source-watch/watch-service-source) home)))

(defn memory-source [home]
  (->MemoryChangeSource home (LinkedBlockingQueue.)))

(defn start! [source]
  (-start! source)
  source)

(defn stop! [source]
  (-stop! source)
  nil)

(defn poll!
  ([source]
   (poll! source 0))
  ([source timeout-ms]
   (-poll! source timeout-ms)))

(defn notify-path! [source path]
  (-notify-path! source path)
  nil)
