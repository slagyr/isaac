;; mutation-tested: 2026-05-06
(ns isaac.tool.exec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.bridge.cancellation :as bridge]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.system :as system]
    [isaac.tool.fs-bounds :as bounds])
  (:import
    [java.util.concurrent TimeUnit]))

(def ^:private default-timeout 30000)

(defn start-process [args]
  (let [command (get args "command")
        workdir (get args "workdir")
        pb      (doto (ProcessBuilder. ["/bin/sh" "-c" command])
                  (.redirectErrorStream true))]
    (when workdir
      (.directory pb (io/file workdir)))
    (.start pb)))

(defn process-finished? [proc timeout-ms]
  (.waitFor proc timeout-ms TimeUnit/MILLISECONDS))

(defn destroy-process!
  ([proc]
   (destroy-process! proc 100))
  ([proc grace-ms]
   (.destroy proc)
   (when-not (process-finished? proc grace-ms)
     (.destroyForcibly proc))))

(defn read-process-output [proc]
  (slurp (.getInputStream proc)))

(defn process-exit-value [proc]
  (.exitValue proc))

(defn- session-workdir [session-key]
  (when session-key
    (when-let [state-dir (system/get :state-dir)]
      (let [cwd (:cwd (store/get-session (or (system/get :session-store) (file-store/create-store state-dir)) session-key))]
        (when (and cwd (.isDirectory (io/file cwd)))
          cwd)))))

(defn- resolve-exec-args [args]
  (let [workdir     (get args "workdir")
        session-key (get args "session_key")
        session-cwd (session-workdir session-key)
        resolved    (cond
                      (or (nil? workdir) (str/blank? workdir) (= "." workdir))
                      session-cwd

                      (.isAbsolute (io/file workdir))
                      workdir

                      session-cwd
                      (.getCanonicalPath (io/file session-cwd workdir))

                      :else
                      workdir)]
    (cond-> args
      resolved (assoc "workdir" resolved))))

(defn- exec-finished-result [proc]
  (let [output (read-process-output proc)
        exit   (process-exit-value proc)]
    (if (zero? exit)
      {:result (str/trim output)}
      {:isError true :error (str "exit " exit ": " (str/trim output))})))

(defn- wait-for-process! [proc session-key timeout-ms]
  (bridge/on-cancel! session-key #(destroy-process! proc))
  (cond
    (process-finished? proc timeout-ms)
    (if (bridge/cancelled? session-key)
      {:error :cancelled}
      (exec-finished-result proc))

    :else
    (do
      (destroy-process! proc 10)
      {:isError true :error "timeout exceeded"})))

(defn exec-tool
  "Execute a shell command.
   Args: command, workdir, timeout."
  [args]
  (let [args        (bounds/string-key-map args)
        session-key (get args "session_key")
        timeout-ms  (or (bounds/arg-int args "timeout" nil) default-timeout)
        args        (resolve-exec-args args)]
    (try
      (wait-for-process! (start-process args) session-key timeout-ms)
      (catch Exception e
        {:isError true :error (.getMessage e)}))))
