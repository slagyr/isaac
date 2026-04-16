(ns isaac.tool.builtin
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.session.bridge :as bridge]
    [isaac.fs :as fs])
  (:import
    [java.util.concurrent TimeUnit]))

;; region ----- read -----

(defn read-tool
  "Read file contents or list a directory.
   Args: {:filePath str :offset int :limit int}"
  [{:keys [filePath offset limit]}]
  (cond
    (not (fs/file-exists? fs/*fs* filePath))
    {:isError true :error (str "not found: " filePath)}

    (when-let [entries (fs/list-files fs/*fs* filePath)]
      (seq entries))
    {:result (str/join "\n" (sort (fs/list-files fs/*fs* filePath)))}

    :else
    (let [lines  (str/split-lines (or (fs/slurp filePath) ""))
          start  (if offset (dec offset) 0)
          sliced (cond->> lines
                   offset (drop start)
                   limit  (take limit))]
      {:result (str/join "\n" sliced)})))

;; endregion ^^^^^ read ^^^^^

;; region ----- write -----

(defn write-tool
  "Write content to a file, creating parent directories as needed.
   Args: {:filePath str :content str}"
  [{:keys [filePath content]}]
  (try
    (fs/make-dirs fs/*fs* filePath)
    (fs/write-file fs/*fs* filePath content)
    {:result (str "wrote " filePath)}
    (catch Exception e
      {:isError true :error (.getMessage e)})))

;; endregion ^^^^^ write ^^^^^

;; region ----- edit -----

(defn edit-tool
  "Replace text in a file.
   Args: {:filePath str :oldString str :newString str :replaceAll bool}"
  [{:keys [filePath oldString newString replaceAll]}]
  (if-not (fs/file-exists? fs/*fs* filePath)
    {:isError true :error (str "not found: " filePath)}
    (let [content (or (fs/slurp filePath) "")
          count   (count (re-seq (java.util.regex.Pattern/compile
                                   (java.util.regex.Pattern/quote oldString))
                                 content))]
      (cond
        (= 0 count)
        {:isError true :error (str "not found: " oldString)}

        (and (> count 1) (not replaceAll))
        {:isError true :error (str "multiple matches for: " oldString)}

        :else
        (let [new-content (str/replace content oldString newString)]
          (fs/write-file fs/*fs* filePath new-content)
          {:result (str "edited " filePath)})))))

;; endregion ^^^^^ edit ^^^^^

;; region ----- exec -----

(def ^:private default-timeout 30000)
(def ^:private poll-interval-ms 50)

(defn start-process [{:keys [command workdir]}]
  (let [pb (doto (ProcessBuilder. ["/bin/sh" "-c" command])
             (.redirectErrorStream true))]
    (when workdir
      (.directory pb (io/file workdir)))
    (.start pb)))

(defn process-finished? [proc timeout-ms]
  (.waitFor proc timeout-ms TimeUnit/MILLISECONDS))

(defn destroy-process! [proc]
  (.destroy proc)
  (when-not (process-finished? proc 100)
    (.destroyForcibly proc)))

(defn read-process-output [proc]
  (slurp (.getInputStream proc)))

(defn process-exit-value [proc]
  (.exitValue proc))

(defn exec-tool
  "Execute a shell command.
   Args: {:command str :workdir str :timeout int}"
  [{:keys [command workdir timeout session-key]}]
  (let [timeout-ms (or timeout default-timeout)]
    (try
      (let [proc (start-process {:command command :workdir workdir})]
        (loop [elapsed 0]
          (cond
            (bridge/cancelled? session-key)
            (do
              (destroy-process! proc)
              {:error :cancelled})

            (process-finished? proc poll-interval-ms)
            (let [output (read-process-output proc)
                  exit   (process-exit-value proc)]
              (if (zero? exit)
                {:result (str/trim output)}
                {:isError true :error (str "exit " exit ": " (str/trim output))}))

            (>= elapsed timeout-ms)
            (do
              (destroy-process! proc)
              {:isError true :error "timeout exceeded"})

            :else
            (recur (+ elapsed poll-interval-ms)))))
      (catch Exception e
        {:isError true :error (.getMessage e)}))))

;; endregion ^^^^^ exec ^^^^^

;; region ----- Registration -----

(defn register-all!
  "Register all built-in tools with the given registry."
  [registry-ns]
  (registry-ns {:name        "read"
                :description "Read file contents or list a directory"
                :parameters  {:type       "object"
                               :properties {:filePath {:type "string" :description "Path to file or directory"}
                                            :offset   {:type "integer" :description "Start line (1-indexed)"}
                                            :limit    {:type "integer" :description "Max lines to return"}}
                               :required   ["filePath"]}
                :handler     #'read-tool})
  (registry-ns {:name        "write"
                :description "Write content to a file"
                :parameters  {:type       "object"
                               :properties {:filePath {:type "string" :description "Path to write"}
                                            :content  {:type "string" :description "Content to write"}}
                               :required   ["filePath" "content"]}
                :handler     #'write-tool})
  (registry-ns {:name        "edit"
                :description "Replace text in a file"
                :parameters  {:type       "object"
                               :properties {:filePath   {:type "string" :description "File to edit"}
                                            :oldString  {:type "string" :description "Text to replace"}
                                            :newString  {:type "string" :description "Replacement text"}
                                            :replaceAll {:type "boolean" :description "Replace all occurrences"}}
                               :required   ["filePath" "oldString" "newString"]}
                :handler     #'edit-tool})
  (registry-ns {:name        "exec"
                :description "Execute a shell command"
                :parameters  {:type       "object"
                               :properties {:command {:type "string" :description "Command to run"}
                                            :workdir {:type "string" :description "Working directory"}
                                            :timeout {:type "integer" :description "Timeout in ms"}}
                               :required   ["command"]}
                :handler     #'exec-tool}))

;; endregion ^^^^^ Registration ^^^^^
