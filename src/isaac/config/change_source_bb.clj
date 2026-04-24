(ns isaac.config.change-source-bb
  "Babashka-compatible file-change watcher based on mtime polling.
   WatchService is unavailable in Babashka's GraalVM native image, so we
   snapshot lastModified timestamps every POLL-MS milliseconds instead."
  (:require
    [clojure.string :as str]
    [isaac.config.change-source :as change-source]
    [isaac.config.paths :as paths])
  (:import
    (java.io File)
    (java.util.concurrent LinkedBlockingQueue TimeUnit)))

(def ^:private poll-ms 500)

(defn- config-root-file ^File [home]
  (File. (paths/config-root home)))

(defn- file-snapshot [^File root]
  (when (.exists root)
    (reduce (fn [acc ^File f]
              (if (.isFile f)
                (assoc acc (.getAbsolutePath f) (.lastModified f))
                acc))
            {}
            (file-seq root))))

(defn- changed-paths [old new]
  (concat
    (for [[path mtime] new :when (not= mtime (get old path))] path)
    (for [path (keys old) :when (not (contains? new path))] path)))

(defn- config-relative [home ^String path]
  (let [prefix (str (paths/config-root home) File/separator)]
    (when (str/starts-with? path prefix)
      (str/replace (subs path (count prefix)) "\\" "/"))))

(deftype PollingChangeSource [home queue state]
  change-source/ConfigChangeSource
  (change-source/-start! [_]
    (when (nil? @state)
      (let [snap     (atom (file-snapshot (config-root-file home)))
            running? (atom true)
            thread   (doto (Thread.
                             (fn []
                               (while @running?
                                 (Thread/sleep poll-ms)
                                 (when @running?
                                   (let [new-snap (file-snapshot (config-root-file home))]
                                     (doseq [path (changed-paths @snap new-snap)]
                                       (when-let [rel (config-relative home path)]
                                         (.offer queue rel)))
                                     (reset! snap new-snap)))))
                             "isaac-config-poller-bb")
                       (.setDaemon true))]
        (.start thread)
        (reset! state {:running? running? :thread thread})))
    nil)
  (change-source/-stop! [_]
    (when-let [{:keys [running?]} @state]
      (reset! running? false)
      (reset! state nil))
    nil)
  (change-source/-poll! [_ timeout-ms]
    (if (pos? timeout-ms)
      (.poll queue timeout-ms TimeUnit/MILLISECONDS)
      (.poll queue)))
  (change-source/-notify-path! [_ path]
    (when-let [rel (config-relative home path)]
      (.offer queue rel))
    nil))

(defn watch-service-source [home]
  (->PollingChangeSource home (LinkedBlockingQueue.) (atom nil)))
